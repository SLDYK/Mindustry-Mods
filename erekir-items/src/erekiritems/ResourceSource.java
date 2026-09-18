package erekiritems;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.util.Align;
import arc.util.Nullable;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.Planets;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.content;

/**
 * 埃里克尔的「资源源」：不需要任何输入，凭空持续产出三种东西，每种都能单独设置。
 *
 * <ul>
 *   <li><b>物品</b>：类型可挑（默认相织物），速率可调；</li>
 *   <li><b>液体</b>：类型可挑（默认水），速率可调；</li>
 *   <li><b>热量</b>：没有“类型”可选（原版只有一种热量），只填数值。</li>
 * </ul>
 *
 * <h3>几个必须知道的引擎事实</h3>
 * <ul>
 *   <li><b>物品</b>：{@code dump(Item)} 是按「我有的 + 对方能收的」转让，送多少取决于我给多少
 *       —— 所以把产出攒在 {@code items} 里再 dump 就能限流。</li>
 *   <li><b>液体</b>：{@code dumpLiquid()} 是靠<b>液位比例差</b>推动的（只有自己液位比例高于对方才送，
 *       送的量 = 差值 × 容量 / scaling）。想精确限速就得把储罐灌满，一灌满又会一次推掉大半罐，
 *       所以这里不走 dumpLiquid，而是按存量逐个邻居 {@link Building#transferLiquid}。</li>
 *   <li><b>热量</b>：热量不是电网那样的全局网络，而是<b>邻接传递</b>。只要建筑实现
 *       {@link HeatBlock}，紧邻的耗热建筑（电热器、熔炉类）就会在自己的
 *       {@code calculateHeat()} 里直接读它的 {@code heat()}。所以这里不需要注册任何东西，
 *       实现接口即可 —— 但也就意味着它只能供热给挨着的建筑，不能隔空送热。</li>
 *   <li><b>容量同时是每帧产出的天花板</b>：{@code itemCapacity} / {@code liquidCapacity}
 *       必须 ≥ {@code maxRate / 60}，否则高速率会被静默截断。</li>
 * </ul>
 *
 * <h3>配置怎么带这么多值</h3>
 * 一次配置要同时决定「物品类型 / 液体类型 / 两个速率 / 热量」，而 {@code TypeIO} 只认
 * 基本类型、内容、{@code Point2}、{@code IntSeq} 等少数几种。这里选 {@link IntSeq}
 * （5 个整数：物品 id、液体 id、物品速率、液体速率、热量；-1 表示“无”），
 * 于是联机同步、存档、蓝图都能完整带上整套设置。
 */
public class ResourceSource extends Block{
    /** 物品速率上限，个/秒。 */
    public static final int maxItemRate = 10_000;
    /** 液体速率上限，单位/秒。 */
    public static final int maxLiquidRate = 10_000;
    /** 热量输出上限，热量单位。原版耗热建筑的需求在 20 ~ 144 之间。 */
    public static final int maxHeat = 1000;

    /** 默认物品速率：10 个/秒。 */
    public static final int defaultItemRate = 10;
    /** 默认液体速率：100 单位/秒。 */
    public static final int defaultLiquidRate = 100;
    /** 默认热量输出：0（不开热）。 */
    public static final int defaultHeat = 0;

    /** 默认产出的物品与液体。 */
    public static final Item defaultItem = Items.phaseFabric;
    public static final Liquid defaultLiquid = Liquids.water;

    /** 设置面板里的快捷值。 */
    public static final int[] itemPresets = {0, 1, 10, 100, 1000};
    public static final int[] liquidPresets = {0, 10, 100, 1000, 10_000};
    public static final int[] heatPresets = {0, 10, 50, 100, 1000};

    /** 详情面板里的三条上限条目。 */
    public static final Stat maxItemRateStat = new Stat("erekir-items-resource-source-maxitemrate");
    public static final Stat maxLiquidRateStat = new Stat("erekir-items-resource-source-maxliquidrate");
    public static final Stat maxHeatStat = new Stat("erekir-items-resource-source-maxheat");

