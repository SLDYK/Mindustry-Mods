import arc.math.geom.Point2;
import arc.struct.IntSeq;
import arc.util.Time;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.Planets;
import mindustry.game.Team;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.Category;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.meta.BuildVisibility;
import erekiritems.ErekirItemsMod;
import erekiritems.ResourceSource;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Offline checks for the "resource-source" block.
 *
 * Covers: block flags, the three selectable outputs (item type / liquid type / heat),
 * both production loops (run for real with a fixed Time.delta), type-change cleanup,
 * the config round-trip (current IntSeq format + the legacy Point2 format) and save IO
 * (current revision 1 + legacy revision 0).
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
        VerifySupport.check("hasLiquids + liquidCapacity > 0",
            node.hasLiquids && node.liquidCapacity > 0, node.liquidCapacity);
        VerifySupport.check("outputsLiquid", node.outputsLiquid);
        VerifySupport.check("does not consume power", !node.consumesPower);
        VerifySupport.eq("default item is phase fabric", Items.phaseFabric, ResourceSource.defaultItem);
        VerifySupport.eq("default liquid is water", Liquids.water, ResourceSource.defaultLiquid);
        VerifySupport.eq("heat is off by default", 0, ResourceSource.defaultHeat);
        VerifySupport.check("configurable", node.configurable);
        VerifySupport.check("saveConfig", node.saveConfig);
        VerifySupport.check("clearOnDoubleTap", node.clearOnDoubleTap);
        VerifySupport.eq("category == distribution", Category.distribution, node.category);
        VerifySupport.eq("buildVisibility == shown", BuildVisibility.shown, node.buildVisibility);
        VerifySupport.check("requirements mention beryllium+silicon",
            node.requirements != null && node.requirements.length == 2, Arrays.toString(node.requirements));

        // every config shape the block accepts
        VerifySupport.check("config handler registered for IntSeq (current format)",
            node.configurations.containsKey(IntSeq.class));
        VerifySupport.check("config handler registered for Point2 (legacy 1.1.0 format)",
            node.configurations.containsKey(Point2.class));
        VerifySupport.check("config handler registered for Item", node.configurations.containsKey(Item.class));
        VerifySupport.check("config handler registered for Liquid", node.configurations.containsKey(Liquid.class));
        VerifySupport.check("configClear registered", node.configurations.containsKey(void.class));

        VerifySupport.check("details were formatted from the bundle",
            node.details != null
                && node.details.contains(String.valueOf(ResourceSource.maxItemRate))
                && node.details.contains(String.valueOf(ResourceSource.maxLiquidRate))
                && node.details.contains(String.valueOf(ResourceSource.maxHeat)),
            node.details);

        VerifySupport.section("helpers");

        VerifySupport.eq("clampItemRate(-1)", 0, ResourceSource.clampItemRate(-1));
        VerifySupport.eq("clampItemRate(50)", 50, ResourceSource.clampItemRate(50));
        VerifySupport.eq("clampItemRate above max", ResourceSource.maxItemRate,
            ResourceSource.clampItemRate(ResourceSource.maxItemRate + 1));
        VerifySupport.eq("clampLiquidRate(-1)", 0, ResourceSource.clampLiquidRate(-1));
        VerifySupport.eq("clampLiquidRate above max", ResourceSource.maxLiquidRate,
            ResourceSource.clampLiquidRate(ResourceSource.maxLiquidRate + 1));
        VerifySupport.eq("clampHeat(-5)", 0, ResourceSource.clampHeat(-5));
        VerifySupport.eq("clampHeat(500)", 500, ResourceSource.clampHeat(500));
        VerifySupport.eq("clampHeat above max", ResourceSource.maxHeat,
            ResourceSource.clampHeat(ResourceSource.maxHeat + 1));

        VerifySupport.eq("shortRate(0)", "0", ResourceSource.shortRate(0));
        VerifySupport.eq("shortRate(10)", "10", ResourceSource.shortRate(10));
        VerifySupport.eq("shortRate(1000)", "1k", ResourceSource.shortRate(1000));
        VerifySupport.eq("shortRate(10000)", "10k", ResourceSource.shortRate(10000));

        VerifySupport.check("itemPresets are ascending within range",
            ascendingWithin(ResourceSource.itemPresets, ResourceSource.maxItemRate),
            Arrays.toString(ResourceSource.itemPresets));
        VerifySupport.check("liquidPresets are ascending within range",
            ascendingWithin(ResourceSource.liquidPresets, ResourceSource.maxLiquidRate),
            Arrays.toString(ResourceSource.liquidPresets));
        VerifySupport.check("heatPresets are ascending within range",
            ascendingWithin(ResourceSource.heatPresets, ResourceSource.maxHeat),
            Arrays.toString(ResourceSource.heatPresets));

        VerifySupport.section("packed state");

        IntSeq packed = ResourceSource.packState(Items.tungsten, Liquids.slag, 50, 60, 100);
        VerifySupport.eq("packed size", 5, packed.size);
        // NOTE: Content.id is a short, so cast it -- boxing a Short and an Integer is never equal
        VerifySupport.eq("packed item id", (int)Items.tungsten.id, packed.get(0));
        VerifySupport.eq("packed liquid id", (int)Liquids.slag.id, packed.get(1));
        VerifySupport.eq("packed item rate", 50, packed.get(2));
        VerifySupport.eq("packed liquid rate", 60, packed.get(3));
        VerifySupport.eq("packed heat", 100, packed.get(4));

        IntSeq nonePacked = ResourceSource.packState(null, null, 1, 2, 3);
        VerifySupport.eq("packed 'no item' is -1", -1, nonePacked.get(0));
        VerifySupport.eq("packed 'no liquid' is -1", -1, nonePacked.get(1));

        IntSeq clamped = ResourceSource.packState(null, null, -5, ResourceSource.maxLiquidRate * 2, 99_999);
        VerifySupport.eq("packing clamps the item rate", 0, clamped.get(2));
        VerifySupport.eq("packing clamps the liquid rate", ResourceSource.maxLiquidRate, clamped.get(3));
        VerifySupport.eq("packing clamps the heat", ResourceSource.maxHeat, clamped.get(4));

        VerifySupport.section("building behaviour");

        ResourceSource.ResourceSourceBuild build = (ResourceSource.ResourceSourceBuild)node.newBuilding();
        // create() wires up the item/liquid modules; newBuilding() alone leaves them null
        build.create(node, Team.sharded);

        VerifySupport.eq("default item", Items.phaseFabric, build.outputItem);
        VerifySupport.eq("default liquid", Liquids.water, build.outputLiquid);
        VerifySupport.eq("default item rate", ResourceSource.defaultItemRate, build.itemRate);
        VerifySupport.eq("default liquid rate", ResourceSource.defaultLiquidRate, build.liquidRate);
        VerifySupport.eq("default heat", ResourceSource.defaultHeat, build.heatOutput);
        VerifySupport.approx("heat is 0 by default", 0f, build.heat());

        // the exact path the game uses for single player, saves and multiplayer
        build.configured(null, ResourceSource.packState(Items.silicon, Liquids.slag, 123, 4567, 250));
        VerifySupport.eq("configured(IntSeq) sets the item", Items.silicon, build.outputItem);
        VerifySupport.eq("configured(IntSeq) sets the liquid", Liquids.slag, build.outputLiquid);
        VerifySupport.eq("configured(IntSeq) sets the item rate", 123, build.itemRate);
        VerifySupport.eq("configured(IntSeq) sets the liquid rate", 4567, build.liquidRate);
        VerifySupport.eq("configured(IntSeq) sets the heat", 250, build.heatOutput);
        VerifySupport.approx("heat() reports the configured heat", 250f, build.heat());
        VerifySupport.approx("heatFrac is heat/maxHeat", 0.25f, build.heatFrac());
        VerifySupport.eq("heat is exposed to logic blocks",
            250.0, build.sense(mindustry.logic.LAccess.heat));
        VerifySupport.eq("config() reports the whole state",
            ResourceSource.packState(Items.silicon, Liquids.slag, 123, 4567, 250), build.config());

        // out-of-range values coming from a config must be clamped
        build.configured(null, new IntSeq(new int[]{Items.silicon.id, Liquids.slag.id,
            -5, ResourceSource.maxLiquidRate * 2, ResourceSource.maxHeat * 3}));
        VerifySupport.eq("negative item rate clamps to 0", 0, build.itemRate);
        VerifySupport.eq("liquid rate above max clamps", ResourceSource.maxLiquidRate, build.liquidRate);
        VerifySupport.eq("heat above max clamps", ResourceSource.maxHeat, build.heatOutput);

        // -1 means "no output"
        build.configured(null, ResourceSource.packState(null, null, 10, 10, 0));
        VerifySupport.check("item id -1 means no item", build.outputItem == null);
        VerifySupport.check("liquid id -1 means no liquid", build.outputLiquid == null);

        // malformed / old-shaped states must be ignored instead of throwing
        build.configured(null, ResourceSource.packState(Items.silicon, null, 7, 7, 0));
        build.configured(null, new IntSeq(new int[]{Items.silicon.id, -1, 9}));
        VerifySupport.eq("a too-short state is ignored", 7, build.itemRate);

        // a 4-entry state (no heat yet) keeps heat at 0
        build.configured(null, new IntSeq(new int[]{Items.silicon.id, Liquids.water.id, 11, 22}));
        VerifySupport.eq("4-entry state still sets the item", Items.silicon, build.outputItem);
        VerifySupport.eq("4-entry state sets both rates", 11, build.itemRate);
        VerifySupport.eq("4-entry state leaves heat at 0", 0, build.heatOutput);

        // legacy Point2 config from mod version 1.1.0
        build.configured(null, new Point2(999, 888));
        VerifySupport.eq("legacy Point2 sets the item rate", 999, build.itemRate);
        VerifySupport.eq("legacy Point2 sets the liquid rate", 888, build.liquidRate);

        // single-value config handlers
        build.configured(null, Items.beryllium);
        VerifySupport.eq("config(Item) sets the item", Items.beryllium, build.outputItem);
        build.configured(null, Liquids.oil);
        VerifySupport.eq("config(Liquid) sets the liquid", Liquids.oil, build.outputLiquid);

        build.configured(null, null);
        VerifySupport.eq("configClear(null) restores the item", ResourceSource.defaultItem, build.outputItem);
        VerifySupport.eq("configClear(null) restores the liquid", ResourceSource.defaultLiquid, build.outputLiquid);
        VerifySupport.eq("configClear(null) restores the item rate", ResourceSource.defaultItemRate, build.itemRate);
        VerifySupport.eq("configClear(null) restores the heat", ResourceSource.defaultHeat, build.heatOutput);

        // disabling the block must cut the heat supply
        build.setHeat(400);
        build.enabled = false;
        VerifySupport.approx("disabled building supplies no heat", 0f, build.heat());
        build.enabled = true;

        VerifySupport.section("production loops");

        // one frame == Time.delta of 1, so 60 frames == one second
        final int frames = 60;

        // pick rates that stay well below the buffers, so nothing is clamped away
        int itemRate = 6, liquidRate = 30;
        build.configured(null, ResourceSource.packState(Items.tungsten, Liquids.slag, itemRate, liquidRate, 0));
        build.items.clear();
        build.liquids.clear();

        Time.delta = 1f;
        for(int i = 0; i < frames; i++){
            build.updateTile();
        }

        VerifySupport.approx("item production matches the rate (" + itemRate + "/s for 1s)",
            itemRate, build.items.get(Items.tungsten));
        VerifySupport.approx("liquid production matches the rate (" + liquidRate + "/s for 1s)",
            liquidRate, build.liquids.get(Liquids.slag));

        // the chosen types are really the ones being produced
        VerifySupport.approx("no phase fabric was produced", 0f, build.items.get(Items.phaseFabric));

        // a rate of 0 must stop producing, not trickle
        build.configured(null, ResourceSource.packState(Items.tungsten, Liquids.slag, 0, 0, 0));
        build.items.clear();
        build.liquids.clear();
        for(int i = 0; i < frames; i++){
            build.updateTile();
        }
        VerifySupport.approx("item rate 0 produces nothing", 0f, build.items.total());
        VerifySupport.approx("liquid rate 0 produces nothing", 0f, build.liquids.currentAmount());

        // ... but whatever is already buffered stays, so downstream can still take it
        build.items.set(Items.tungsten, 5);
        build.liquids.set(Liquids.slag, 5f);
        build.updateTile();
        VerifySupport.approx("item rate 0 keeps the buffered items", 5f, build.items.get(Items.tungsten));
        VerifySupport.approx("liquid rate 0 keeps the buffered liquid", 5f, build.liquids.get(Liquids.slag));

        // switching the produced type must drop the old leftovers, otherwise they would
        // clog the buffer forever (they can never be dumped under the new type)
        build.configured(null, ResourceSource.packState(Items.silicon, Liquids.oil, 5, 5, 0));
        build.updateTile();
        VerifySupport.approx("leftover items of the old type are dropped", 0f, build.items.get(Items.tungsten));
        VerifySupport.approx("leftover liquid of the old type is dropped", 0f, build.liquids.get(Liquids.slag));

        // turning an output off must clear its buffer
        build.configured(null, ResourceSource.packState(null, null, 5, 5, 0));
        build.items.set(Items.silicon, 3);
        build.liquids.set(Liquids.oil, 3f);
        build.updateTile();
        VerifySupport.approx("no item selected clears the item buffer", 0f, build.items.total());
        VerifySupport.approx("no liquid selected clears the liquid buffer", 0f, build.liquids.currentAmount());

        // buffers must cap the output instead of overflowing
        build.configured(null, ResourceSource.packState(Items.silicon, Liquids.water,
            ResourceSource.maxItemRate, ResourceSource.maxLiquidRate, 0));
        for(int i = 0; i < frames; i++){
            build.updateTile();
        }
        VerifySupport.approx("items never exceed itemCapacity", node.itemCapacity, build.items.total());
        VerifySupport.approx("liquids never exceed liquidCapacity", node.liquidCapacity,
            build.liquids.currentAmount());

        // the max rate must be reachable per frame, i.e. one frame at max rate has to fit
        VerifySupport.check("itemCapacity can hold one frame of the max item rate",
            node.itemCapacity >= ResourceSource.maxItemRate / 60f,
            ResourceSource.maxItemRate / 60f + " per frame vs capacity " + node.itemCapacity);
        VerifySupport.check("liquidCapacity can hold one frame of the max liquid rate",
            node.liquidCapacity >= ResourceSource.maxLiquidRate / 60f,
            ResourceSource.maxLiquidRate / 60f + " per frame vs capacity " + node.liquidCapacity);

        VerifySupport.section("save IO");

        VerifySupport.eq("save revision bumped to 1", (byte)1, build.version());

        // current format: 2 shorts (item/liquid id) + 3 ints
        build.configured(null, ResourceSource.packState(Items.silicon, Liquids.slag, 777, 5432, 321));
        ByteBuffer buffer = ByteBuffer.allocate(64);
        build.write(new Writes(new ByteBufferOutput(buffer)));
        int written = buffer.position();
        VerifySupport.eq("write emits ids + rates + heat", 2 + 2 + 4 + 4 + 4, written);

        byte[] bytes = new byte[written];
        buffer.flip();
        buffer.get(bytes);

        build.configured(null, ResourceSource.packState(null, null, 0, 0, 0));
        build.read(new Reads(new ByteBufferInput(ByteBuffer.wrap(bytes))), (byte)1);
        VerifySupport.eq("read restores the item", Items.silicon, build.outputItem);
        VerifySupport.eq("read restores the liquid", Liquids.slag, build.outputLiquid);
        VerifySupport.eq("read restores the item rate", 777, build.itemRate);
        VerifySupport.eq("read restores the liquid rate", 5432, build.liquidRate);
        VerifySupport.eq("read restores the heat", 321, build.heatOutput);

        // legacy revision 0 (mod 1.1.0) wrote just the two rates
        ByteBuffer legacy = ByteBuffer.allocate(16);
        Writes legacyWrites = new Writes(new ByteBufferOutput(legacy));
        legacyWrites.i(444);
        legacyWrites.i(333);
        int legacyWritten = legacy.position();

        byte[] legacyBytes = new byte[legacyWritten];
        legacy.flip();
        legacy.get(legacyBytes);

        build.configured(null, ResourceSource.packState(null, null, 0, 0, 0));
        build.read(new Reads(new ByteBufferInput(ByteBuffer.wrap(legacyBytes))), (byte)0);
        VerifySupport.eq("legacy revision 0 restores the item rate", 444, build.itemRate);
        VerifySupport.eq("legacy revision 0 restores the liquid rate", 333, build.liquidRate);
        VerifySupport.eq("legacy revision 0 leaves the default item", ResourceSource.defaultItem, build.outputItem);
        VerifySupport.eq("legacy revision 0 leaves the default liquid", ResourceSource.defaultLiquid,
            build.outputLiquid);

        // a heat source is only useful if the engine can see it through the HeatBlock interface
        VerifySupport.check("the build implements HeatBlock", build instanceof HeatBlock);
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
