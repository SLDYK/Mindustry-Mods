package enemypause;

import arc.struct.ObjectMap;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.BaseBuilderAI;
import mindustry.ai.RtsAI;
import mindustry.game.Rules.TeamRule;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.world.blocks.units.UnitBlock.UnitBuild;

import java.lang.reflect.Field;

/**
 * 冻结「敌方侧」的各类倒计时：一个实例代表一次暂停会话。
 *
 * 用法（都由 EnemyPause 驱动）：
 *   capture()  暂停瞬间记录各倒计时当前的读数
 *   hold()     每帧（Trigger.afterGameUpdate）写回读数，抵消这一帧的时间推进
 *   release()  手动恢复时把读数还原，让敌人的节奏接着暂停前走
 *
 * 覆盖的倒计时（括号里是 mindustry v160.1 的源码位置）：
 *
 *   1. 下一波进攻倒计时    state.wavetime
 *                          （Logic.update()：先按 Time.delta 递减，再判 <=0 则 runWave()）
 *
 *   2. 敌方 AI 计时器      BaseBuilderAI.timer（基地扩建、AI 核心单位、路径刷新）
 *                          RtsAI.timer（部队调度、AI 核心单位）
 *                          （Logic.update() 的 TeamData 循环里调用两者的 update()）
 *
 *   3. 敌方工厂激活倒计时  Team.activateUnitFactories() 判的是
 *                          state.tick >= 激活延迟，埃里基尔上默认是进图 2 小时后
 *
 *   4. 敌方工厂生产进度    UnitFactory / Reconstructor 的 UnitBuild.time 与 progress
 *
 * 都不涉及「已经在场上的敌方单位」——冻住那些等于把敌人本身定住，是另一个需求。
 */
public class EnemyTimers{

    /**
     * 波次冻结值至少保留这么多 tick（10 tick = 1/6 秒）。
     *
     * 因为若在倒计时只剩最后一两帧时按下暂停，原样冻结的话下一帧扣掉 Time.delta
     * 就归零了，runWave() 仍然会把这一波放出来，暂停等于没生效。
     * 抬到 1/6 秒能堵住这个缺口，代价是恢复后最多差 0.17 秒，肉眼察觉不到。
     */
    private static final float MIN_HOLD_TICKS = 10f;

    /**
     * BaseBuilderAI / RtsAI 里那个 Interval 字段的名字。
     *
     * 这两个字段是包级私有、没有 getter，只能反射拿。Interval 本身没问题——
     * arc.util.Interval.getTimes() 是公开的，拿到对象后不用再反射。
     */
    private static final String TIMER_FIELD = "timer";

    private static final Field BUILD_AI_TIMER = findTimerField(BaseBuilderAI.class);
    private static final Field RTS_AI_TIMER = findTimerField(RtsAI.class);

    // ---- 1. 波次倒计时 ----
    private float frozenWaveTime;

    // ---- 2. 敌方 AI 计时器 ----
    /**
     * 各 Interval 在暂停瞬间的「年龄」（即该槽位已经等了多久），恢复时用它接着数。
     *
     * 必须存年龄而不是 Interval 里的原始时间戳：Interval 存的是「上次重置时的 Time.time」，
     * 跟暂停开始的时刻差着一截，直接拿它去减 Time.time 会把时间戳算得极小，
     * 而 Interval.check() 的第一个分支正是「Time.time - times[id] >= delay」，
     * 结果就是一恢复就立刻触发。
     */
    private final ObjectMap<Interval, float[]> aiAges = new ObjectMap<>();

    /** 复用的小数组，避免每帧分配。 */
    private final Interval[] tmpIntervals = new Interval[2];

    // ---- 3. 敌方工厂激活倒计时 ----
    private double tickAtCapture;

    // ---- 4. 敌方工厂生产进度 ----
    /** 暂停瞬间各敌方工厂的 {time, progress}。 */
    private final ObjectMap<Building, float[]> production = new ObjectMap<>();

    /** 暂停瞬间记录各倒计时的当前读数。 */
    public void capture(){
        frozenWaveTime = Math.max(Vars.state.wavetime, MIN_HOLD_TICKS);
        // 立刻写一次：这样"倒计时已经归零、这一波正要发起"的那一帧也能被拦住
        Vars.state.wavetime = frozenWaveTime;

        aiAges.clear();
        rememberAiTimers();

        tickAtCapture = Vars.state.tick;

        production.clear();
        rememberProduction();

        // 敌方 AI 是游戏偷懒创建的（Logic 里 if(data.buildAi == null) ...），
        // 可能到此刻还没建起来，那时这里就是 0；键进游戏后按 Y 看到的数字
        // 正好用来判断反射有没有真的接上。
        Log.info("[enemy-pause] frozen: wave=@ tick, enemyAiTimers=@, enemyFactories=@",
            (int)frozenWaveTime, aiAges.size, production.size);
    }

    /**
     * 每帧写回读数。由 Trigger.afterGameUpdate 调用——该时机在倒计时递减、runWave()
     * 判断、以及敌方 AI 与建筑的这一帧更新之后，正好把这一帧的推进抹掉。
     */
    public void hold(){
        Vars.state.wavetime = frozenWaveTime;
        freezeAiTimers();
        holdActivation();
        freezeProduction();
    }