    /** 设置面板里物品/液体图标选择器的大小（行数 × 列数）。 */
    public static final int pickerRows = 3, pickerColumns = 5;

    public ResourceSource(String name){
        super(name);

        update = true;
        solid = true;

        // 两个输出方向都要开。
        // 容量不只是“仓有多大”，它同时是每帧能产出的天花板（上限速率时每帧要
        // maxRate/60 ≈ 167 件），所以必须高于这个数，否则高速率会被静默截断。
        hasItems = true;
        itemCapacity = 200;
        hasLiquids = true;
        liquidCapacity = 200f;
        outputsLiquid = true;
        // 只出不进，不需要画液体的流动方向
        displayFlow = false;
        // 它不是电力建筑：既不发电也不耗电（Block 的默认值是 true）
        outputsPower = false;
        consumesPower = false;

        group = BlockGroup.transportation;
        selectionRows = pickerRows;
        selectionColumns = pickerColumns;

        // 三种输出都能改
        configurable = true;
        saveConfig = true;
        clearOnDoubleTap = true;

        // 开局即可用（不占科技树），只归到埃里克尔名下
        alwaysUnlocked = true;
        shownPlanets.add(Planets.erekir);

        details = Core.bundle.format("erekir-items-resource-source.details",
            maxItemRate, maxLiquidRate, maxHeat, defaultItemRate, defaultLiquidRate);

        requirements(Category.distribution, ItemStack.with(Items.beryllium, 40, Items.silicon, 20));

        // 完整设置（当前格式）：物品 id、液体 id、物品速率、液体速率、热量
        config(IntSeq.class, (ResourceSourceBuild build, IntSeq value) -> build.applyState(value));
        // 下面三种是兼容 / 互操作用的：
        //  - Point2 是本模组 1.1.0 的旧格式（只有两个速率），旧存档与旧蓝图还能用；
        //  - Item / Liquid 让别的东西（或手写蓝图）能单独指定一个类型。
        config(Point2.class, (ResourceSourceBuild build, Point2 value) -> build.setRates(value.x, value.y));
        config(Item.class, (ResourceSourceBuild build, Item item) -> build.setOutputItem(item));
        config(Liquid.class, (ResourceSourceBuild build, Liquid liquid) -> build.setOutputLiquid(liquid));

        configClear((ResourceSourceBuild build) -> build.resetToDefaults());
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(maxItemRateStat, maxItemRate, StatUnit.itemsSecond);
        stats.add(maxLiquidRateStat, maxLiquidRate, StatUnit.liquidSecond);
        stats.add(maxHeatStat, maxHeat, StatUnit.heatUnits);
    }

    @Override
    public void setBars(){
        super.setBars();

        // super 里已经加好了原版的 items / liquid 条（显示仓里的存量）。
        // 热量条只在真的开了热量输出时才显示 —— addBar 的 Func 返回 null 就是不显示。
        addBar("heat", (ResourceSourceBuild build) -> build.heatOutput <= 0 ? null : new Bar(
            () -> Core.bundle.format("bar.heatamount", (int)build.heat()),
            () -> Pal.lightOrange,
            () -> build.heat() / maxHeat));
    }

    // ------------------------------------------------------------------ 静态工具

    /** 把任意输入夹到合法区间。 */
    public static int clampItemRate(int perSecond){
        return Mathf.clamp(perSecond, 0, maxItemRate);
    }

    public static int clampLiquidRate(int perSecond){
        return Mathf.clamp(perSecond, 0, maxLiquidRate);
    }

    public static int clampHeat(int heat){
        return Mathf.clamp(heat, 0, maxHeat);
    }

    /** 快捷按钮上的简写：0 / 1 / 10 / 1k / 10k / 1M。 */
    public static String shortRate(int value){
        if(value <= 0) return "0";
        if(value % 1_000_000 == 0) return (value / 1_000_000) + "M";
        if(value % 1_000 == 0) return (value / 1_000) + "k";
        return String.valueOf(value);
    }

