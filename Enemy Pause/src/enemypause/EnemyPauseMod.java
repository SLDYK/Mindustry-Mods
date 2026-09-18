package enemypause;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.mod.Mod;
import mindustry.net.Net;

/**
 * Enemy Pause 模组入口。
 *
 * 功能（战役模式下）：
 *   Y  暂停 / 继续敌方侧的倒计时——下一波进攻，以及地图目标里那些计时目标
 *      （如「敌人来袭：9:35」）
 *   U  跳过本轮倒计时：直接推到终点，让后果立刻发生（波次立刻出兵、
 *      目标计时立刻判定完成）
 *
 * 冻的只是倒计时，不是敌人本身：已在场单位照常行动，工厂照常生产。
 * 细节见 EnemyTimers / EnemySkip，状态机见 EnemyPause。
 *
 * 联机时两个键都只在主机端生效：主机端一变状态就广播给所有客户端
 * （EnemyPausePacket），客户端弹同样的提示、并把倒计时也按在同一个读数上。
 */
public class EnemyPauseMod extends Mod{

    /** 暂停键标识：同时用于存设置，以及拼出翻译键 keybind.enemy_pause.name。 */
    public static final String BIND_NAME = "enemy_pause";

    /** 跳过键标识，拼出 keybind.enemy_pause_skip.name。 */
    public static final String SKIP_BIND_NAME = "enemy_pause_skip";

    /** 复用游戏自带的 general 分类，设置界面里会显示已翻译好的"常规"。 */
    public static final String BIND_CATEGORY = "general";

    /**
     * 默认绑定 y 键。
     * Mindustry 自带的绑定已经用掉了 a~z 里除 i/k/l/o/u/y 以外的所有字母，
     * 其中 p 是地图标记(ping)、空格是游戏暂停——语义最接近的两个都被占了。
     * 玩家可以在 设置 -> 按键 里改成自己习惯的键。
     */
    public static final KeyCode DEFAULT_KEY = KeyCode.y;

    /** 跳过键默认绑定 u——和 y 同一批剩下的字母。 */
    public static final KeyCode DEFAULT_SKIP_KEY = KeyCode.u;

    private final EnemyPause pause = EnemyPause.instance;
    private KeyBind toggleBind, skipBind;

    @Override
    public void init(){
        // 封包注册要放在无头服务端检查之前：封包 ID 是按注册顺序递增分配的，
        // 两端注册的东西不一样就会错位。
        // 只在没注册过时注册——mod 重新加载时 init() 会再执行一次，
        // 重复注册会白占一个 ID，让与对端的编号对不上。
        if(Net.getPacketClassId(EnemyPausePacket.class) == -1){
            Net.registerPacket(EnemyPausePacket::new);
        }

        // 按键轮询和 HUD 提示都只存在于客户端，无头服务端不需要
        if(Vars.headless) return;

        // KeyBind.all 属于 arc，跨 mod 重新加载依然存在，正好可以拿它当"是否已注册过"的标记。
        // mod 重新加载时 init() 会再执行一次，那时必须复用已有按键并跳过监听注册：
        // 否则同一次按键会被两个监听处理两次（两次 toggle 互相抵消、看起来就像按键失灵）。
        KeyBind existingToggle = KeyBind.all.find(b -> b.name.equals(BIND_NAME));
        KeyBind existingSkip = KeyBind.all.find(b -> b.name.equals(SKIP_BIND_NAME));

        if(existingToggle != null && existingSkip != null){
            toggleBind = existingToggle;
            skipBind = existingSkip;
            return;
        }

        // 逐个判断而不是整体 return：万一只有一个已注册（比如上个版本只有暂停键），
        // 缺的那个还能补上
        toggleBind = existingToggle != null ? existingToggle : KeyBind.add(BIND_NAME, DEFAULT_KEY, BIND_CATEGORY);
        skipBind = existingSkip != null ? existingSkip : KeyBind.add(SKIP_BIND_NAME, DEFAULT_SKIP_KEY, BIND_CATEGORY);

        // Trigger.update 每帧都触发（游戏内菜单打开时也触发），在这里轮询按键。
        // 用 Events.run 而不是 Events.on：Trigger 是个枚举，注册用的"键"就是枚举常量本身。
        Events.run(Trigger.update, this::pollKeys);

        // afterGameUpdate 在倒计时递减、runWave() 判断、目标推进与敌方 AI 这一帧的更新之后触发，
        // 只有在这个时机写回读数才真的按得住敌方倒计时。
        Events.run(Trigger.afterGameUpdate, pause::update);

        Log.info("[enemy-pause] loaded. Pause: " + DEFAULT_KEY.name()
            + ", Skip: " + DEFAULT_SKIP_KEY.name()
            + " (rebindable in Settings -> Keybinds).");
    }

    private void pollKeys(){
        if(Core.input == null) return;

        if(toggleBind != null && Core.input.keyTap(toggleBind)){
            pause.toggle();
        }
        if(skipBind != null && Core.input.keyTap(skipBind)){
            pause.skip();
        }
    }
}
