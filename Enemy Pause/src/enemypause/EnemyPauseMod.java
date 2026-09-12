package enemypause;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.mod.Mod;

/**
 * Enemy Pause 模组入口。
 *
 * 功能：战役模式下按一个键，暂停 / 继续"下一波敌方进攻"的倒计时。
 */
public class EnemyPauseMod extends Mod{

    /** 按键标识：同时用于存设置，以及拼出翻译键 keybind.enemy_pause.name。 */
    public static final String BIND_NAME = "enemy_pause";

    /** 复用游戏自带的 general 分类，设置界面里会显示已翻译好的"常规"。 */
    public static final String BIND_CATEGORY = "general";

    /**
     * 默认绑定 y 键。
     * Mindustry 自带的绑定已经用掉了 a~z 里除 i/k/l/o/u/y 以外的所有字母，
     * 其中 p 是地图标记(ping)、空格是游戏暂停——语义最接近的两个都被占了。
     * 玩家可以在 设置 -> 按键 里改成自己习惯的键。
     */
    public static final KeyCode DEFAULT_KEY = KeyCode.y;

    private final EnemyWavePause wavePause = new EnemyWavePause();
    private KeyBind toggleBind;

    @Override
    public void init(){
        // 按键轮询和 HUD 提示都只存在于客户端，无头服务端不需要
        if(Vars.headless) return;

        // KeyBind.all 属于 arc，跨 mod 重新加载依然存在，正好可以拿它当"是否已注册过"的标记。
        // mod 重新加载时 init() 会再执行一次，那时必须复用同一个按键并跳过监听注册：
        // 否则同一次按键会被两个监听处理两次，两次 toggle 互相抵消，看起来就像按键失灵。
        KeyBind existing = KeyBind.all.find(b -> b.name.equals(BIND_NAME));
        if(existing != null){
            toggleBind = existing;
            return;
        }

        toggleBind = KeyBind.add(BIND_NAME, DEFAULT_KEY, BIND_CATEGORY);

        // Trigger.update 每帧都触发（游戏内菜单打开时也触发），在这里轮询按键。
        // 用 Events.run 而不是 Events.on：Trigger 是个枚举，注册用的"键"就是枚举常量本身。
        Events.run(Trigger.update, this::pollKey);

        // afterGameUpdate 紧跟在倒计时递减和 runWave() 判断之后，
        // 只有在这个时机写回 state.wavetime 才真的按得住倒计时。
        Events.run(Trigger.afterGameUpdate, wavePause::update);

        Log.info("[enemy-pause] loaded. Default key: " + DEFAULT_KEY.name()
            + " (rebindable in Settings -> Keybinds).");
    }

    private void pollKey(){
        if(toggleBind != null && Core.input != null && Core.input.keyTap(toggleBind)){
            wavePause.toggle();
        }
    }
}
