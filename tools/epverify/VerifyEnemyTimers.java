import arc.util.Time;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.MapObjectives;
import mindustry.game.MapObjectives.TimerObjective;
import java.lang.reflect.Field;

/**
 * EnemyTimers 的离线验证：用真实游戏类构造最小环境，检查
 *   1. 地图目标里的计时目标（TimerObjective）在暂停期间是否真的停住、且不会判定完成
 *   2. 波次倒计时是否停住
 *   3. 恢复后计时目标是否接着剩余时间走，而不是重新数满一轮
 *
 * 编译运行：
 *   CP="Enemy Pause/libs/dependencies.jar;Enemy Pause/build/libs/EnemyPause.jar"
 *   javac -encoding UTF-8 -implicit:none -sourcepath tools/epverify -cp "$CP" -d tools/epverify/out tools/epverify/VerifyEnemyTimers.java
 *   java -Dfile.encoding=UTF-8 -cp "tools/epverify/out;$CP" VerifyEnemyTimers
 */
public class VerifyEnemyTimers{
    static int failures = 0;
    static Field countup;

    public static void main(String[] args) throws Exception{
        countup = TimerObjective.class.getDeclaredField("countup");
        countup.setAccessible(true);

        // ---- 最小环境：只需要 Vars.state + 一条计时目标 ----
        Vars.state = new GameState();
        Vars.state.tick = 1000;
        Vars.state.wavetime = 600f;

        // duration 单位是 tick（界面上乘 objectiveTimerMultiplier 后按秒显示）
        TimerObjective timer = new TimerObjective("@objective.enemiesapproaching", 1800f);
        Vars.state.rules.objectives = new MapObjectives();
        Vars.state.rules.objectives.add(timer);

        Time.setInternalTime(1000f);
        Time.delta = 1f;

        enemypause.EnemyTimers timers = new enemypause.EnemyTimers();

        // ---- 对照组：不暂停时计时目标会推进 ----
        for(int i = 1; i <= 30; i++){
            Time.setInternalTime(1000f + i);
            timer.update();
        }
        check("对照组：30 帧后 countup 推进到 30", countup(timer) == 30f);

        // 回到"已推进 60 tick"的位置
        setCountup(timer, 60f);

        // ---- 暂停 ----
        Time.setInternalTime(2000f);
        timers.capture();
        check("capture 记录波次读数", Vars.state.wavetime == 600f);
        check("capture 保留计时目标读数", countup(timer) == 60f);

        boolean waveHeld = true, timerHeld = true, neverCompleted = true;
        // 模拟 3000 帧：游戏每帧把两个倒计时都推进，然后 hold() 写回
        for(int i = 1; i <= 3000; i++){
            Time.setInternalTime(2000f + i);
            Vars.state.tick += 1;
            Vars.state.wavetime -= 1f;
            if(timer.update()) neverCompleted = false;
            timers.hold();

            if(Vars.state.wavetime != 600f) waveHeld = false;
            if(countup(timer) != 60f) timerHeld = false;
        }
        check("暂停 3000 帧期间波次倒计时保持不动", waveHeld);
        check("暂停 3000 帧期间计时目标保持不动", timerHeld);
        check("暂停期间计时目标从未判定完成", neverCompleted);

        // ---- 恢复后应接着剩余时间走 ----
        timers.release();
        setCountup(timer, 60f);
        Time.setInternalTime(6000f);
        timer.update();     // 已经过 1 帧
        check("恢复后计数从冻结处继续（61）", countup(timer) == 61f);

        // ---- 逼近完成的边界：不能在暂停瞬间漏出去 ----
        TimerObjective nearly = new TimerObjective("@objective.enemyescalating", 1800f);
        Vars.state.rules.objectives = new MapObjectives();
        Vars.state.rules.objectives.add(nearly);
        setCountup(nearly, 1799.5f);

        enemypause.EnemyTimers timers2 = new enemypause.EnemyTimers();
        Time.setInternalTime(7000f);
        timers2.capture();

        boolean leaked = false;
        for(int i = 1; i <= 600; i++){
            Time.setInternalTime(7000f + i);
            if(nearly.update()) leaked = true;
            timers2.hold();
        }
        check("只剩 0.5 tick 时暂停：不会漏出完成判定", !leaked);
        check("边界情况下读数被钳到阈值前 10 tick", countup(nearly) == 1790f);

        summary();
    }

    static float countup(TimerObjective timer) throws Exception{
        return countup.getFloat(timer);
    }

    static void setCountup(TimerObjective timer, float value) throws Exception{
        countup.setFloat(timer, value);
    }

    static void check(String name, boolean ok){
        if(!ok) failures++;
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + name);
    }

    static void summary(){
        System.out.println(failures == 0 ? "全部通过" : (failures + " 项失败"));
        System.exit(failures == 0 ? 0 : 1);
    }
}
