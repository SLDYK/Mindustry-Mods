import arc.Core;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.I18NBundle;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;
import arc.util.io.PropertiesUtils;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.Planets;
import mindustry.core.ContentLoader;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.world.meta.BuildVisibility;
import erekiritems.ErekirItemsMod;
import erekiritems.TunableNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Offline verifier for the Erekir tunable power node mod.
 *
 * Checks two layers:
 *   1. the built jar: mod.hjson at the root, sprite/atlas name matches what the game will look up,
 *      and every bundle key used by the code is present in BOTH language files;
 *   2. the block itself, loaded for real (headless content loader): block flags, the config
 *      round-trip (including clamping and configClear), the per-tick power math, and save IO.
 *
 * Run (Windows, from the repo root):
 *   javac -encoding UTF-8 -implicit:none -sourcepath tools/eiverify -cp "<game jar>;<mod jar>" -d tools/eiverify/out tools/eiverify/VerifyTunableNode.java
 *   java -Dfile.encoding=UTF-8 -cp "tools/eiverify/out;<game jar>;<mod jar>" VerifyTunableNode <mod jar>
 */
public class VerifyTunableNode{
    private static int checks = 0, failures = 0;

    public static void main(String[] args) throws Exception{
        if(args.length < 1){
            System.err.println("usage: VerifyTunableNode <mod jar>");
            System.exit(2);
        }
        Path jar = Path.of(args[0]);
        if(!Files.isRegularFile(jar)){
            System.err.println("mod jar not found: " + jar);
            System.exit(2);
        }

        Path tmp = Files.createTempDirectory("eiverify");
        try{
            verifyJarAndBundles(jar, tmp);
            verifyBlock(jar, tmp);
        }finally{
            // best-effort cleanup
            try(var stream = Files.walk(tmp)){
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try{ Files.delete(p); }catch(Exception ignored){}
                });
            }
        }

        System.out.println();
        System.out.println("checks: " + checks + ", failures: " + failures);
        if(failures > 0){
            System.out.println("FAILED");
            System.exit(1);
        }else{
            System.out.println("ALL OK");
        }
    }

    // ------------------------------------------------------------------ jar layer

    private static void verifyJarAndBundles(Path jar, Path tmp) throws Exception{
        section("jar structure");

        String modName, mainClass;
        try(ZipFile zip = new ZipFile(jar.toFile())){
            // mod.hjson must be at the jar root
            ZipEntry meta = zip.getEntry("mod.hjson");
            check("mod.hjson is at the jar root", meta != null);
            if(meta == null) return;

            String hjson = new String(zip.getInputStream(meta).readAllBytes(), StandardCharsets.UTF_8);
            modName = jsonValue(hjson, "name");
            mainClass = jsonValue(hjson, "main");
            check("mod.hjson has a name", modName != null && !modName.isEmpty(), modName);

            // replicate the game's normalization: LoadedMod name = meta.name lowercased, spaces -> hyphens
            String normalized = modName == null ? null : modName.toLowerCase(java.util.Locale.ROOT).replace(" ", "-");

            check("mod.hjson declares the mod main class", mainClass != null && mainClass.equals(ErekirItemsMod.class.getName()),
                String.valueOf(mainClass));
            check("main class is inside the jar", zip.getEntry(mainClass.replace('.', '/') + ".class") != null);

            // mod sprites are packed as "<mod name>-<file base name>"; the block looks up its own
            // content name, so check that the file name really resolves to that region name.
            String contentName = normalized + "-" + ErekirItemsMod.NODE_NAME;
            String spriteFile = findSpriteForRegion(zip, normalized, contentName);
            check("a mod sprite in sprites/ packs to the region '" + contentName + "'", spriteFile != null,
                spriteFile == null ? describeSprites(zip) : spriteFile);

            // bundles
            String[] bundleNames = {"bundles/bundle.properties", "bundles/bundle_zh_CN.properties"};
            String[] required = {
                "block." + normalized + "-" + ErekirItemsMod.NODE_NAME + ".name",
                "block." + normalized + "-" + ErekirItemsMod.NODE_NAME + ".description",
                normalized + ".details",
                "stat." + normalized + "-maxoutput",
                normalized + ".output",
                normalized + ".apply",
            };

            for(String bundleName : bundleNames){
                ZipEntry entry = zip.getEntry(bundleName);
                check(bundleName + " exists in the jar", entry != null);
                if(entry == null) continue;

                // extract next to the jar name so PropertiesUtils can read it
                Path out = tmp.resolve(Path.of(bundleName).getFileName().toString());
                try(InputStream in = zip.getInputStream(entry)){
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }

                ObjectMap<String, String> props = new ObjectMap<>();
                PropertiesUtils.load(props, new Fi(out.toAbsolutePath().toString()).reader());

                for(String key : required){
                    check(bundleName + " defines '" + key + "'", props.get(key) != null);
                }
                check(bundleName + " details keep their {0}/{1} placeholders",
                    props.get(normalized + ".details") != null
                        && props.get(normalized + ".details").contains("{0}")
                        && props.get(normalized + ".details").contains("{1}"));
            }
        }
    }

    // ---------------------------------------------------------------- block layer

    private static void verifyBlock(Path jar, Path tmp) throws Exception{
        section("headless content loader");

        // minimal environment: content loader + the mod's bundle, no client modules at all
        Vars.headless = true;
        Vars.content = new ContentLoader();

        ObjectMap<String, String> props = new ObjectMap<>();
        PropertiesUtils.load(props, new Fi(tmp.resolve("bundle.properties").toAbsolutePath().toString()).reader());
        I18NBundle bundle = I18NBundle.createEmptyBundle();
        bundle.setProperties(props);
        Core.bundle = bundle;

        // create the base content exactly the way the game does (this is what makes Items/Planets
        // and everything the block's constructor references available)
        Vars.content.createBaseContent();

        section("block definition");

        TunableNode node = new TunableNode(ErekirItemsMod.NODE_NAME);

        check("block name", "tunable-node".equals(node.name), node.name);
        check("alwaysUnlocked (no research needed)", node.alwaysUnlocked);
        check("shownPlanets == {erekir}", node.shownPlanets.size == 1 && node.shownPlanets.contains(Planets.erekir),
            node.shownPlanets);
        check("pure power source: outputsPower && !consumesPower", node.outputsPower && !node.consumesPower);
        check("configurable", node.configurable);
        check("saveConfig", node.saveConfig);
        check("clearOnDoubleTap", node.clearOnDoubleTap);
        check("category == power", node.category == Category.power, node.category);
        check("buildVisibility == shown", node.buildVisibility == BuildVisibility.shown, node.buildVisibility);
        check("range", node.range == 12, node.range);
        check("health", node.health == 100, node.health);
        check("requirements mention beryllium+silicon", node.requirements != null && node.requirements.length == 2,
            java.util.Arrays.toString(node.requirements));
        check("config handler registered for Integer", node.configurations.containsKey(Integer.class));
        check("configClear registered", node.configurations.containsKey(void.class));
        check("details were formatted from the bundle",
            node.details != null && node.details.contains(String.valueOf(TunableNode.maxOutput))
                && node.details.contains(String.valueOf(TunableNode.defaultOutput)), node.details);

        section("helpers");

        eq("clampOutput(-1)", 0, TunableNode.clampOutput(-1));
        eq("clampOutput(0)", 0, TunableNode.clampOutput(0));
        eq("clampOutput(12345)", 12345, TunableNode.clampOutput(12345));
        eq("clampOutput(maxOutput)", TunableNode.maxOutput, TunableNode.clampOutput(TunableNode.maxOutput));
        eq("clampOutput(Integer.MAX_VALUE)", TunableNode.maxOutput, TunableNode.clampOutput(Integer.MAX_VALUE));

        eq("shortOutput(0)", "0", TunableNode.shortOutput(0));
        eq("shortOutput(100)", "100", TunableNode.shortOutput(100));
        eq("shortOutput(1000)", "1k", TunableNode.shortOutput(1000));
        eq("shortOutput(10000)", "10k", TunableNode.shortOutput(10000));
        eq("shortOutput(100000)", "100k", TunableNode.shortOutput(100000));
        eq("shortOutput(1000000)", "1M", TunableNode.shortOutput(1000000));

        boolean presetsOk = true;
        int last = -1;
        for(int value : TunableNode.presetOutputs){
            presetsOk &= value >= 0 && value <= TunableNode.maxOutput && value > last;
            last = value;
        }
        check("presetOutputs are ascending within 0..maxOutput", presetsOk, java.util.Arrays.toString(TunableNode.presetOutputs));

        section("building behaviour");

        Building build = node.newBuilding();
        build.block = node;
        TunableNode.TunableNodeBuild typed = (TunableNode.TunableNodeBuild)build;

        eq("default output", TunableNode.defaultOutput, typed.powerPerSecond);
        check("buildings are enabled by default", typed.enabled);
        approx("default production is per-tick (perSecond/60)", TunableNode.defaultOutput / 60f, typed.getPowerProduction());

        // config round-trip: this is the exact path the game uses for single player, saves and MP
        build.configured(null, 12345);
        eq("configured(12345) applies", 12345, typed.powerPerSecond);
        eq("config() reports the value", Integer.valueOf(12345), build.config());

        build.configured(null, -100);
        eq("negative values clamp to 0", 0, typed.powerPerSecond);
        approx("production is 0 when the output is 0", 0f, typed.getPowerProduction());

        build.configured(null, TunableNode.maxOutput + 12345);
        eq("values above maxOutput clamp", TunableNode.maxOutput, typed.powerPerSecond);

        build.configured(null, 6000);
        approx("6000/s == 100 power per tick", 100f, typed.getPowerProduction());
        approx("production * 60 round-trips back to the setting", 6000f, typed.getPowerProduction() * 60f);

        build.configured(null, null);
        eq("configClear(null) restores the default", TunableNode.defaultOutput, typed.powerPerSecond);

        build.enabled = false;
        approx("disabled building produces nothing", 0f, typed.getPowerProduction());
        build.enabled = true;

        section("save IO");

        typed.setOutput(7777);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(64);
        build.write(new Writes(new ByteBufferOutput(buffer)));
        int written = buffer.position();
        check("write emits the configured value", written == 4, written);

        byte[] bytes = new byte[written];
        buffer.flip();
        buffer.get(bytes);

        typed.powerPerSecond = 0;
        build.read(new Reads(new ByteBufferInput(java.nio.ByteBuffer.wrap(bytes))), (byte)0);
        eq("read restores the output", 7777, typed.powerPerSecond);
    }

    // --------------------------------------------------------------------- utils

    private static String jsonValue(String hjson, String key){
        var matcher = java.util.regex.Pattern.compile("(?m)^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*:\\s*\"?([^\"\\r\\n]+)\"?\\s*$").matcher(hjson);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /**
     * Finds the sprite file whose packed atlas region equals {@code regionName}.
     * Mirrors Mods.packSprites(): the mod name is prepended unless the part after the first
     * hyphen of the file name already starts with "<mod name>-".
     */
    private static String findSpriteForRegion(ZipFile zip, String modName, String regionName){
        var entries = zip.entries();
        while(entries.hasMoreElements()){
            String name = entries.nextElement().getName();
            if(!name.startsWith("sprites/") || !name.endsWith(".png") || name.indexOf('/', "sprites/".length()) != -1) continue;

            String baseName = name.substring("sprites/".length(), name.length() - ".png".length());
            int hyphen = baseName.indexOf('-');
            String packed = ((hyphen != -1 && baseName.substring(hyphen + 1).startsWith(modName + "-")) ? "" : modName + "-") + baseName;
            if(packed.equals(regionName)) return name;
        }
        return null;
    }

    private static String describeSprites(ZipFile zip){
        var out = new StringBuilder();
        var entries = zip.entries();
        while(entries.hasMoreElements()){
            String name = entries.nextElement().getName();
            if(name.startsWith("sprites/")) out.append(name).append(' ');
        }
        return out.length() == 0 ? "no sprites in jar" : out.toString();
    }

    private static void section(String name){
        System.out.println();
        System.out.println("== " + name + " ==");
    }

    private static void check(String label, boolean ok){
        check(label, ok, null);
    }

    private static void check(String label, boolean ok, Object actual){
        checks++;
        if(ok){
            System.out.println("  [ok]   " + label);
        }else{
            failures++;
            System.out.println("  [FAIL] " + label + (actual == null ? "" : " (actual: " + actual + ")"));
        }
    }

    private static void eq(String label, Object expected, Object actual){
        check(label, expected == null ? actual == null : expected.equals(actual), actual);
    }

    private static void approx(String label, float expected, float actual){
        check(label, Math.abs(expected - actual) < 0.001f, actual);
    }
}
