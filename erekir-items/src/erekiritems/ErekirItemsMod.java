package erekiritems;

import arc.util.Log;
import mindustry.mod.Mod;

/**
 * 模组入口：把本模组的内容（埃里克尔的建筑 / 物品）注册进游戏。
 *
 * <p>内容创建必须放在 {@code loadContent()}：游戏在 {@code ContentLoader.createModContent()}
 * 阶段调用它，此时原版方块与两个行星都已就绪，模组新建的方块才能正确拿到 id、
 * 归到埃里克尔名下（{@code shownPlanets}）。</p>
 *
 * <p>加新内容时照着下面的写法追加一个 {@code public static} 字段即可，
 * 详细步骤见模组 README 的「加一个新建筑 / 物品」一节。</p>
 */
public class ErekirItemsMod extends Mod{
    /** 可调电力节点的方块名（完整内容名 = 模组内部名 + "-" + 这个名字）。 */
    public static final String NODE_NAME = "tunable-node";
    /** 资源源的方块名。 */
    public static final String SOURCE_NAME = "resource-source";

    /** 已注册的可调电力节点，方便别处引用/调试。 */
    public static TunableNode node;
    /** 已注册的资源源。 */
    public static ResourceSource source;

    @Override
    public void loadContent(){
        node = new TunableNode(NODE_NAME);
        source = new ResourceSource(SOURCE_NAME);

        Log.debug("[erekir-items] content loaded: @ (max @/s), @ (max @ items + @ liquid/s)",
            node.name, TunableNode.maxOutput,
            source.name, ResourceSource.maxItemRate, ResourceSource.maxLiquidRate);
    }
}
