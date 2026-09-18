package enemypause;

import arc.struct.IntSeq;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.MapObjectives.MapObjective;
import mindustry.game.MapObjectives.TimerObjective;
import mindustry.gen.Call;

/**
 * 「跳过本轮倒计时」：把正在走的倒计时直接推到终点，让后果立刻发生。
 *
 * 和 EnemyTimers 正好相反：
 *   EnemyTimers  每帧把读数写回，让倒计时停住；
 *   EnemySkip    一次性把读数推过终点，让游戏自己判定触发。
 *
 * 两类倒计时：
 *
 *   1. 波次倒计时  用 Logic.skipWave()，也就是 HUD 上那个「提前进攻」按钮干的事：
 *                  runWave() 立刻出兵，并把 wavetime 重置成一整轮。
 *
 *   2. 目标计时    调用 Call.completeObjective(index)。它内部就是 done()，但**别直接调 done()**：
 *                  单机下它本地执行 done()，联机下主机端除了本地执行还会把
 *                  CompleteObjectiveCallPacket 发给所有客户端（已对 160.1 的字节码确认）；
 *                  直接调 done() 在联机下客户端什么也收不到，它的计时目标会一直跑下去，
 *                  界面上的数字越过上限后变成负数，而且目标永远不会从列表里消失。
 *                  done() 内部：加/减 objectiveFlags、标记完成、跑 completionLogicCode。
 */
public class EnemySkip{

    /** 跳过所有正在走的敌方倒计时。@return 是否跳过了至少一项 */
    public static boolean all(){
        // 菜单 / 编辑器里没有「本轮倒计时」；更重要的是不能让 runWave() 在空世界上跑
        if(Vars.state == null || !Vars.state.isGame()) return false;

        boolean any = skipWave();
        // 不能写成 any = skipTimers() || any：短路求值会让 skipTimers() 不去执行
        if(skipTimers()){
            any = true;
        }
        return any;
    }

    private static boolean skipWave(){
        if(Vars.logic == null || Vars.spawner == null) return false;

        // 没有波次、或这一局根本不发兵，就没有「本轮」可跳
        if(!Vars.state.rules.waves || !Vars.state.rules.waveSending) return false;

        // 游戏自己把倒计时按住了（比如场上还有敌人、又开了 waitEnemies），那不是能跳的东西
        if(Vars.logic.isWaitingWave()) return false;

        // 正在放兵的半途插队会让 spawner 的状态错乱
        if(Vars.spawner.isSpawning()) return false;

        Vars.logic.skipWave();
        return true;
    }

    private static boolean skipTimers(){
        Seq<MapObjective> all = Vars.state.rules.objectives == null ? null : Vars.state.rules.objectives.all;
        if(all == null) return false;

        // 先收集、再逐个完成。不能边遍历边调 done()：done() 会改父子依赖状态，
        // 于是"刚被父目标解锁的子目标"会在同一轮里变得 qualified 而被一起跳掉，
        // 但它其实才刚开始数，不该跳。
        IntSeq running = new IntSeq();
        for(int i = 0; i < all.size; i++){
            MapObjective objective = all.get(i);
            if(objective instanceof TimerObjective timer && timer.qualified()){
                running.add(i);
            }
        }

        for(int i = 0; i < running.size; i++){
            // 走官方远程：单机=本地执行，主机端=本地执行 + 广播给客户端
            Call.completeObjective(running.get(i));
        }
        return running.size > 0;
    }
}
