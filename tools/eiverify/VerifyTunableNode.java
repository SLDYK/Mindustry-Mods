import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Planets;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.world.meta.BuildVisibility;
import erekiritems.ErekirItemsMod;
import erekiritems.TunableNode;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * Offline checks for the "tunable-node" block: block flags, the config round-trip
 * (including clamping and configClear), the per-tick power math and save IO.
 *
 * Run:  java -cp "out;&lt;game jar&gt;;&lt;mod jar&gt;" VerifyTunableNode &lt;mod jar&gt;
 */
public class VerifyTunableNode{
    public static void main(String[] args) throws Exception{
        Path jar = VerifySupport.requireJar(args);
        Path tmp = VerifySupport.tempDir("eiverify-tunable-node");

        try{
            VerifySupport.setupHeadless(jar, tmp);
            verifyBlock();
        }finally{
            VerifySupport.cleanup(tmp);
        }

        VerifySupport.finish();
    }

    private static void verifyBlock(){
        VerifySupport.section("block definition");

        TunableNode node = new TunableNode(ErekirItemsMod.NODE_NAME);

        VerifySupport.eq("block name", "tunable-node", node.name);
        VerifySupport.check("alwaysUnlocked (no research needed)", node.alwaysUnlocked);
        VerifySupport.check("shownPlanets == {erekir}",
            node.shownPlanets.size == 1 && node.shownPlanets.contains(Planets.erekir), node.shownPlanets);
        VerifySupport.check("pure power source: outputsPower && !consumesPower",
            node.outputsPower && !node.consumesPower);
        VerifySupport.check("configurable", node.configurable);
        VerifySupport.check("saveConfig", node.saveConfig);
        VerifySupport.check("clearOnDoubleTap", node.clearOnDoubleTap);
        VerifySupport.eq("category == power", Category.power, node.category);
        VerifySupport.eq("buildVisibility == shown", BuildVisibility.shown, node.buildVisibility);
        VerifySupport.eq("range", 12, node.range);
        VerifySupport.eq("health", 100, node.health);
        VerifySupport.check("requirements mention beryllium+silicon",
            node.requirements != null && node.requirements.length == 2, Arrays.toString(node.requirements));
        VerifySupport.check("config handler registered for Integer", node.configurations.containsKey(Integer.class));
        VerifySupport.check("configClear registered", node.configurations.containsKey(void.class));
        VerifySupport.check("details were formatted from the bundle",
            node.details != null
                && node.details.contains(String.valueOf(TunableNode.maxOutput))
                && node.details.contains(String.valueOf(TunableNode.defaultOutput)),
            node.details);

        VerifySupport.section("helpers");

        VerifySupport.eq("clampOutput(-1)", 0, TunableNode.clampOutput(-1));
        VerifySupport.eq("clampOutput(0)", 0, TunableNode.clampOutput(0));
        VerifySupport.eq("clampOutput(12345)", 12345, TunableNode.clampOutput(12345));
        VerifySupport.eq("clampOutput(maxOutput)", TunableNode.maxOutput, TunableNode.clampOutput(TunableNode.maxOutput));
        VerifySupport.eq("clampOutput(Integer.MAX_VALUE)", TunableNode.maxOutput,
            TunableNode.clampOutput(Integer.MAX_VALUE));

        VerifySupport.eq("shortOutput(0)", "0", TunableNode.shortOutput(0));
        VerifySupport.eq("shortOutput(100)", "100", TunableNode.shortOutput(100));
        VerifySupport.eq("shortOutput(1000)", "1k", TunableNode.shortOutput(1000));
        VerifySupport.eq("shortOutput(10000)", "10k", TunableNode.shortOutput(10000));
        VerifySupport.eq("shortOutput(100000)", "100k", TunableNode.shortOutput(100000));
        VerifySupport.eq("shortOutput(1000000)", "1M", TunableNode.shortOutput(1000000));

        boolean presetsOk = true;
        int last = -1;
        for(int value : TunableNode.presetOutputs){
            presetsOk &= value >= 0 && value <= TunableNode.maxOutput && value > last;
            last = value;
        }
        VerifySupport.check("presetOutputs are ascending within 0..maxOutput", presetsOk,
            Arrays.toString(TunableNode.presetOutputs));

        VerifySupport.section("building behaviour");

        Building build = node.newBuilding();
        build.block = node;
        TunableNode.TunableNodeBuild typed = (TunableNode.TunableNodeBuild)build;

        VerifySupport.eq("default output", TunableNode.defaultOutput, typed.powerPerSecond);
        VerifySupport.check("buildings are enabled by default", typed.enabled);
        VerifySupport.approx("default production is per-tick (perSecond/60)",
            TunableNode.defaultOutput / 60f, typed.getPowerProduction());

        // this is the exact path the game uses for single player, saves and multiplayer
        build.configured(null, 12345);
        VerifySupport.eq("configured(12345) applies", 12345, typed.powerPerSecond);
        VerifySupport.eq("config() reports the value", Integer.valueOf(12345), build.config());

        build.configured(null, -100);
        VerifySupport.eq("negative values clamp to 0", 0, typed.powerPerSecond);
        VerifySupport.approx("production is 0 when the output is 0", 0f, typed.getPowerProduction());

        build.configured(null, TunableNode.maxOutput + 12345);
        VerifySupport.eq("values above maxOutput clamp", TunableNode.maxOutput, typed.powerPerSecond);

        build.configured(null, 6000);
        VerifySupport.approx("6000/s == 100 power per tick", 100f, typed.getPowerProduction());
        VerifySupport.approx("production * 60 round-trips back to the setting", 6000f,
            typed.getPowerProduction() * 60f);

        build.configured(null, null);
        VerifySupport.eq("configClear(null) restores the default", TunableNode.defaultOutput, typed.powerPerSecond);

        build.enabled = false;
        VerifySupport.approx("disabled building produces nothing", 0f, typed.getPowerProduction());
        build.enabled = true;

        VerifySupport.section("save IO");

        typed.setOutput(7777);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(64);
        build.write(new Writes(new ByteBufferOutput(buffer)));
        int written = buffer.position();
        VerifySupport.eq("write emits the configured value", 4, written);

        byte[] bytes = new byte[written];
        buffer.flip();
        buffer.get(bytes);

        typed.powerPerSecond = 0;
        build.read(new Reads(new ByteBufferInput(java.nio.ByteBuffer.wrap(bytes))), (byte)0);
        VerifySupport.eq("read restores the output", 7777, typed.powerPerSecond);
    }
}
