package enemypause;

import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.MapObjectives.MapObjective;
import mindustry.game.MapObjectives.TimerObjective;

import java.lang.reflect.Field;

/**
 * 冻结敌方侧的倒计时：一个实例代表一次暂停会话。
 *
 * 用法（都由 EnemyPause 驱动）：
 *   capture()  暂停瞬间记录各倒计时当前的读数，并立刻写回一次
 *   hold()     每帧（Trigger.afterGameUpdate）写回读数，抵消这一帧的时间推进
 *   release()  手动恢复时清掉记录，让倒计时接着暂停前的位置走
 *
 * 冻结的两项（括号里是 mindustry v160.1 的源码位置）：
 *
 *   1. 下一波进攻倒计时      state.wavetime
 *                            （Logic.update()：先按 Time.delta 递减，再判 <=0 则 runWave()）
 *
 *   2. 地图目标里的计时目标  MapObjectives.TimerObjective.countup
 *                            （MapObjectives.update() → TimerObjective.update()：
 *                              countup += Time.delta，够到 duration * objectiveTimerMultiplier 就算完成。
 *                              界面上显示的就是 duration*倍率 - countup，例如「敌人来袭：9:35」、
 *                              「敌方在 N 后扩大单位生产」，这些文案由关卡地图数据以
 *                              @objective.enemiesapproaching 这类键引用。）
 *
 * 两项都只是把「下一件事什么时候发生」按住，不动敌方的行为本身：
 * 已经在场的单位照常行动开火，工厂照常生产与激活，基地 AI 也照常扩建。
 */
public class EnemyTimers{

    /**
     * 冻结值至少保留这么多 tick（10 tick = 1/6 秒）。
     *
     * 两项倒计时都是「够到阈值就触发」，而触发判定发生在每帧的推进里、比我们的写回更早。
     * 所以若在只剩最后一两帧时按下暂停，原样冻结的话那次判定仍然会通过，暂停等于没生效：
     *   - 波次：runWave() 仍会把这一波放出来
     *   - 计时目标：目标直接判定完成并触发后果（比如敌人真的开始进攻）
     * 抬到 1/6 秒能堵住这个缺口，代价是恢复后最多差 0.17 秒，肉眼察觉不到。
     * （Time.delta 上限是 3，所以 10 tick 有 3 帧余量。）
     */
    private static final float MIN_HOLD_TICKS = 10f;

    /**
     * TimerObjective 里的倒计时累加器。
     *
     * 它是 protected、没有 getter/setter，而模组和它不同包，只能反射。
     * 上游改了这个字段名也不会把模组整个拖垮：取不到就只放弃这一项，
     * 波次倒计时照常工作。
     */
    private static final Field COUNTUP = findCountup();

    // ---- 1. 波次倒计时 ----
    private float frozenWaveTime;

    // ---- 2. 地图目标里的计时目标 ----
    /** 各计时目标在暂停瞬间的 countup 读数。 */
    private final ObjectMap<TimerObjective, Float> frozenTimers = new ObjectMap<>();

    /** 暂停瞬间记录各倒计时的当前读数。 */
    public void capture(){
        frozenWaveTime = Math.max(Vars.state.wavetime, MIN_HOLD_TICKS);
        // 立刻写一次：这样"倒计时已经归零、这一波正要发起"的那一帧也能被拦住
        Vars.state.wavetime = frozenWaveTime;

        frozenTimers.clear();
        rememberTimers();
        holdTimers();

        // 实测时可以看这行：timerObjectives 一直是 0，说明这张图没有计时目标
        Log.info("[enemy-pause] frozen: wave=@ tick, timerObjectives=@",
            (int)frozenWaveTime, frozenTimers.size);
    }

    /**
     * 每帧写回读数。由 Trigger.afterGameUpdate 调用——该时机在波次递减、runWave() 判断
     * 以及 objectives.update() 之后，正好把这一帧的推进抹掉。
     */
    public void hold(){
        Vars.state.wavetime = frozenWaveTime;
        rememberTimers();
        holdTimers();
    }

    /** 恢复时调用：清掉记录，倒计时从冻结时的位置继续。 */
    public void release(){
        // 不需要"还原"：countup / wavetime 一直停在冻结时的读数上，
        // 恢复后游戏自己会从那里接着加。
        frozenTimers.clear();
    }

    // ------------------------------------------------------------------
    // 2. 地图目标里的计时目标
    // ------------------------------------------------------------------

    /**
     * 记录还没见过的计时目标。
     *
     * 不能在 capture() 里一次记完了事：目标可以挂在别的目标下面
     * （MapObjective.parents），父目标没完成前它根本不参与更新，
     * 界面上的数字也是第一次 qualified 之后才有意义。
     */
    private void rememberTimers(){
        if(COUNTUP == null) return;

        for(MapObjective objective : Vars.state.rules.objectives.all){
            if(!(objective instanceof TimerObjective timer)) continue;
            if(frozenTimers.containsKey(timer)) continue;

            float limit = limit(timer);
            // 已经逼近完成的目标要把读数往回钳一点，否则下一帧那次判定就会通过
            float value = Math.min(readCountup(timer), Math.max(limit - MIN_HOLD_TICKS, 0f));
            frozenTimers.put(timer, value);
        }
    }

    private void holdTimers(){
        if(COUNTUP == null) return;

        for(ObjectMap.Entry<TimerObjective, Float> entry : frozenTimers){
            writeCountup(entry.key, entry.value);
        }
    }

    /** 完成阈值：duration 是 tick 数，再乘上关卡规则里的倍率。 */
    private static float limit(TimerObjective timer){
        return timer.duration * Vars.state.rules.objectiveTimerMultiplier;
    }

    private static Field findCountup(){
        try{
            Field field = TimerObjective.class.getDeclaredField("countup");
            field.setAccessible(true);
            return field;
        }catch(Throwable t){
            Log.warn("[enemy-pause] 取不到 TimerObjective.countup，地图目标里的倒计时不会被冻结");
            return null;
        }
    }

    private static float readCountup(TimerObjective timer){
        try{
            return COUNTUP.getFloat(timer);
        }catch(Throwable t){
            return 0f;
        }
    }

    private static void writeCountup(TimerObjective timer, float value){
        try{
            COUNTUP.setFloat(timer, value);
        }catch(Throwable ignored){
        }
    }
}
