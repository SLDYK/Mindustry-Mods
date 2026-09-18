package erekiritems;

import arc.util.Log;
import mindustry.mod.Mod;

/**
 * 模组入口：只做一件事 —— 把可调电力节点注册进游戏内容。
 *
 * <p>内容创建必须放在 {@code loadContent()}：游戏在 {@code ContentLoader.createModContent()}
 * 阶段调用它，此时原版方块与两个行星都已就绪，模组新建的方块才能正确拿到 id、归到埃里克尔名下。</p>
 */
public class ErekirItemsMod extends Mod{
    /** 方块名（游戏内完整内容名 = 模组内部名 + "-" + 这个名字）。 */
    public static final String NODE_NAME = "tunable-node";

    /** 已注册的方块实例，方便别处引用/调试。 */
    public static TunableNode node;

    @Override
    public void loadContent(){
        node = new TunableNode(NODE_NAME);
        Log.debug("[erekir-items] content loaded: @ (max @/s)", node.name, TunableNode.maxOutput);
    }
}
