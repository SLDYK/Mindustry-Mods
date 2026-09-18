package erekiritems;

import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.Planets;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.blocks.power.BeamNode;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

/**
 * 埃里克尔的可调电力节点。
 *
 * <p>外形与连接方式和原版「光束节点」一致（1x1，向四个正方向发射光束，
 * 自动连到 range 格内第一个带电网的建筑），但它本身是一台<b>纯电源</b>：
 * 只往电网里灌电，不从电网取电。输出功率由玩家点开方块后自行设置。</p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>输出走引擎标准接口 {@link mindustry.gen.Building#getPowerProduction()}：
 *       {@code PowerGraph} 会因为 {@code outputsPower == true && consumesPower == false}
 *       把它登记进 producers，每帧读取该方法的返回值。</li>
 *   <li>游戏内部的一切电量都是「每 tick」值，界面展示的才是「每秒」（×60）。
 *       玩家输入的是每秒值，所以这里要除以 60。</li>
 *   <li>设置值作为方块配置（{@code config(Integer.class, ...)}）走游戏的配置流程，
 *       单机立即生效，联机会同步给主机与所有客户端。</li>
 * </ul>
 */
public class TunableNode extends BeamNode{
    /** 玩家可设置的最大输出，单位：电力/秒。 */
    public static final int maxOutput = 1_000_000;
    /** 默认输出（刚放下 / 双击清空配置后），单位：电力/秒。 */
    public static final int defaultOutput = 1000;
    /** 游戏里电量的内部单位是每 tick，UI 统一按 ×60 当作每秒显示。 */
    public static final float ticksPerSecond = 60f;
    /** 设置界面里的快捷值。 */
    public static final int[] presetOutputs = {0, 100, 1000, 10000, 100000, 1000000};

    /** 建造/详情面板里显示的“最大输出”条目。 */
    public static final Stat maxOutputStat = new Stat("erekir-items-maxoutput");

    public TunableNode(String name){
        super(name);

        // 连接范围比原版光束节点（10）稍大一点，方便接线
        range = 12;
        health = 100;

        // 纯电源：只发电，不从电网取电（PowerGraph 据此把它归入 producers）
        outputsPower = true;
        consumesPower = false;

        // 允许玩家点开方块设置输出
        configurable = true;
        saveConfig = true;
        // 双击已选中的节点 = 恢复默认输出
        clearOnDoubleTap = true;

        // 开局即可用（不占用科技树），并且只归到埃里克尔名下
        alwaysUnlocked = true;
        shownPlanets.add(Planets.erekir);

        // 详情文案里要带上具体的上限/默认值，所以用 format 而不是让语言包写死数字
        details = Core.bundle.format("erekir-items.details", maxOutput, defaultOutput);

        requirements(Category.power, ItemStack.with(Items.beryllium, 20, Items.silicon, 10));

        // 配置值 = 电力/秒（整数）
        config(Integer.class, (TunableNodeBuild build, Integer value) -> build.setOutput(value));
        configClear((TunableNodeBuild build) -> build.setOutput(defaultOutput));
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(maxOutputStat, maxOutput, StatUnit.powerSecond);
    }

    @Override
    public void setBars(){
        super.setBars();

        // 原版光束节点的 "power" 条显示的是整个电网的收支，这里换成这个节点自己的输出，
        // 打开方块就能直接看到当前设的是多少。
        removeBar("power");
        addBar("power", (TunableNodeBuild build) -> new Bar(
            () -> Core.bundle.format("bar.poweroutput",
                Strings.fixed(build.getPowerProduction() * ticksPerSecond * build.timeScale(), 1)),
            () -> Pal.powerBar,
            () -> build.enabled ? 1f : 0f
        ));
    }

    /** 把任意输入夹到合法区间（0 ~ {@link #maxOutput}）。 */
    public static int clampOutput(int perSecond){
        return Mathf.clamp(perSecond, 0, maxOutput);
    }

    /** 快捷按钮上的简写：0 / 100 / 1k / 10k / 100k / 1M。 */
    public static String shortOutput(int perSecond){
        if(perSecond <= 0) return "0";
        if(perSecond % 1_000_000 == 0) return (perSecond / 1_000_000) + "M";
        if(perSecond % 1_000 == 0) return (perSecond / 1_000) + "k";
        return String.valueOf(perSecond);
    }

    public class TunableNodeBuild extends BeamNodeBuild{
        /** 当前输出，单位：电力/秒。 */
        public int powerPerSecond = defaultOutput;

        public void setOutput(int perSecond){
            powerPerSecond = clampOutput(perSecond);
        }

        @Override
        public float getPowerProduction(){
            // PowerGraph 每帧调用这里，返回值是“每 tick”电量
            return enabled ? powerPerSecond / ticksPerSecond : 0f;
        }

        @Override
        public Integer config(){
            return powerPerSecond;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(powerPerSecond);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            setOutput(read.i());
        }

        @Override
        public void buildConfiguration(Table table){
            TextField field = new TextField(String.valueOf(powerPerSecond));
            field.setFilter(TextField.TextFieldFilter.digitsOnly);
            field.setMaxLength(7); // 最大 maxOutput = 1000000，正好 7 位
            field.setAlignment(Align.center);

            table.add(Core.bundle.get("erekir-items.output")).padBottom(6f).row();

            // 输入框 + 应用
            Table input = new Table();
            input.add(field).width(120f).height(42f).padRight(6f);
            input.button(Core.bundle.get("erekir-items.apply"), Styles.flatt, () -> {
                int value = clampOutput(Strings.parseInt(field.getText(), defaultOutput));
                configure(value);
                field.setText(String.valueOf(value));
            }).width(70f).height(42f);
            table.add(input).row();

            // 常用值快捷按钮
            Table presets = new Table();
            for(int i = 0; i < presetOutputs.length; i++){
                int value = presetOutputs[i];
                presets.button(shortOutput(value), Styles.flatt, () -> {
                    configure(value);
                    field.setText(String.valueOf(value));
                }).width(72f).height(38f).pad(2f);
                if(i % 3 == 2) presets.row();
            }
            table.add(presets).padTop(6f).row();
        }
    }
}
