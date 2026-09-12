import arc.util.serialization.Jval;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 离线校验模组产物（由 check.sh 调用）。不用启动游戏就能发现最常见的几类错误：
//
//   1. jar 根目录有没有 mod.hjson，且能按 HJSON 解析、必需字段是否齐全
//   2. main 指向的类能不能加载、是不是继承自 mindustry.mod.Mod
//   3. 资源有没有放对位置：
//        - 语言包必须在 jar 内的 bundles/ 目录下
//      Gradle 的 from(dir) 默认把"目录内容"铺到 jar 根目录，很容易把语言包
//      打到根目录去；游戏会静默读不到，只表现为界面显示原始键名，很难查。
//   4. 代码里以模组内部名为前缀的文案键（如 enemy-pause.paused）
//      是否真的在语言包里定义了，避免改键名时只改了一边。
//
// 用法: java -cp <classes>:<dependencies.jar> CheckMod <模组.jar> <dependencies.jar>
public class CheckMod{

    static int failed = 0;

    static void check(String label, boolean ok, String detail){
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + label + (detail == null ? "" : "  -> " + detail));
        if(!ok) failed++;
    }

    static void warn(String label, String detail){
        System.out.println("[WARN] " + label + (detail == null ? "" : "  -> " + detail));
    }

    // Jval 的 getString 遇到非字符串会抛异常，这里按类型安全地取值
    static String show(Jval val){
        if(val.isString()) return val.asString();
        if(val.isBoolean()) return String.valueOf(val.asBool());
        if(val.isNumber()) return String.valueOf(val.asDouble());
        return "<非标量>";
    }

    /** 按 .properties 规则取出键名（# 和 ! 开头是注释，分隔符是 = : 或空白）。 */
    static Set<String> propertyKeys(String text){
        Set<String> keys = new LinkedHashSet<>();
        for(String raw : text.split("\\R")){
            String line = raw.trim();
            if(line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;

            Matcher m = Pattern.compile("^([^=:\\s]+)\\s*[=:\\s]").matcher(line);
            if(m.find()) keys.add(m.group(1));
        }
        return keys;
    }

    /**
     * 粗暴但够用地从 class 文件里抓字符串字面量：直接扫可打印 ASCII 连续段，
     * 再按前缀筛。这样不用引入额外的字节码库。
     */
    static Set<String> stringLiterals(byte[] bytes){
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("[\\x20-\\x7E]{4,}").matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        while(m.find()) out.add(m.group());
        return out;
    }

    public static void main(String[] args) throws Exception{
        String modJarPath = args[0];
        String depsJarPath = args[1];

        // 一次遍历把需要的东西都读出来
        List<String> entries = new ArrayList<>();
        String hjson = null;
        Set<String> defaultKeys = new LinkedHashSet<>();
        List<String> classNames = new ArrayList<>();

        try(JarFile jf = new JarFile(modJarPath)){
            var it = jf.entries();
            while(it.hasMoreElements()){
                JarEntry e = it.nextElement();
                if(e.isDirectory()) continue;
                entries.add(e.getName());

                if(e.getName().equals("mod.hjson")){
                    try(InputStream in = jf.getInputStream(e)){
                        hjson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }else if(e.getName().equals("bundles/bundle.properties")){
                    try(InputStream in = jf.getInputStream(e)){
                        defaultKeys = propertyKeys(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }else if(e.getName().endsWith(".class")){
                    classNames.add(e.getName());
                }
            }
        }

        // ---- 1. mod.hjson ----
        if(hjson == null){
            check("jar 根目录存在 mod.hjson", false, "没找到");
            System.exit(1);
        }

        Jval meta;
        try{
            // 游戏读 mod.hjson 走的就是这个重载，HJSON 语法由它自己识别
            meta = Jval.read(hjson);
            check("mod.hjson 是合法 HJSON", true, null);
        }catch(Exception e){
            check("mod.hjson 是合法 HJSON", false, e.toString());
            System.exit(1);
            return;
        }

        String[] required = {"name", "displayName", "author", "main", "version", "minGameVersion", "java"};
        for(String key : required){
            Jval val = meta.get(key);
            check("字段 " + key, val != null, val == null ? "缺失" : show(val));
        }

        // java: true 必须是布尔值，游戏靠它判断这是 Java 模组
        Jval javaField = meta.get("java");
        check("java 字段是布尔 true", javaField != null && javaField.isBoolean() && javaField.asBool(),
            javaField == null ? "缺失" : show(javaField));

        String modName = meta.getString("name", null);

        // ---- 2. 主类 ----
        URLClassLoader loader = new URLClassLoader(
            new URL[]{new File(modJarPath).toURI().toURL()},
            new URLClassLoader(new URL[]{new File(depsJarPath).toURI().toURL()}, null));

        // initialize=false，避免触发 Mindustry 的静态初始化
        Class<?> modBase = Class.forName("mindustry.mod.Mod", false, loader);
        String mainName = meta.getString("main", null);

        Class<?> mainCls = null;
        try{
            mainCls = Class.forName(mainName, false, loader);
            check("主类可加载", true, mainName);
        }catch(Throwable t){
            check("主类可加载", false, t.toString());
        }

        if(mainCls != null){
            check("继承 mindustry.mod.Mod", modBase.isAssignableFrom(mainCls), null);
            check("主类是 public", java.lang.reflect.Modifier.isPublic(mainCls.getModifiers()), null);
            check("有 public 无参构造（游戏靠它实例化）",
                java.util.Arrays.stream(mainCls.getConstructors())
                    .anyMatch(c -> c.getParameterCount() == 0),
                null);
        }

        // ---- 3. 语言包是否放在 bundles/ 下 ----
        List<String> rootBundles = entries.stream()
            .filter(n -> !n.contains("/") && n.matches("bundle(_[A-Za-z_]+)?\\.properties"))
            .sorted().toList();
        List<String> packedBundles = entries.stream()
            .filter(n -> n.startsWith("bundles/") && n.endsWith(".properties"))
            .sorted().toList();

        check("语言包位于 jar 内的 bundles/ 目录下", rootBundles.isEmpty(),
            rootBundles.isEmpty() ? String.join(", ", packedBundles)
                : "发现位于根目录的 " + rootBundles
                    + "（build.gradle 里 from(\"bundles/\") 需要加 into(\"bundles\")）");

        check("至少有一个语言包", !packedBundles.isEmpty(), null);

        if(!packedBundles.isEmpty() && !packedBundles.contains("bundles/bundle.properties")){
            warn("缺少默认语言包 bundles/bundle.properties",
                "其他语言缺失的键会直接显示原始键名，建议补一份英文兜底");
        }

        // ---- 4. 代码里引用的文案键是否都有定义 ----
        if(modName != null && !defaultKeys.isEmpty()){
            String prefix = modName + ".";
            Set<String> used = new LinkedHashSet<>();

            try(JarFile jf = new JarFile(modJarPath)){
                for(String name : classNames){
                    try(InputStream in = jf.getInputStream(jf.getJarEntry(name))){
                        for(String lit : stringLiterals(in.readAllBytes())){
                            if(lit.startsWith(prefix)) used.add(lit);
                        }
                    }
                }
            }

            // defaultKeys 前面被重新赋值过，在 lambda 里用不了，先取个终态引用
            Set<String> defined = defaultKeys;
            List<String> missing = used.stream().filter(k -> !defined.contains(k)).sorted().toList();
            check("代码里引用的文案键都已定义（" + prefix + "*）", missing.isEmpty(),
                missing.isEmpty() ? used.size() + " 个键" : "缺少 " + missing);
        }

        System.out.println(failed == 0 ? "\n==> 全部通过" : "\n==> 有 " + failed + " 项失败");
        System.exit(failed == 0 ? 0 : 2);
    }
}
