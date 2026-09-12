package enemypause;

import arc.struct.ObjectMap;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.BaseBuilderAI;
import mindustry.ai.RtsAI;
import mindustry.game.Teams.TeamData;

import java.lang.reflect.Field;

/**
 * 冻结敌方侧的倒计时：一个实例代表一次暂停会话。
 *
 * 用法（都由 EnemyPause 驱动）：
 *   capture()  暂停瞬间记录各倒计时当前的读数
 *   hold()     每帧（Trigger.afterGameUpdate）写回读数，抵消这一帧的时间推进
 *   release()  手动恢复时把读数还原，让敌人的节奏接着暂停前走
 *
 * 冻结的两项（括号里是 mindustry v160.1 的源码位置）：
 *
 *   1. 下一波进攻倒计时    state.wavetime——界面上那个「敌人来袭 x:xx」
 *                          （Logic.update()：先按 Time.delta 递减，再判 <=0 则 runWave()）
 *
 *   2. 敌方 AI 计时器      BaseBuilderAI.timer（基地扩建、AI 核心单位、路径刷新）
 *                          RtsAI.timer（部队调度、AI 核心单位）
 *                          （Logic.update() 的 TeamData 循环里调用两者的 update()）
 *
 * 两项都只是把「下一件事什么时候发生」按住，不是把敌人本身按住：
 *
 *   - 已经在场的敌方单位照常行动、开火，只是 AI 不会启动新的扩建批次 / 新的部队调度
 *   - 敌方工厂照常生产、照常激活，玩家侧的行为也不受影响
 *
 * 早期版本还冻过「敌方工厂激活倒计时」和「工厂生产进度」，按需求已移除：
 * 那两项会表现为敌人的生产行为被卡住，而需求只要求暂停倒计时。
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

    /** 暂停瞬间记录各倒计时的当前读数。 */
    public void capture(){
        frozenWaveTime = Math.max(Vars.state.wavetime, MIN_HOLD_TICKS);
        // 立刻写一次：这样"倒计时已经归零、这一波正要发起"的那一帧也能被拦住
        Vars.state.wavetime = frozenWaveTime;

        aiAges.clear();
        rememberAiTimers();

        // 敌方 AI 是游戏偷懒创建的（Logic 里 if(data.buildAi == null) ...），
        // 可能到此刻还没建起来，那时这里就是 0。
        // 实测时可以看这行：一直为 0 就说明反射没接上。
        Log.info("[enemy-pause] frozen: wave=@ tick, enemyAiTimers=@",
            (int)frozenWaveTime, aiAges.size);
    }

    /**
     * 每帧写回读数。由 Trigger.afterGameUpdate 调用——该时机在倒计时递减、runWave()
     * 判断、以及敌方 AI 这一帧的更新之后，正好把这一帧的推进抹掉。
     */
    public void hold(){
        Vars.state.wavetime = frozenWaveTime;
        freezeAiTimers();
    }

    /** 手动恢复时调用：把还来得及还原的读数还原回去。 */
    public void release(){
        // 波次倒计时不用还原：写回冻结值本身就意味着"从暂停处接着数"。
        // 但 AI 计时器必须还原，否则每次暂停都会让敌方 AI 重新数满一整轮。
        for(ObjectMap.Entry<Interval, float[]> entry : aiAges){
            float[] times = entry.key.getTimes();
            float[] ages = entry.value;
            for(int i = 0; i < times.length && i < ages.length; i++){
                times[i] = Time.time - ages[i];
            }
        }
        aiAges.clear();
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
}