    /** 手动恢复时调用：把还来得及还原的读数还原回去。 */
    public void release(){
        // 波次和工厂激活不用还原：
        //   - wavetime 写回冻结值本身就意味着"从暂停处接着数"
        //   - 激活延迟已经被推后，本来就该留在推后后的时间点
        // AI 计时器必须还原，否则每次暂停都会让敌方 AI 重新数满一整轮。
        for(ObjectMap.Entry<Interval, float[]> entry : aiAges){
            float[] times = entry.key.getTimes();
            float[] ages = entry.value;
            for(int i = 0; i < times.length && i < ages.length; i++){
                times[i] = Time.time - ages[i];
            }
        }
        aiAges.clear();
        production.clear();
    }

    // ------------------------------------------------------------------
    // 2. 敌方 AI 计时器
    // ------------------------------------------------------------------

    private void rememberAiTimers(){
        for(Interval interval : currentAiTimers()){
            if(interval != null && !aiAges.containsKey(interval)){
                recordAges(interval);
            }
        }
    }

    /** 把 Interval 各槽位当前已经等了多久（年龄）记下来。 */
    private void recordAges(Interval interval){
        float[] times = interval.getTimes();
        float[] ages = new float[times.length];
        for(int i = 0; i < times.length; i++){
            ages[i] = Time.time - times[i];
        }
        aiAges.put(interval, ages);
    }

    private void freezeAiTimers(){
        for(Interval interval : currentAiTimers()){
            if(interval == null) continue;

            // 暂停期间游戏才懒创建出来的 AI（Logic 里是 if(data.buildAi == null) ...），
            // capture() 时还看不到，第一次扫到就以当前读数为基准
            if(!aiAges.containsKey(interval)){
                recordAges(interval);
            }

            // Interval.check() 判的是 Time.time - times[id] >= delay，
            // 每帧把时间戳归到"此刻"，年龄就恒为 0，永远够不到触发点。
            //
            // 注意不能写成 times[id] += Time.delta：帧率一抖动，时间戳就会被写到
            // Time.time 之后，而 Interval.check() 的另一个分支 Time.time < times[id]
            // 恰好把"时间戳在未来"当成"该触发了"，会反过来让 AI 乱触发。
            float[] times = interval.getTimes();
            for(int i = 0; i < times.length; i++){
                times[i] = Time.time;
            }
        }
    }

    /** 敌方阵营那两个 AI 的计时器，取不到的槽位为 null。 */
    private Interval[] currentAiTimers(){
        TeamData data = Vars.state.rules.waveTeam.data();
        tmpIntervals[0] = intervalOf(data.buildAi, BUILD_AI_TIMER);
        tmpIntervals[1] = intervalOf(data.rtsAi, RTS_AI_TIMER);
        return tmpIntervals;
    }

    private static Field findTimerField(Class<?> type){
        try{
            Field field = type.getDeclaredField(TIMER_FIELD);
            field.setAccessible(true);
            return field;
        }catch(Throwable t){
            // 上游改了字段名也不该把模组整个拖垮，只放弃这一项就好
            Log.warn("[enemy-pause] 取不到 @ 的 @ 字段，该项敌方倒计时不会被冻结",
                type.getSimpleName(), TIMER_FIELD);
            return null;
        }
    }

    private static Interval intervalOf(Object ai, Field field){
        if(ai == null || field == null) return null;
        try{
            return (Interval)field.get(ai);
        }catch(Throwable t){
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 3. 敌方工厂激活倒计时
    // ------------------------------------------------------------------

    private void holdActivation(){
        Team enemy = Vars.state.rules.waveTeam;

        // 已经激活的不能再推：activateUnitFactories() 一旦为 true 就说明敌方工厂在跑了，
        // 此时继续加延迟反而会把它们重新关掉
        if(enemy.activateUnitFactories()) return;

        // state.tick 是全局的（玩家侧也用它），没法只冻敌人，只好反着把激活时间往后推相同的量
        double now = Vars.state.tick;
        TeamRule rule = enemy.rules();
        rule.unitFactoryActivationDelay += (float)(now - tickAtCapture);
        tickAtCapture = now;
    }

    // ------------------------------------------------------------------
    // 4. 敌方工厂生产进度
    // ------------------------------------------------------------------

    private void rememberProduction(){
        for(Building build : Vars.state.rules.waveTeam.data().buildings){
            if(build instanceof UnitBuild unit && !production.containsKey(build)){
                production.put(build, new float[]{unit.time, unit.progress});
            }
        }
    }

    private void freezeProduction(){
        for(Building build : Vars.state.rules.waveTeam.data().buildings){
            if(!(build instanceof UnitBuild unit)) continue;

            float[] snapshot = production.get(build);
            if(snapshot == null){
                // 暂停期间才建起来的工厂（或第一次扫到），以当前进度为基准
                production.put(build, new float[]{unit.time, unit.progress});
            }else{
                // 每帧写回意味着进度最多只多走一帧：若工厂刚好卡在最后一帧完工，
                // 那一台单位仍会漏出来。要堵住得去读 UnitFactory 的 plan.time 留余量，
                // 收益（最多漏一个单位）不值这个耦合，就先这样。
                unit.time = snapshot[0];
                unit.progress = snapshot[1];
            }
        }
    }
}
