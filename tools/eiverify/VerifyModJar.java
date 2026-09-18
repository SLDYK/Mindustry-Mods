import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mod-level checks on the built jar, independent of any single block:
 * metadata placement, the main class, and — for every content — its sprite
 * region name and every bundle key the code reads.
 *
 * Run:  java -cp "out;<game jar>;<mod jar>" VerifyModJar <mod jar>
 */
public class VerifyModJar{
    /** Mod internal name, as used for content names and sprite prefixes. */
    private static final String MOD_NAME = "erekir-items";

    /**
     * Every block in the mod and the bundle keys it needs.
     * Layout: { block name, number of {0}.. placeholders in the details string, keys... }
     * {@code %s} in a key is replaced with the full content name (mod name + "-" + block name).
     */
    private static final Object[][] CONTENTS = {
        {"tunable-node", 2,
            "block.%s.name",
            "block.%s.description",
            "%s.details",
            "%s.output",
            "stat.%s-maxoutput",
        },
        {"resource-source", 4,
            "block.%s.name",
            "block.%s.description",
            "%s.details",
            "%s.itemrate",
            "%s.liquidrate",
            "stat.%s-itemrate",
            "stat.%s-liquidrate",
        },
    };

    public static void main(String[] args) throws Exception{
        Path jar = VerifySupport.requireJar(args);
        Path tmp = VerifySupport.tempDir("eiverify-modjar");

        try(ZipFile zip = new ZipFile(jar.toFile())){
            verifyMetadata(zip);
            verifySprites(zip);
            verifyBundles(zip, tmp);
        }finally{
            VerifySupport.cleanup(tmp);
        }

        VerifySupport.finish();
    }

    private static void verifyMetadata(ZipFile zip) throws Exception{
        VerifySupport.section("mod metadata");

        ZipEntry meta = zip.getEntry("mod.hjson");
        VerifySupport.check("mod.hjson is at the jar root", meta != null);
        if(meta == null) return;

        String hjson = new String(zip.getInputStream(meta).readAllBytes(), StandardCharsets.UTF_8);

        String name = jsonValue(hjson, "name");
        String main = jsonValue(hjson, "main");
        String displayName = jsonValue(hjson, "displayName");
        String minGameVersion = jsonValue(hjson, "minGameVersion");

        VerifySupport.eq("mod.hjson name", MOD_NAME, name);
        VerifySupport.eq("mod.hjson main", "erekiritems.ErekirItemsMod", main);
        VerifySupport.check("mod.hjson has a displayName", displayName != null && !displayName.isEmpty(), displayName);
        VerifySupport.check("mod.hjson has a minGameVersion", minGameVersion != null && !minGameVersion.isEmpty(), minGameVersion);
        VerifySupport.check("main class is inside the jar", zip.getEntry(main.replace('.', '/') + ".class") != null);
    }

    private static void verifySprites(ZipFile zip){
        VerifySupport.section("sprites");

        for(Object[] content : CONTENTS){
            String contentName = MOD_NAME + "-" + content[0];
            String spriteFile = findSpriteForRegion(zip, MOD_NAME, contentName);
            VerifySupport.check("a sprite in sprites/ packs to the region '" + contentName + "'",
                spriteFile != null, spriteFile == null ? describeSprites(zip) : spriteFile);
        }
    }

    private static void verifyBundles(ZipFile zip, Path tmp) throws Exception{
        String[] bundleNames = {"bundles/bundle.properties", "bundles/bundle_zh_CN.properties"};

        for(String bundleName : bundleNames){
            VerifySupport.section(bundleName);

            var props = VerifySupport.readBundle(zip, bundleName, tmp);

            for(Object[] content : CONTENTS){
                String contentName = MOD_NAME + "-" + content[0];
                int placeholders = (Integer)content[1];

                for(int i = 2; i < content.length; i++){
                    String key = ((String)content[i]).replace("%s", contentName);
                    VerifySupport.check("defines '" + key + "'", props.get(key) != null);
                }

                String details = props.get(contentName + ".details");
                boolean placeholdersOk = details != null;
                for(int i = 0; placeholdersOk && i < placeholders; i++){
                    placeholdersOk = details.contains("{" + i + "}");
                }
                VerifySupport.check("'" + contentName + ".details' keeps its " + placeholders + " placeholders",
                    placeholdersOk);
            }
        }
    }

    // --------------------------------------------------------------------- utils

    private static String jsonValue(String hjson, String key){
        var matcher = java.util.regex.Pattern
            .compile("(?m)^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*:\\s*\"?([^\"\\r\\n]+)\"?\\s*$")
            .matcher(hjson);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /**
     * Finds the sprite file whose packed atlas region equals {@code regionName}.
     * Mirrors Mods.packSprites(): the mod name is prepended unless the part after the
     * first hyphen of the file name already starts with "&lt;mod name&gt;-".
     */
    private static String findSpriteForRegion(ZipFile zip, String modName, String regionName){
        var entries = zip.entries();
        while(entries.hasMoreElements()){
            String name = entries.nextElement().getName();
            if(!name.startsWith("sprites/") || !name.endsWith(".png")) continue;
            if(name.indexOf('/', "sprites/".length()) != -1) continue;

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
}
