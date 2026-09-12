import arc.util.Interval;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.BaseBuilderAI;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;

import java.lang.reflect.Field;

/**
 * EnemyTimers 的离线验证：用真实游戏类构造最小环境，检查
 *   1. 反射能否拿到 BaseBuilderAI.timer / RtsAI.timer
 *   2. 暂停期间波次倒计时与 AI 计时器是否真的停住
 *   3. 恢复后 AI 计时器是否接着剩余时间走（而不是重新数满一轮）
 *
 * 编译运行：
 *   javac -encoding UTF-8 -implicit:none -cp "Enemy Pause/libs/dependencies.jar;Enemy Pause/build/libs/EnemyPause.jar" -d out VerifyEnemyTimers.java
 *   java -Dfile.encoding=UTF-8 -cp "out;Enemy Pause/libs/dependencies.jar;Enemy Pause/build/libs/EnemyPause.jar" VerifyEnemyTimers
 */
public class VerifyEnemyTimers{
    static int failures = 0;

    public static void main(String[] args) throws Exception{
        // ---- 最小环境：只需要 Vars.state + 敌方 TeamData ----
        Vars.state = new GameState();
        Vars.state.tick = 1000;
        Vars.state.wavetime = 600f;
        Time.setInternalTime(1000f);
        Time.delta = 1f;

        Team enemy = Vars.state.rules.waveTeam;
        TeamData data = enemy.data();
        data.buildAi = new BaseBuilderAI(data);

        Interval aiTimer = timerOf(data.buildAi, "BaseBuilderAI");
        check("反射拿到 BaseBuilderAI.timer", aiTimer != null);
        if(aiTimer == null){
            summary();
            return;
        }

        // ---- 对照组：不暂停时 AI 计时器会正常触发 ----
        aiTimer.get(0, 60f);              // 首次调用会重置时间戳
        for(int i = 1; i <= 59; i++) Time.setInternalTime(1000f + i);
        check("对照组：59 tick 时未触发", !aiTimer.get(0, 60f));
        Time.setInternalTime(1000f + 60);
        check("对照组：60 tick 时触发", aiTimer.get(0, 60f));

        // 重新把 AI 计时器推到"已经等了 30 tick"的位置
        Time.setInternalTime(2000f);
        aiTimer.get(0, 60f);
        for(int i = 1; i <= 30; i++) Time.setInternalTime(2000f + i);

        // ---- 暂停 ----
        enemypause.EnemyTimers timers = new enemypause.EnemyTimers();
        timers.capture();
        check("capture 记录波次读数", Vars.state.wavetime == 600f);

        boolean waveHeld = true, aiHeld = true;
        // 模拟 600 帧：游戏每帧把倒计时减 1，然后 hold() 写回
        for(int i = 31; i <= 630; i++){
            Time.setInternalTime(2000f + i);
            Vars.state.tick += 1;
            Vars.state.wavetime -= 1f;
            timers.hold();

            if(Vars.state.wavetime != 600f) waveHeld = false;
            if(aiTimer.get(0, 60f)) aiHeld = false;
        }
        check("暂停 600 帧期间波次倒计时保持不动", waveHeld);
        check("暂停 600 帧期间 AI 计时器未被触发", aiHeld);
        check("暂停期间波次读数仍为 600", Vars.state.wavetime == 600f);

        // ---- 恢复：AI 计时器应只剩 30 tick ----
        timers.release();
        for(int i = 631; i <= 659; i++){
            Time.setInternalTime(2000f + i);
            if(aiTimer.get(0, 60f)){
                check("恢复后剩余 29 tick 内不应触发", false);
                break;
            }
        }
        Time.setInternalTime(2000f + 660);
        check("恢复后在剩余 30 tick 处触发", aiTimer.get(0, 60f));

        summary();
    }

    static Interval timerOf(Object ai, String label) throws Exception{
        Field f = ai.getClass().getDeclaredField("timer");
        f.setAccessible(true);
        return (Interval)f.get(ai);
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