    /** 打包一份完整设置，作为配置值（{@code -1} = 该输出关闭）。 */
    public static IntSeq packState(@Nullable Item item, @Nullable Liquid liquid,
        int itemRate, int liquidRate, int heat){
        IntSeq state = new IntSeq(5);
        state.add(item == null ? -1 : item.id);
        state.add(liquid == null ? -1 : liquid.id);
        state.add(clampItemRate(itemRate));
        state.add(clampLiquidRate(liquidRate));
        state.add(clampHeat(heat));
        return state;
    }

    /** 输入框的位数上限。 */
    private static int digits(int max){
        return String.valueOf(max).length();
    }

    /** 内容 id 反查；越界或 -1 一律当“无”，免得存档里出现旧 id 时炸掉。 */
    private static @Nullable Item itemFromId(int id){
        return id >= 0 && id < content.items().size ? content.item(id) : null;
    }

    private static @Nullable Liquid liquidFromId(int id){
        return id >= 0 && id < content.liquids().size ? content.liquid(id) : null;
    }

    // ------------------------------------------------------------------ 建筑

    public class ResourceSourceBuild extends Building implements HeatBlock{
        /** 当前产出的物品，null = 不产物品。 */
        public @Nullable Item outputItem = defaultItem;
        /** 当前产出的液体，null = 不产液体。 */
        public @Nullable Liquid outputLiquid = defaultLiquid;
        /** 物品产出，个/秒。 */
        public int itemRate = defaultItemRate;
        /** 液体产出，单位/秒。 */
        public int liquidRate = defaultLiquidRate;
        /** 热量输出，热量单位。 */
        public int heatOutput = defaultHeat;

        /** 不够一件物品的零头，攒着下次一起出。 */
        private float itemAccum;

        // ---- 设置 ----

        public void resetToDefaults(){
            outputItem = defaultItem;
            outputLiquid = defaultLiquid;
            itemRate = defaultItemRate;
            liquidRate = defaultLiquidRate;
            heatOutput = defaultHeat;
        }

        public void setOutputItem(@Nullable Item item){
            outputItem = item;
        }

        public void setOutputLiquid(@Nullable Liquid liquid){
            outputLiquid = liquid;
        }

        public void setRates(int itemPerSecond, int liquidPerSecond){
            itemRate = clampItemRate(itemPerSecond);
            liquidRate = clampLiquidRate(liquidPerSecond);
        }

        public void setHeat(int heat){
            heatOutput = clampHeat(heat);
        }

        /** 应用一份完整设置（来自配置值）。 */
        public void applyState(@Nullable IntSeq state){
            // 至少要有 4 项；热量是后加的，缺了就当 0（向前兼容）
            if(state == null || state.size < 4) return;

            setOutputItem(itemFromId(state.get(0)));
            setOutputLiquid(liquidFromId(state.get(1)));
            setRates(state.get(2), state.get(3));
            setHeat(state.size > 4 ? state.get(4) : 0);
        }

        /** 当前设置打包成配置值。 */
        public IntSeq packState(){
            return ResourceSource.packState(outputItem, outputLiquid, itemRate, liquidRate, heatOutput);
        }

        // ---- 热量（HeatBlock）----
        // 热量靠邻接传递：紧邻的耗热建筑会直接读这里的 heat()，不需要注册到任何网络里。

        @Override
        public float heat(){
            return enabled ? heatOutput : 0f;
        }

        @Override
        public float heatFrac(){
            // 注意要转 float：heatOutput 与 maxHeat 都是 int，整数相除会永远得 0
            return heatOutput / (float)maxHeat;
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.heat) return heat();
            return super.sense(sensor);
        }

        // ---- 每帧产出 ----

        @Override
        public void updateTile(){
            updateItems();
            updateLiquids();
        }

