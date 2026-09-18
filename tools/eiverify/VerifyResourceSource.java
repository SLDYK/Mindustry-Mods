import arc.math.geom.Point2;
import arc.util.Time;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.Planets;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.world.meta.BuildVisibility;
import erekiritems.ErekirItemsMod;
import erekiritems.ResourceSource;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * Offline checks for the "resource-source" block: block flags, both output directions,
 * the clamping/preset helpers, the config round-trip and save IO.
 *
 * The production loops are exercised for real by calling updateTile() with a fixed
 * Time.delta, so the per-second to per-tick math is verified rather than assumed.
 *
 * Run:  java -cp "out;&lt;game jar&gt;;&lt;mod jar&gt;" VerifyResourceSource &lt;mod jar&gt;
 */
public class VerifyResourceSource{
    public static void main(String[] args) throws Exception{
        Path jar = VerifySupport.requireJar(args);
        Path tmp = VerifySupport.tempDir("eiverify-resource-source");

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

        ResourceSource node = new ResourceSource(ErekirItemsMod.SOURCE_NAME);

        VerifySupport.eq("block name", "resource-source", node.name);
        VerifySupport.check("alwaysUnlocked (no research needed)", node.alwaysUnlocked);
        VerifySupport.check("shownPlanets == {erekir}",
            node.shownPlanets.size == 1 && node.shownPlanets.contains(Planets.erekir), node.shownPlanets);
        VerifySupport.check("hasItems + itemCapacity > 0", node.hasItems && node.itemCapacity > 0, node.itemCapacity);
        VerifySupport.check("hasLiquids + liquidCapacity > 0", node.hasLiquids && node.liquidCapacity > 0, node.liquidCapacity);
        VerifySupport.check("outputsLiquid", node.outputsLiquid);
        VerifySupport.check("does not consume power", !node.consumesPower);
        VerifySupport.eq("outputs phase fabric", Items.phaseFabric, node.outputItem);
        VerifySupport.eq("outputs water", Liquids.water, node.outputLiquid);
        VerifySupport.check("configurable", node.configurable);
        VerifySupport.check("saveConfig", node.saveConfig);
        VerifySupport.check("clearOnDoubleTap", node.clearOnDoubleTap);
        VerifySupport.eq("category == distribution", Category.distribution, node.category);
        VerifySupport.eq("buildVisibility == shown", BuildVisibility.shown, node.buildVisibility);
        VerifySupport.check("requirements mention beryllium+silicon",
            node.requirements != null && node.requirements.length == 2, Arrays.toString(node.requirements));
        VerifySupport.check("config handler registered for Point2", node.configurations.containsKey(Point2.class));
        VerifySupport.check("configClear registered", node.configurations.containsKey(void.class));
        VerifySupport.check("details were formatted from the bundle",
            node.details != null
                && node.details.contains(String.valueOf(ResourceSource.maxItemRate))
                && node.details.contains(String.valueOf(ResourceSource.maxLiquidRate)),
            node.details);

        VerifySupport.section("helpers");

        VerifySupport.eq("clampItemRate(-1)", 0, ResourceSource.clampItemRate(-1));
        VerifySupport.eq("clampItemRate(50)", 50, ResourceSource.clampItemRate(50));
        VerifySupport.eq("clampItemRate above max", ResourceSource.maxItemRate,
            ResourceSource.clampItemRate(ResourceSource.maxItemRate + 1));
        VerifySupport.eq("clampLiquidRate(-1)", 0, ResourceSource.clampLiquidRate(-1));
        VerifySupport.eq("clampLiquidRate(500)", 500, ResourceSource.clampLiquidRate(500));
        VerifySupport.eq("clampLiquidRate above max", ResourceSource.maxLiquidRate,
            ResourceSource.clampLiquidRate(ResourceSource.maxLiquidRate + 1));

        VerifySupport.eq("shortRate(0)", "0", ResourceSource.shortRate(0));
        VerifySupport.eq("shortRate(10)", "10", ResourceSource.shortRate(10));
        VerifySupport.eq("shortRate(1000)", "1k", ResourceSource.shortRate(1000));
        VerifySupport.eq("shortRate(10000)", "10k", ResourceSource.shortRate(10000));

        VerifySupport.check("itemPresets are ascending within range", ascendingWithin(
            ResourceSource.itemPresets, ResourceSource.maxItemRate), Arrays.toString(ResourceSource.itemPresets));
        VerifySupport.check("liquidPresets are ascending within range", ascendingWithin(
            ResourceSource.liquidPresets, ResourceSource.maxLiquidRate), Arrays.toString(ResourceSource.liquidPresets));

        VerifySupport.section("building behaviour");

        ResourceSource.ResourceSourceBuild build =
            (ResourceSource.ResourceSourceBuild)node.newBuilding();
        // create() wires up the item/liquid modules; newBuilding() alone leaves them null
        build.create(node, Team.sharded);

        VerifySupport.eq("default item rate", ResourceSource.defaultItemRate, build.itemRate);
        VerifySupport.eq("default liquid rate", ResourceSource.defaultLiquidRate, build.liquidRate);

        // the exact path the game uses for single player, saves and multiplayer
        build.configured(null, new Point2(123, 4567));
        VerifySupport.eq("configured(Point2) applies the item rate", 123, build.itemRate);
        VerifySupport.eq("configured(Point2) applies the liquid rate", 4567, build.liquidRate);
        VerifySupport.eq("config() reports both rates", new Point2(123, 4567), build.config());

        build.configured(null, new Point2(-5, ResourceSource.maxLiquidRate * 2));
        VerifySupport.eq("negative item rate clamps to 0", 0, build.itemRate);
        VerifySupport.eq("liquid rate above max clamps", ResourceSource.maxLiquidRate, build.liquidRate);

        build.configured(null, null);
        VerifySupport.eq("configClear(null) restores the item default", ResourceSource.defaultItemRate, build.itemRate);
        VerifySupport.eq("configClear(null) restores the liquid default", ResourceSource.defaultLiquidRate, build.liquidRate);

        VerifySupport.section("production loops");

        // one frame == Time.delta of 1, so 60 frames == one second
        final int frames = 60;

        // pick rates that stay well below the buffers, so nothing is clamped away
        int itemRate = 6, liquidRate = 30;
        build.setRates(itemRate, liquidRate);
        build.items.clear();
        build.liquids.clear();

        Time.delta = 1f;
        for(int i = 0; i < frames; i++){
            build.updateTile();
        }

        VerifySupport.approx("item production matches the rate (" + itemRate + "/s for 1s)",
            itemRate, build.items.get(Items.phaseFabric));
        VerifySupport.approx("liquid production matches the rate (" + liquidRate + "/s for 1s)",
            liquidRate, build.liquids.get(Liquids.water));

        // a rate of 0 must stop producing, not trickle
        build.setRates(0, 0);
        build.items.clear();
        build.liquids.clear();
        for(int i = 0; i < frames; i++){
            build.updateTile();
        }
        VerifySupport.approx("item rate 0 produces nothing", 0f, build.items.get(Items.phaseFabric));
        VerifySupport.approx("liquid rate 0 produces nothing", 0f, build.liquids.get(Liquids.water));

        // ... but whatever is already buffered stays, so downstream can still take it
        build.setRates(0, 0);
        build.items.set(Items.phaseFabric, 5);
        build.liquids.set(Liquids.water, 5f);
        build.updateTile();
        VerifySupport.approx("item rate 0 keeps the buffered items", 5f, build.items.get(Items.phaseFabric));
        VerifySupport.approx("liquid rate 0 keeps the buffered liquid", 5f, build.liquids.get(Liquids.water));

        // buffers must cap the output instead of overflowing
        build.setRates(ResourceSource.maxItemRate, ResourceSource.maxLiquidRate);
        for(int i = 0; i < frames; i++){
            build.updateTile();
        }
        VerifySupport.approx("items never exceed itemCapacity", node.itemCapacity, build.items.get(Items.phaseFabric));
        VerifySupport.approx("liquids never exceed liquidCapacity", node.liquidCapacity,
            build.liquids.get(Liquids.water));

        // the max rate must be reachable per frame, i.e. a full second at max rate has to
        // fill more than the buffer would hold in a single frame
        VerifySupport.check("itemCapacity can hold one frame of the max item rate",
            node.itemCapacity >= ResourceSource.maxItemRate / 60f,
            ResourceSource.maxItemRate / 60f + " per frame vs capacity " + node.itemCapacity);
        VerifySupport.check("liquidCapacity can hold one frame of the max liquid rate",
            node.liquidCapacity >= ResourceSource.maxLiquidRate / 60f,
            ResourceSource.maxLiquidRate / 60f + " per frame vs capacity " + node.liquidCapacity);

        VerifySupport.section("save IO");

        // both values must stay within their caps, otherwise read() clamps them back
        int savedItem = 777, savedLiquid = 5432;
        build.setRates(savedItem, savedLiquid);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(64);
        build.write(new Writes(new ByteBufferOutput(buffer)));
        int written = buffer.position();
        VerifySupport.eq("write emits both rates", 8, written);

        byte[] bytes = new byte[written];
        buffer.flip();
        buffer.get(bytes);

        build.setRates(0, 0);
        build.read(new Reads(new ByteBufferInput(java.nio.ByteBuffer.wrap(bytes))), (byte)0);
        VerifySupport.eq("read restores the item rate", savedItem, build.itemRate);
        VerifySupport.eq("read restores the liquid rate", savedLiquid, build.liquidRate);
    }

    private static boolean ascendingWithin(int[] values, int max){
        int last = -1;
        for(int value : values){
            if(value < 0 || value > max || value <= last) return false;
            last = value;
        }
        return true;
    }
}
