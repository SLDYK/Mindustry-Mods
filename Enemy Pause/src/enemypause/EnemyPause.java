package enemypause;

import arc.Core;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.maps.Map;

/**
 * 暂停状态机：管 Y 键的开关、生效范围判定，以及"局面变了就自动解除"。
 *
 * 真正冻住哪些倒计时见 EnemyTimers。
 *
 * 注意这里冻的是「下一件事什么时候发生」，不是敌人本身：已经在场的敌方单位
 * 照常行动开火，敌方工厂也照常生产，只是不会启动新的扩建批次 / 新的部队调度。
 */
public class EnemyPause{

    private boolean paused;

    /** 本次暂停会话的各项冻结读数；解除后置空。 */
    private EnemyTimers timers;

    /** 暂停瞬间的波次序号与地图，用来判断暂停期间局面是否已经变了。 */
    private int waveWhenPaused;
    private Map mapWhenPaused;

    public boolean isPaused(){
        return paused;
    }

    /** 暂停 / 继续。由按键轮询调用。 */
    public void toggle(){
        if(paused){
            release(true);
            toast("enemy-pause.resumed");
        }else{
            pause();
        }
    }

    private void pause(){
        GameState state = Vars.state;

        if(!usable(state)){
            toast("enemy-pause.unavailable");
            return;
        }

        paused = true;
        waveWhenPaused = state.wave;
        mapWhenPaused = state.map;

        timers = new EnemyTimers();
        timers.capture();

        toast("enemy-pause.paused");
    }

    /**
     * 每帧保活，并在条件不再成立时自动解除。
     * 由 Trigger.afterGameUpdate 调用。
     */
    public void update(){
        if(!paused) return;

        GameState state = Vars.state;

        // 换地图、退出战役、游戏结束，或者期间已经放出去过一波（例如点了 HUD 的提前进攻），
        // 都自动解除，避免暂停状态残留到下一局。
        // 这种解除不还原读数：局面已经变了，让敌方节奏重新数才是对的。
        if(!usable(state) || state.gameOver || state.map != mapWhenPaused || state.wave != waveWhenPaused){
            release(false);
            return;
        }

        timers.hold();
    }

    /**
     * 暂停是否有意义：战役模式、单机，且这一局确实有敌方节奏在跑。
     *
     * 有敌方核心的关卡（attackMode）没有波次，但敌方基地 AI 和工厂照样在推进，
     * 所以这种情况也算可用。
     */
    private static boolean usable(GameState state){
        return state.isGame()
            && state.isCampaign()
            && !Vars.net.client()
            && (state.rules.waves || state.rules.attackMode);
    }

    private void release(boolean restore){
        if(timers != null && restore){
            timers.release();
        }
        timers = null;
        paused = false;
    }

    private static void toast(String bundleKey){
        if(Vars.ui == null || Vars.ui.hudfrag == null) return;
        if(Vars.state == null || !Vars.state.isGame()) return;
        Vars.ui.hudfrag.showToast(Core.bundle.get(bundleKey));
    }
}
