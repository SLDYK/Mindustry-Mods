import arc.Core;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.I18NBundle;
import arc.util.io.PropertiesUtils;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shared plumbing for the erekir-items offline verifiers.
 *
 * Everything here runs without starting the game: it only needs the game jar
 * (for the classes) and the built mod jar (for the bundles and sprites).
 */
public class VerifySupport{
    private static int checks = 0, failures = 0;

    /** Reads the mod jar path from the command line, failing loudly if it is missing. */
    public static Path requireJar(String[] args){
        if(args.length < 1){
            System.err.println("usage: <verifier> <mod jar>");
            System.exit(2);
        }
        Path jar = Path.of(args[0]);
        if(!Files.isRegularFile(jar)){
            System.err.println("mod jar not found: " + jar);
            System.exit(2);
        }
        return jar;
    }

    public static Path tempDir(String prefix) throws Exception{
        return Files.createTempDirectory(prefix);
    }

    public static void cleanup(Path dir){
        try(var stream = Files.walk(dir)){
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try{ Files.delete(p); }catch(Exception ignored){}
            });
        }catch(Exception ignored){
            // best effort
        }
    }

    /**
     * Builds the smallest environment the game's content loader needs.
     *
     * The whole point is {@code createBaseContent()}: it runs the real base-content
     * creation path (Items, Liquids, Planets, ...), which is what makes it possible to
     * construct a mod block outside the game at all. Cutting it short (e.g. calling
     * Items.load() directly) fails, because PlanetGenerator touches Blocks on the way.
     *
     * The bundle is loaded from the mod jar so that the block constructors'
     * {@code Core.bundle.format(...)} / {@code Core.bundle.get(...)} calls resolve.
     */
    public static void setupHeadless(Path jar, Path tmp) throws Exception{
        Path bundleFile = tmp.resolve("bundle.properties");
        try(ZipFile zip = new ZipFile(jar.toFile())){
            ZipEntry entry = zip.getEntry("bundles/bundle.properties");
            if(entry == null) throw new IllegalStateException("bundles/bundle.properties missing from " + jar);
            try(InputStream in = zip.getInputStream(entry)){
                Files.copy(in, bundleFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Vars.headless = true;
        Vars.content = new ContentLoader();
        // some Building methods (produced(), allowUpdate(), ...) read Vars.state.rules,
        // so the game state has to exist even in this stripped-down environment
        Vars.state = new GameState();

        ObjectMap<String, String> props = new ObjectMap<>();
        PropertiesUtils.load(props, new Fi(bundleFile.toAbsolutePath().toString()).reader());
        I18NBundle bundle = I18NBundle.createEmptyBundle();
        bundle.setProperties(props);
        Core.bundle = bundle;

        Vars.content.createBaseContent();
    }

    /** Loads one bundle from the mod jar and returns it, for independent key checks. */
    public static ObjectMap<String, String> readBundle(ZipFile zip, String entryName, Path tmp) throws Exception{
        ZipEntry entry = zip.getEntry(entryName);
        check(entryName + " exists in the jar", entry != null);
        if(entry == null) return new ObjectMap<>();

        Path out = tmp.resolve(entryName.replace('/', '_'));
        try(InputStream in = zip.getInputStream(entry)){
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }

        ObjectMap<String, String> props = new ObjectMap<>();
        PropertiesUtils.load(props, new Fi(out.toAbsolutePath().toString()).reader());
        return props;
    }

    // ------------------------------------------------------------------ reporting

    public static void section(String name){
        System.out.println();
        System.out.println("== " + name + " ==");
    }

    public static void check(String label, boolean ok){
        check(label, ok, null);
    }

    public static void check(String label, boolean ok, Object actual){
        checks++;
        if(ok){
            System.out.println("  [ok]   " + label);
        }else{
            failures++;
            System.out.println("  [FAIL] " + label + (actual == null ? "" : " (actual: " + actual + ")"));
        }
    }

    public static void eq(String label, Object expected, Object actual){
        check(label, expected == null ? actual == null : expected.equals(actual), actual);
    }

    public static void approx(String label, float expected, float actual){
        check(label, Math.abs(expected - actual) < 0.001f, actual);
    }

    public static int checks(){
        return checks;
    }

    public static int failures(){
        return failures;
    }

    /** Prints the summary; a verifier with failures exits with code 1. */
    public static void finish(){
        System.out.println();
        System.out.println("checks: " + checks + ", failures: " + failures);
        if(failures > 0){
            System.out.println("FAILED");
            System.exit(1);
        }else{
            System.out.println("ALL OK");
        }
    }
}
