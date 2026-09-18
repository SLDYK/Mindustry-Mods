package enemypause;

import arc.Core;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.maps.Map;

/**
 * 暂停状态机：管 Y 键的开关、U 键的跳过、生效范围判定、"局面变了就自动解除"，
 * 以及联机时主机端与客户端的同步。
 *
 * 真正冻住哪些倒计时见 EnemyTimers，跳过逻辑见 EnemySkip。
 *
 * 这里冻的是倒计时本身（波次 + 地图目标里的计时目标），不是敌人的行为：
 * 已经在场的敌方单位照常行动开火，工厂照常生产，基地 AI 也照常扩建。
 *
 * 联机里的分工：
 *   主机端 / 单机   Y、U 真正生效；状态一变就广播给所有客户端（EnemyPausePacket）
 *   客户端          Y、U 只弹"仅主机端可控制"；收到广播后弹与主机端相同的提示，
 *                   并用主机端给的读数把自己的倒计时也按住（见 onRemote）
 */
public class EnemyPause{

    /** 全局唯一实例：按键轮询（EnemyPauseMod）与网络封包（EnemyPausePacket）都走它。 */
    public static final EnemyPause instance = new EnemyPause();

    /** 主机端 / 单机的暂停会话是否开着。 */
    private boolean paused;

    /** 客户端镜像：主机端广播说暂停了。 */
    private boolean remotePaused;

    /** 本次暂停会话的冻结读数；解除后置空。主机端与客户端各持有一份。 */
    private EnemyTimers timers;

    /** 暂停瞬间的波次序号与地图，用来判断暂停期间局面是否已经变了（仅主机端用）。 */
    private int waveWhenPaused;
    private Map mapWhenPaused;

    /** 任意一端处于暂停中。 */
    public boolean isPaused(){
        return paused || remotePaused;
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
            // 联机里当客户端时不能说"仅战役模式可用"——那是另一回事
            toast(hostBlocked(state) ? "enemy-pause.hostonly" : "enemy-pause.unavailable");
            return;
        }

        paused = true;
        waveWhenPaused = state.wave;
        mapWhenPaused = state.map;

        timers = new EnemyTimers();
        // 客户端要停在同一个数字上，所以连冻结读数一起广播过去
        broadcast(true, timers.waveTime());

        toast("enemy-pause.paused");
    }

    /**
     * 跳过当前正在走的倒计时：直接推到终点，让后果立刻发生（波次立刻出兵、目标计时立刻判定完成）。
     * 由 U 键调用——暂停与否都能用。
     */
    public void skip(){
        // 联机时只有主机端能控制：客户端自己动手只会造成不同步——
        // skipWave() 会在本地凭空刷出一批敌人并自行 state.wave++，
        // 目标完成也传不到别人那儿，随后都被服务端的状态覆盖。
        if(hostBlocked(Vars.state)){
            toast("enemy-pause.hostonly");
            return;
        }

        // 需求：跳过之后不再保持暂停，按「自动解除」处理。
        // 不还原读数——局面已经变了，让敌方节奏重新数才是对的。
        // release() 内部会把"解除"广播出去，客户端跟着放开自己的倒计时。
        if(paused){
            release(false);
        }

        boolean any = EnemySkip.all();
        toast(any ? "enemy-pause.skipped" : "enemy-pause.skip.none");
    }

    /**
     * 每帧保活，并在条件不再成立时自动解除。
     * 由 Trigger.afterGameUpdate 调用。
     */
    public void update(){
        // ---- 客户端镜像：只负责按住自己在跑的那两个倒计时 ----
        // 能不能暂停是主机端判定的事（广播只会在主机端真的暂停后才发过来），
        // 所以这里**不能**做 usable() 判断：它的 !net.client() 会让客户端永远"不可用"，
        // 结果就是主机端停了、客户端照跑，计时目标还会一路数成负数。
        if(remotePaused){
            GameState remoteState = Vars.state;
            if(remoteState == null || !remoteState.isGame()){
                // 退出到菜单 / 换局：静默解除，别在非游戏状态上写 wavetime
                releaseRemote();
            }else if(timers != null){
                timers.hold();
            }
            return;
        }

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

    // ------------------------------------------------------------------
    // 联机同步
    // ------------------------------------------------------------------

    /**
     * 客户端收到主机端的广播。由 EnemyPausePacket 回到主线程后调用。
     *
     * 客户端不自己决定任何事，只做两件：弹与主机端相同的提示，以及用 EnemyTimers
     * 把倒计时按在主机端给的读数上。后者是必须的——客户端的 wavetime 只靠状态快照
     * 纠正（间隔可到数秒，期间会一直自己递减），目标计时的 countup 更是完全不参与同步，
     * 不受干预时会越过上限，让界面上那句"敌人来袭：9:35"一路数成负数。
     */
    public void onRemote(boolean remotePaused, float waveTime){
        // 主机端不会收到自己的广播（Net.send 只发给真实连接），这里再兜一层
        if(!Vars.net.client()) return;

        if(remotePaused){
            if(this.remotePaused) return;

            this.remotePaused = true;
            timers = EnemyTimers.remote(waveTime);
            toast("enemy-pause.paused");
        }else{
            if(!this.remotePaused) return;

            releaseRemote();
            toast("enemy-pause.resumed");
        }
    }

    /** 把暂停状态告诉所有客户端。只有主机端（开房 / 专用服）发得出去。 */
    private static void broadcast(boolean paused, float waveTime){
        if(!Vars.net.server()) return;
        Vars.net.send(new EnemyPausePacket(paused, waveTime), true);
    }

    /**
     * 暂停是否有意义：战役模式、非客户端（单机或自己开房），且这一局确实有敌方节奏在跑。
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

    /**
     * 是不是"联机里的客户端"——那种情况下两个键都不该起作用。
     *
     * 判定用 Net.client()，它的定义是 !server && active：
     *   - 单机：active=false → 不是客户端
     *   - 自己开房（主机端）：server=true → 也不是客户端，功能照常
     *   - 连进别人的房间：server=false 且 active=true → 是客户端，挡住
     * 游戏内菜单/编辑器里没有"本轮倒计时"，走的是 unavailable 那条提示。
     */
    private static boolean hostBlocked(GameState state){
        return state != null && state.isGame() && Vars.net.client();
    }

    /** 解除主机端（或单机）的暂停会话，并通知客户端同步放开。 */
    private void release(boolean restore){
        if(!paused) return;

        if(timers != null && restore){
            timers.release();
        }
        timers = null;
        paused = false;

        broadcast(false, 0f);
    }

    /** 客户端：主机端说不用按了。 */
    private void releaseRemote(){
        remotePaused = false;
        if(timers != null){
            timers.release();
            timers = null;
        }
    }

    private static void toast(String bundleKey){
        if(Vars.ui == null || Vars.ui.hudfrag == null) return;
        if(Vars.state == null || !Vars.state.isGame()) return;
        Vars.ui.hudfrag.showToast(Core.bundle.get(bundleKey));
    }
}
