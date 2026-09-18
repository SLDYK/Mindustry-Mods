import arc.util.Time;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.MapObjectives;
import mindustry.game.MapObjectives.TimerObjective;
import mindustry.net.Net;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * 联机同步（EnemyPause + EnemyPausePacket）的离线验证：
 *   1. 封包被注册、ID 稳定，且序列化往返后各字段原样还原
 *   2. 客户端收到"暂停"广播后，波次倒计时与目标计时都按主机端的读数停住，且不判定完成
 *   3. 客户端收到"继续"广播后恢复递减
 *   4. 主机端不会处理自己发出的广播
 *   5. 客户端按 Y / U 只弹提示，不改任何状态
 *
 * 用反射把 Vars.net 摆成需要的形态（active / server 是 Net 的私有字段，
 * Net.client() = !server && active，Net.server() = server && active）：
 *   单机 = (false, false)   主机端 = (true, true)   客户端 = (true, false)
 *
 * 编译运行：
 *   CP="Enemy Pause/libs/dependencies.jar;Enemy Pause/build/libs/EnemyPause.jar"
 *   javac -encoding UTF-8 -implicit:none -sourcepath tools/epverify -cp "$CP" -d tools/epverify/out tools/epverify/VerifyEnemyPause.java
 *   java -Dfile.encoding=UTF-8 -cp "tools/epverify/out;$CP" VerifyEnemyPause
 */
public class VerifyEnemyPause{
    static int failures = 0;
    static Field countup;

    public static void main(String[] args) throws Exception{
        countup = TimerObjective.class.getDeclaredField("countup");
        countup.setAccessible(true);

        // ---- 最小环境 ----
        Vars.state = new GameState();
        Vars.state.set(GameState.State.playing);
        Vars.state.wavetime = 420f;

        TimerObjective timer = new TimerObjective("@objective.enemiesapproaching", 1800f);
        Vars.state.rules.objectives = new MapObjectives();
        Vars.state.rules.objectives.add(timer);

        Vars.net = new Net(null);
        Time.delta = 1f;

        // ---- 1. 封包注册 ----
        // 走 EnemyPauseMod.init() 里那段真实逻辑：无头模式让它在注册完封包后立刻返回，
        // 不去碰按键绑定与 HUD（那些东西在离线环境里没有）。
        Vars.headless = true;
        new enemypause.EnemyPauseMod().init();
        Vars.headless = false;

        byte id = Net.getPacketClassId(enemypause.EnemyPausePacket.class);
        check("封包已注册（拿到有效 ID）", id != -1);
        check("按 ID 能取回同一个封包类", Net.newPacket(id).getClass() == enemypause.EnemyPausePacket.class);

        // 重复注册会让 ID 与对端错位，所以必须只能注册一次
        new enemypause.EnemyPauseMod().init();
        check("重复 init() 不会重复注册", Net.getPacketClassId(enemypause.EnemyPausePacket.class) == id);

        // ---- 2. 序列化往返 ----
        ByteBuffer buffer = ByteBuffer.allocate(64);
        new enemypause.EnemyPausePacket(true, 512.5f).write(new Writes(new ByteBufferOutput(buffer)));

        buffer.flip();
        enemypause.EnemyPausePacket in = new enemypause.EnemyPausePacket();
        in.read(new Reads(new ByteBufferInput(buffer)));
        check("往返后 paused 一致", in.paused);
        check("往返后 waveTime 一致", in.waveTime == 512.5f);

        // ---- 3. 客户端：收到"暂停"广播 ----
        setNet(true, false);
        setCountup(timer, 0f);
        Vars.state.wavetime = 420f;          // 客户端本地读数比主机端低一截

        enemypause.EnemyPause.instance.onRemote(true, 500f);   // 主机端广播的是 500
        check("客户端进入暂停状态", enemypause.EnemyPause.instance.isPaused());
        check("客户端波次读数被改成主机端的值", Vars.state.wavetime == 500f);

        boolean waveHeld = true, timerHeld = true, completed = false;
        for(int i = 1; i <= 1200; i++){
            Vars.state.wavetime -= 1f;             // 客户端本地照常递减（Logic.update）
            if(timer.update()) completed = true;   // 客户端的计时目标也照常推进
            enemypause.EnemyPause.instance.update();   // 模组每帧的保活

            if(Vars.state.wavetime != 500f) waveHeld = false;
            if(countup(timer) != 0f) timerHeld = false;
        }
        check("客户端波次倒计时按主机端读数停住", waveHeld);
        check("客户端目标计时停住（不再累加）", timerHeld);
        check("客户端目标计时不会判定完成", !completed);

        // ---- 4. 客户端：收到"继续"广播 ----
        enemypause.EnemyPause.instance.onRemote(false, 0f);
        check("客户端退出暂停状态", !enemypause.EnemyPause.instance.isPaused());

        boolean resumed = true;
        for(int i = 1; i <= 5; i++){
            Vars.state.wavetime -= 1f;
            enemypause.EnemyPause.instance.update();
            if(Vars.state.wavetime != 500f - i) resumed = false;
        }
        check("客户端恢复后立即正常递减", resumed);

        // ---- 5. 主机端不会处理自己发出的广播 ----
        setNet(true, true);
        enemypause.EnemyPause.instance.onRemote(true, 300f);
        check("主机端忽略自己广播的暂停", !enemypause.EnemyPause.instance.isPaused());
        check("主机端不会因广播改动波次读数", Vars.state.wavetime == 495f);

        // ---- 6. 客户端按键：只提示，不改任何状态 ----
        setNet(true, false);
        Vars.state.wavetime = 420f;
        enemypause.EnemyPause.instance.toggle();
        check("客户端按 Y 不进暂停状态", !enemypause.EnemyPause.instance.isPaused());
        check("客户端按 Y 不改波次读数", Vars.state.wavetime == 420f);

        enemypause.EnemyPause.instance.skip();
        check("客户端按 U 不完成任何目标", !timer.isCompleted());

        summary();
    }

    /** 把 Vars.net 摆成指定形态。 */
    static void setNet(boolean active, boolean server) throws Exception{
        Field fa = Net.class.getDeclaredField("active");
        Field fs = Net.class.getDeclaredField("server");
        fa.setAccessible(true);
        fs.setAccessible(true);
        fa.setBoolean(Vars.net, active);
        fs.setBoolean(Vars.net, server);
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