        private void updateItems(){
            if(outputItem == null){
                itemAccum = 0f;
                if(items.total() > 0) items.clear();
                return;
            }

            // 换了产出类型：把上一种攒下的清掉，否则它们永远送不出去、还会堵住仓
            if(items.total() > 0 && !items.has(outputItem)) items.clear();

            if(itemRate > 0){
                itemAccum += itemRate * delta() / 60f;

                int whole = (int)itemAccum;
                if(whole > 0){
                    int added = Math.min(whole, itemCapacity - items.get(outputItem));
                    if(added > 0){
                        items.add(outputItem, added);
                        itemAccum -= added;
                        produced(outputItem, added);
                    }
                }
                // 送不走的时候别无限攒，最多留一仓
                itemAccum = Math.min(itemAccum, itemCapacity);
            }else{
                itemAccum = 0f;
            }

            // 速率设为 0 只是不再补货：仓里剩下的照样送出去，不会凭空消失
            dump(outputItem);
        }

        private void updateLiquids(){
            if(outputLiquid == null){
                if(liquids.currentAmount() > 0.0001f) liquids.clear();
                return;
            }

            // 换了产出类型：清掉上一种液体
            if(liquids.currentAmount() > 0.0001f
                && liquids.get(outputLiquid) < liquids.currentAmount() - 0.0001f){
                liquids.clear();
            }

            if(liquidRate > 0){
                // 凭空补货，储罐容量就是每帧的上限
                liquids.set(outputLiquid,
                    Math.min(liquids.get(outputLiquid) + liquidRate * delta() / 60f, liquidCapacity));
            }

            if(liquids.get(outputLiquid) <= 0.0001f || proximity == null || proximity.size == 0) return;

            // 按当前存量逐个送给邻居。每送一次都要重新读存量：transferLiquid 内部
            // 还会按对方剩余空间再收一次窄，实际送出的可能比这里算的少。
            for(int i = 0; i < proximity.size; i++){
                float remaining = liquids.get(outputLiquid);
                if(remaining <= 0.0001f) break;

                Building other = proximity.get(i);
                if(other == null || other.block == null || !other.block.hasLiquids || other.liquids == null) continue;
                if(!canDumpLiquid(other, outputLiquid)) continue;

                // 让液体桥之类的中转方块把液体送到真正的目的地
                Building dest = other.getLiquidDestination(this, outputLiquid);
                if(dest == null || dest == this || dest.block == null
                    || !dest.block.hasLiquids || dest.liquids == null) continue;

                float space = dest.block.liquidCapacity - dest.liquids.get(outputLiquid);
                float amount = Math.min(remaining, space);
                if(amount <= 0.0001f) continue;

                transferLiquid(dest, amount, outputLiquid);
            }
        }

