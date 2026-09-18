import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.GameState.State;
import mindustry.game.MapObjectives;
import mindustry.game.MapObjectives.TimerObjective;

/**
 * EnemySkip 的离线验证：用真实游戏类构造最小环境，检查
 *   1. 只有「此刻正在跑」的计时目标会被跳过；挂在未完成父目标下的子目标不能被跳
 *   2. 非游戏状态（菜单）下什么也不做——尤其不能让 runWave() 在空世界上跑
 *   3. Vars.logic 为空时能安全退化，不抛异常
 *   4. 目标完成走的是 Call.completeObjective（单机下它就是本地 done()，
 *      联机下主机端还会把完成广播给客户端）——不能被改成直接 done()
 *
 * 编译运行：
 *   CP="Enemy Pause/libs/dependencies.jar;Enemy Pause/build/libs/EnemyPause.jar"
 *   javac -encoding UTF-8 -implicit:none -sourcepath tools/epverify -cp "$CP" -d tools/epverify/out tools/epverify/VerifyEnemySkip.java
 *   java -Dfile.encoding=UTF-8 -cp "tools/epverify/out;$CP" VerifyEnemySkip
 */
public class VerifyEnemySkip{
    static int failures = 0;

    public static void main(String[] args){
        // 最小环境：Call.completeObjective 内部要问 Vars.net.server()/active()，
        // 所以得给它一个“没连网”的 Net 实例——那正是单机的形态（server=false, active=false），
        // 于是它会在本地执行 done()，与实际单机行为一致。
        Vars.net = new mindustry.net.Net(null);

        // ---- 1. 菜单状态：不应跳任何东西 ----
        Vars.state = new GameState();
        check("菜单状态下 all() 返回 false", !enemypause.EnemySkip.all());

        // ---- 2. 游戏内：父目标在跑、子目标还没轮到 ----
        Vars.state = new GameState();
        Vars.state.set(State.playing);

        TimerObjective parent = new TimerObjective("@objective.enemiesapproaching", 1800f);
        TimerObjective child = new TimerObjective("@objective.enemyescalating", 1800f);
        parent.child(child);

        Vars.state.rules.objectives = new MapObjectives();
        Vars.state.rules.objectives.add(parent);

        check("父目标此刻在跑（qualified）", parent.qualified());
        check("子目标此刻不在跑（父目标未完成）", !child.qualified());

        boolean any = enemypause.EnemySkip.all();

        check("all() 报告跳过了内容", any);
        check("在跑的父目标被判定完成", parent.isCompleted());
        check("未轮到的子目标**没有**被一起跳掉", !child.isCompleted());

        // ---- 3. 父目标完成后，子目标这才开始跑 ----
        check("父目标完成后子目标变为在跑", child.qualified());

        // 再按一次 U：这次该轮到子目标
        enemypause.EnemySkip.all();
        check("再跳一次后子目标完成", child.isCompleted());

        // ---- 4. 都跳完了，再按应当无事可做 ----
        check("没有目标可跳时 all() 返回 false", !enemypause.EnemySkip.all());

        // ---- 5. 非计时目标不受影响 ----
        Vars.state.rules.objectives = new MapObjectives();
        var research = new MapObjectives.ResearchObjective(mindustry.content.Items.copper);
        Vars.state.rules.objectives.add(research);
        check("只有研究目标时 all() 返回 false（它不是计时目标）", !enemypause.EnemySkip.all());
        check("研究目标未被误标记完成", !research.isCompleted());

        summary();
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
