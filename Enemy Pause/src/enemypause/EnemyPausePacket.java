package enemypause;

import arc.Core;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.net.Packet;

/**
 * 主机端 → 客户端的暂停状态广播（Y 键）。
 *
 * 为什么非得自己发封包：倒计时的推进与目标完成都是服务端权威的，
 * 客户端既看不到"主机端按了 Y"，也推不出该把数字停在哪一帧。
 * 这个封包把两件事告诉所有客户端：
 *   paused   —— 现在是不是暂停状态（客户端据此弹提示、并按/放自己的倒计时）
 *   waveTime —— 主机端冻结住的波次读数，客户端照抄，两边显示才一致
 * （地图目标的 countup 不参与同步，客户端只能就地取值，见 EnemyTimers.remote）
 *
 * 安全性：Mindustry 在连接时会比对双方模组列表（NetServer.handleConnect，
 * 不一致直接踢），所以联机里两端一定都装着本模组、也都注册了这个封包，ID 一致。
 *
 * 注册由 EnemyPauseMod.init() 负责，只能在没注册过时注册一次——封包 ID 是按注册顺序
 * 递增分配的（Net.registerPacket），重复注册会让 ID 与对端错位。
 */
public class EnemyPausePacket extends Packet{

    public boolean paused;
    /** 暂停时主机端冻结的波次读数；恢复时无意义。 */
    public float waveTime;

    public EnemyPausePacket(boolean paused, float waveTime){
        this.paused = paused;
        this.waveTime = waveTime;
    }

    /** Net.newPacket 反射构造用。 */
    public EnemyPausePacket(){
    }

    @Override
    public void write(Writes write){
        write.bool(paused);
        write.f(waveTime);
    }

    @Override
    public void read(Reads read){
        paused = read.bool();
        waveTime = read.f();
    }

    @Override
    public void handleClient(){
        // handleClient 跑在网络线程上，而这里要碰游戏状态（wavetime / countup），
        // 所以丢回主线程执行
        boolean remotePaused = paused;
        float remoteWaveTime = waveTime;
        Core.app.post(() -> EnemyPause.instance.onRemote(remotePaused, remoteWaveTime));
    }
}