        // ---- 存档 / 配置 ----

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.s(outputItem == null ? -1 : outputItem.id);
            write.s(outputLiquid == null ? -1 : outputLiquid.id);
            write.i(itemRate);
            write.i(liquidRate);
            write.i(heatOutput);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                setOutputItem(itemFromId(read.s()));
                setOutputLiquid(liquidFromId(read.s()));
                setRates(read.i(), read.i());
                setHeat(read.i());
            }else{
                // revision 0 = 模组 1.1.0：只写了两个速率，类型还是写死的相织物 + 水
                setOutputItem(defaultItem);
                setOutputLiquid(defaultLiquid);
                setRates(read.i(), read.i());
                setHeat(defaultHeat);
            }
        }

        @Override
        public IntSeq config(){
            return packState();
        }

        // ---- 设置面板 ----

        @Override
        public void buildConfiguration(Table table){
            TextField itemRateField = numberField(itemRate, digits(maxItemRate));
            TextField liquidRateField = numberField(liquidRate, digits(maxLiquidRate));
            TextField heatField = numberField(heatOutput, digits(maxHeat));

            table.defaults().left();

            // 物品：标签带当前选择，下面是图标选择器（和原版源方块同一套选择器，
            // 会自动过滤掉本星球用不了的物品）。点图标立即生效。
            table.label(() -> Core.bundle.get("erekir-items-resource-source.item") + "  "
                + contentName(outputItem)).padBottom(4f).row();
            Table itemPicker = new Table();
            ItemSelection.buildTable(null, itemPicker, content.items(), () -> outputItem,
                item -> applyPanel(item, outputLiquid, itemRateField, liquidRateField, heatField),
                false, pickerRows, pickerColumns);
            table.add(itemPicker).padBottom(10f).row();

            // 液体：同一套选择器，传 content.liquids() 即可
            table.label(() -> Core.bundle.get("erekir-items-resource-source.liquid") + "  "
                + contentName(outputLiquid)).padBottom(4f).row();
            Table liquidPicker = new Table();
            ItemSelection.buildTable(null, liquidPicker, content.liquids(), () -> outputLiquid,
                liquid -> applyPanel(outputItem, liquid, itemRateField, liquidRateField, heatField),
                false, pickerRows, pickerColumns);
            table.add(liquidPicker).padBottom(10f).row();

            table.add(numberRow("erekir-items-resource-source.itemrate", itemRateField, itemPresets,
                itemRateField, liquidRateField, heatField)).padBottom(4f).row();
            table.add(numberRow("erekir-items-resource-source.liquidrate", liquidRateField, liquidPresets,
                itemRateField, liquidRateField, heatField)).padBottom(4f).row();
            table.add(numberRow("erekir-items-resource-source.heat", heatField, heatPresets,
                itemRateField, liquidRateField, heatField)).padBottom(10f).row();

            table.button(Core.bundle.get("erekir-items.apply"), Styles.flatt,
                () -> applyPanel(outputItem, outputLiquid, itemRateField, liquidRateField, heatField))
                .width(220f).height(46f).row();
        }

        /** 显示当前选中的内容名，没选就显示「（无）」。 */
        private CharSequence contentName(@Nullable UnlockableContent selected){
            return selected == null
                ? Core.bundle.get("erekir-items-resource-source.none")
                : selected.localizedName;
        }

        /**
         * 读取三个输入框，连同给定的物品/液体类型一起提交。
         * 越界或空值会被夹回合法区间，并把夹过的结果写回输入框，让玩家看到实际生效的值。
         */
        private void applyPanel(@Nullable Item item, @Nullable Liquid liquid,
            TextField itemRateField, TextField liquidRateField, TextField heatField){

            int itemValue = clampItemRate(Strings.parseInt(itemRateField.getText(), itemRate));
            int liquidValue = clampLiquidRate(Strings.parseInt(liquidRateField.getText(), liquidRate));
            int heatValue = clampHeat(Strings.parseInt(heatField.getText(), heatOutput));

            // 必须写全限定名：本类里也有个无参的 packState()
            configure(ResourceSource.packState(item, liquid, itemValue, liquidValue, heatValue));

            itemRateField.setText(String.valueOf(itemValue));
            liquidRateField.setText(String.valueOf(liquidValue));
            heatField.setText(String.valueOf(heatValue));
        }

        private TextField numberField(int value, int maxLength){
            TextField field = new TextField(String.valueOf(value));
            field.setFilter(TextField.TextFieldFilter.digitsOnly);
            field.setMaxLength(maxLength);
            field.setAlignment(Align.center);
            return field;
        }

        /** 一行数值设置：标签 + 输入框 + 快捷按钮（点快捷值立即生效，不必按「应用」）。 */
        private Table numberRow(String labelKey, TextField field, int[] presets,
            TextField itemRateField, TextField liquidRateField, TextField heatField){

            Table row = new Table();
            row.add(Core.bundle.get(labelKey)).left().padRight(6f);
            row.add(field).width(84f).height(40f).padRight(6f);

            for(int preset : presets){
                row.button(shortRate(preset), Styles.flatt, () -> {
                    field.setText(String.valueOf(preset));
                    applyPanel(outputItem, outputLiquid, itemRateField, liquidRateField, heatField);
                }).width(56f).height(40f).pad(1f);
            }
            return row;
        }
    }
}
