package erekiritems;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.Planets;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 埃里克尔的「资源源」：不需要任何输入，凭空持续产出一件物品和一种液体
 * （默认是相织物 + 水）。两种产出各自的速率都能在方块设置面板里改。
 *
 * <p>两个输出方向的实现差别很大，原因在引擎里：</p>
 * <ul>
 *   <li><b>物品</b>：{@code dump(Item)} 是按「我有的 + 对方能收的」转让，
 *       送多少取决于我给多少 —— 所以把产出攒在 {@code items} 里再 dump 就能限流。</li>
 *   <li><b>液体</b>：{@code dumpLiquid()} 是靠<b>液位比例差</b>推动的
 *       （只有自己液位比例高于对方才送，送的量 = 差值 × 容量 / scaling）。
 *       想精确控制每秒输出就得把储罐灌满，可一灌满又会一次推掉大半罐。
 *       所以这里不走 dumpLiquid，而是把水攒在储罐里、按存量逐个邻居
 *       {@link Building#transferLiquid} —— 送多少完全由自己说了算。</li>
 * </ul>
 */
public class ResourceSource extends Block{
    /** 产出的物品。 */
    public Item outputItem = Items.phaseFabric;
    /** 产出的液体。 */
    public Liquid outputLiquid = Liquids.water;

    /** 物品速率上限，个/秒。 */
    public static final int maxItemRate = 10_000;
    /** 液体速率上限，单位/秒。 */
    public static final int maxLiquidRate = 10_000;
    /** 默认物品速率：相织物 10 个/秒。 */
    public static final int defaultItemRate = 10;
    /** 默认液体速率：水 100 单位/秒。 */
    public static final int defaultLiquidRate = 100;

    /** 设置面板里的快捷值（物品，个/秒）。 */
    public static final int[] itemPresets = {0, 1, 10, 100, 1000};
    /** 设置面板里的快捷值（液体，单位/秒）。 */
    public static final int[] liquidPresets = {0, 10, 100, 1000, 10_000};

    /** 详情面板里的两条速率条目。 */
    public static final Stat itemRateStat = new Stat("erekir-items-resource-source-itemrate");
    public static final Stat liquidRateStat = new Stat("erekir-items-resource-source-liquidrate");

    public ResourceSource(String name){
        super(name);

        update = true;
        solid = true;

        // 两个输出方向都要开。
        // 容量不只是“仓有多大”：它同时是每帧能产出的天花板（rate 上限时每帧要
        // maxRate/60 ~ 167 件），所以容量必须高于这个数，否则高速率会被静默截断。
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

        // 速率由玩家设置
        configurable = true;
        saveConfig = true;
        clearOnDoubleTap = true;

        // 开局即可用（不占科技树），只归到埃里克尔名下
        alwaysUnlocked = true;
        shownPlanets.add(Planets.erekir);

        details = Core.bundle.format("erekir-items-resource-source.details",
            maxItemRate, maxLiquidRate, defaultItemRate, defaultLiquidRate);

        requirements(Category.distribution, ItemStack.with(Items.beryllium, 40, Items.silicon, 20));

        // 一次配置要同时带上两个速率，所以打包进 Point2（x = 物品，y = 液体）。
        // Point2 是 TypeIO 原生支持的类型，联机同步 / 存档 / 蓝图都走得通。
        config(Point2.class, (ResourceSourceBuild build, Point2 value) -> build.setRates(value.x, value.y));
        configClear((ResourceSourceBuild build) -> build.setRates(defaultItemRate, defaultLiquidRate));
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(itemRateStat, maxItemRate, StatUnit.itemsSecond);
        stats.add(liquidRateStat, maxLiquidRate, StatUnit.liquidSecond);
    }

    /** 把任意输入夹到合法区间。 */
    public static int clampItemRate(int perSecond){
        return Mathf.clamp(perSecond, 0, maxItemRate);
    }

    public static int clampLiquidRate(int perSecond){
        return Mathf.clamp(perSecond, 0, maxLiquidRate);
    }

    /** 快捷按钮上的简写：0 / 1 / 10 / 1k / 10k / 1M。 */
    public static String shortRate(int perSecond){
        if(perSecond <= 0) return "0";
        if(perSecond % 1_000_000 == 0) return (perSecond / 1_000_000) + "M";
        if(perSecond % 1_000 == 0) return (perSecond / 1_000) + "k";
        return String.valueOf(perSecond);
    }

    public class ResourceSourceBuild extends Building{
        /** 物品产出，个/秒。 */
        public int itemRate = defaultItemRate;
        /** 液体产出，单位/秒。 */
        public int liquidRate = defaultLiquidRate;

        /** 不够一件物品的零头，攒着下次一起出。 */
        private float itemAccum;

        public void setRates(int itemPerSecond, int liquidPerSecond){
            itemRate = clampItemRate(itemPerSecond);
            liquidRate = clampLiquidRate(liquidPerSecond);
        }

        @Override
        public void updateTile(){
            updateItems();
            updateLiquids();
        }

        private void updateItems(){
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
            if(liquidRate > 0){
                // 产水：凭空补货，储罐容量就是每帧的上限
                liquids.set(outputLiquid,
                    Math.min(liquids.get(outputLiquid) + liquidRate * delta() / 60f, liquidCapacity));
            }

            // 速率设为 0 只是不再补货：仓里剩下的照样送出去，不会凭空消失
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

        @Override
        public Point2 config(){
            return new Point2(itemRate, liquidRate);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(itemRate);
            write.i(liquidRate);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            setRates(read.i(), read.i());
        }

        @Override
        public void buildConfiguration(Table table){
            TextField itemField = numberField(itemRate, String.valueOf(maxItemRate).length());
            TextField liquidField = numberField(liquidRate, String.valueOf(maxLiquidRate).length());

            table.add(Core.bundle.get("erekir-items-resource-source.itemrate")).left().padBottom(4f).row();
            table.add(itemField).width(150f).height(42f).padBottom(4f).row();
            table.add(presetRow(itemPresets, true, itemField, liquidField)).padBottom(10f).row();

            table.add(Core.bundle.get("erekir-items-resource-source.liquidrate")).left().padBottom(4f).row();
            table.add(liquidField).width(150f).height(42f).padBottom(4f).row();
            table.add(presetRow(liquidPresets, false, itemField, liquidField)).padBottom(10f).row();

            // 两个输入框一起提交：一次点击 = 一次配置同步
            table.button(Core.bundle.get("erekir-items.apply"), Styles.flatt,
                () -> applyFields(itemField, liquidField)).width(158f).height(44f).row();
        }

        /**
         * 读取两个输入框，提交一次配置。
         * 越界/空值会被夹回合法区间，并把夹过的结果写回输入框，让玩家看到实际生效的值。
         */
        private void applyFields(TextField itemField, TextField liquidField){
            int itemValue = clampItemRate(Strings.parseInt(itemField.getText(), itemRate));
            int liquidValue = clampLiquidRate(Strings.parseInt(liquidField.getText(), liquidRate));

            configure(new Point2(itemValue, liquidValue));

            itemField.setText(String.valueOf(itemValue));
            liquidField.setText(String.valueOf(liquidValue));
        }

        private TextField numberField(int value, int maxLength){
            TextField field = new TextField(String.valueOf(value));
            field.setFilter(TextField.TextFieldFilter.digitsOnly);
            field.setMaxLength(maxLength);
            field.setAlignment(Align.center);
            return field;
        }

        /**
         * 一行快捷值，点一下立即生效（不必再按「应用」）。
         *
         * @param itemRow true 表示这一行改的是物品速率，false 表示液体速率
         */
        private Table presetRow(int[] presets, boolean itemRow, TextField itemField, TextField liquidField){
            Table row = new Table();
            for(int i = 0; i < presets.length; i++){
                int value = presets[i];
                row.button(shortRate(value), Styles.flatt, () -> {
                    if(itemRow){
                        itemField.setText(String.valueOf(value));
                    }else{
                        liquidField.setText(String.valueOf(value));
                    }
                    applyFields(itemField, liquidField);
                }).width(62f).height(38f).pad(2f);
            }
            return row;
        }
    }
}
