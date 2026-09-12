package enemypause;

import arc.Core;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.maps.Map;

/**
 * 冻结"下一波敌方进攻"的倒计时。只在战役模式生效。
 *
 * 游戏在 Logic.update() 里这样推进倒计时（v159.7）：
 *
 *     if(rules.waves && rules.waveTimer && !state.gameOver && !isWaitingWave())
 *         state.wavetime = Math.max(state.wavetime - Time.delta, 0);
 *     if(!net.client() && state.wavetime <= 0 && rules.waves)
 *         runWave();
 *
 * state.wavetime 的单位是 tick（60 tick = 1 秒）。所谓"暂停倒计时"，就是在上面两步
 * 之后把 state.wavetime 写回冻结值：倒计时既不会前进，也不会满足 <= 0 而去发起进攻。
 */
public class EnemyWavePause{

    /**
     * 冻结值至少保留这么多 tick（10 tick = 1/6 秒）。
     *
     * 因为若在倒计时只剩最后一两帧时按下暂停，原样冻结的话下一帧扣掉 Time.delta
     * 就归零了，上面那句 runWave() 仍然会把这一波放出来，暂停等于没生效。
     * 抬到 1/6 秒能堵住这个缺口，代价是恢复后最多差 0.17 秒，肉眼察觉不到。
     */
    private static final float MIN_HOLD_TICKS = 10f;

    private boolean paused;

    /** 暂停瞬间的倒计时读数，每帧写回这个值。 */
    private float frozenTime;

    /** 暂停瞬间的波次序号与地图，用来判断暂停期间局面是否已经变了。 */
    private int waveWhenPaused;
    private Map mapWhenPaused;

    public boolean isPaused(){
        return paused;
    }

    /** 暂停 / 继续。由按键轮询调用。 */
    public void toggle(){
        if(paused){
            paused = false;
            toast("enemy-pause.resumed");
        }else{
            pause();
        }
    }

    private void pause(){
        GameState state = Vars.state;

        // 需求是只在战役模式生效。战役地图的判据就是 rules.sector != null
        if(!state.isGame() || !state.isCampaign() || !state.rules.waves){
            toast("enemy-pause.unavailable");
            return;
        }

        paused = true;
        waveWhenPaused = state.wave;
        mapWhenPaused = state.map;
        frozenTime = Math.max(state.wavetime, MIN_HOLD_TICKS);

        // 立刻写一次：这样"倒计时已经归零、这一波正要发起"的那一帧也能被拦住
        state.wavetime = frozenTime;

        toast("enemy-pause.paused");
    }

    /**
     * 每帧保活，并在条件不再成立时自动解除。
     * 由 Trigger.afterGameUpdate 调用——该时机在倒计时递减与 runWave() 判断之后。
     */
    public void update(){
        if(!paused) return;

        GameState state = Vars.state;

        // 换地图、退出战役、游戏结束，或者期间已经放出去过一波（例如点了 HUD 的提前进攻），
        // 都自动解除，避免暂停状态残留到下一局
        if(!state.isGame() || !state.isCampaign() || !state.rules.waves
            || state.gameOver || state.map != mapWhenPaused || state.wave != waveWhenPaused){
            paused = false;
            return;
        }

        state.wavetime = frozenTime;
    }

    private static void toast(String bundleKey){
        if(Vars.ui == null || Vars.ui.hudfrag == null) return;
        if(Vars.state == null || !Vars.state.isGame()) return;
        Vars.ui.hudfrag.showToast(Core.bundle.get(bundleKey));
    }
}
