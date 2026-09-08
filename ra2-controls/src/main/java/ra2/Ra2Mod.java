package ra2;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.input.KeyBind;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.input.DesktopInput;
import ra2.input.Ra2Input;

/**
 * RA2 Controls 模组主类。
 *
 * <p>职责：
 * <ol>
 *   <li>注册模组自定义键位（RA2 分类，全部可在 设置→控制 中改绑/重置）</li>
 *   <li>在客户端加载完成后，用 {@link Ra2Input} 替换原版桌面输入处理器</li>
 *   <li>原版指挥专属键解除绑定（unset）：select_all_units/select_all_unit_factories/
 *       select_all_unit_transport/command_queue —— 指挥操作 100% 由模组键位定义，
 *       与原版按键零冲突（用户定案 2026-09-09，PLAN §4.1 矩阵）</li>
 * </ol>
 *
 * <p>安装时序关键点（PLAN §4.2）：原版在 ClientLoadEvent 中调用 input.add()，
 * 直接在同一事件里 setInput 会抢在 add() 之前，必须 Core.app.post 延后一拍。
 */
public class Ra2Mod extends mindustry.mod.Mod {

    /** 模组键位分类名（bundle: category.ra2.name；设置→控制 里显示为分组标题）。 */
    public static final String keybindCategory = "ra2";

    // 模组自定义键位（静态字段便于 Ra2Input 直接引用）
    public static KeyBind bindStop;
    public static KeyBind bindSpread;
    public static KeyBind bindPanCore;

    public Ra2Mod() {
        // 构造函数里不做任何依赖游戏状态的事；全部推迟到 init
    }

    @Override
    public void init() {
        // 移动端/无头端不装（触屏输入模型不同，见 PLAN §1.2）
        if (Vars.mobile || Vars.headless) {
            return;
        }

        registerKeybinds();

        // 必须延后一拍：原版 input.add() 在 ClientLoadEvent 中执行（F1/F2）。
        // 两路兜底：若模组 init 晚于 ClientLoadEvent（如加载中启用模组），
        // 直接 post 安装；否则挂事件等 ClientLoad。
        if (Vars.state != null && Vars.state.isGame()) {
            Core.app.post(Ra2Mod::install);
        } else {
            Events.on(EventType.ClientLoadEvent.class, e -> Core.app.post(Ra2Mod::install));
        }
    }

    /**
     * 注册模组键位到 设置→控制 列表（RA2 分类）。
     * 默认值取自 RA2-CONTROLS §5：F 停止、X 散开、Home 回核心。
     * 玩家改绑/重置由引擎的 KeybindDialog 全权处理（持久化到 settings）。
     *
     * <p>注意：指挥模式切换键本身是原版 commandMode 键位（默认左 Ctrl），
     * 玩家已可在设置→控制 中自行改绑（如 Tab）；模组不强制修改。
     */
    private void registerKeybinds(){
        try {
            bindStop = KeyBind.add("ra2-stop", KeyCode.f, keybindCategory);
            bindSpread = KeyBind.add("ra2-spread", KeyCode.x, keybindCategory);
            bindPanCore = KeyBind.add("ra2-pan-core", KeyCode.home, keybindCategory);
            Log.info("[ra2-controls] keybinds registered (category: @).", keybindCategory);
        } catch(Throwable t){
            Log.err("[ra2-controls] keybind registration failed", t);
        }
    }

    /** 执行输入处理器替换；已是 Ra2Input 则跳过（防重入）。 */
    static void install() {
        try {
            if (Vars.control == null || Vars.control.input == null) {
                Log.err("[ra2-controls] control.input not available, abort.");
                return;
            }
            if (Vars.control.input instanceof Ra2Input) {
                Log.info("[ra2-controls] already installed, skip.");
                return;
            }
            if (!(Vars.control.input instanceof DesktopInput)) {
                Log.warn("[ra2-controls] current input is @ (not DesktopInput), replacing anyway.",
                    Vars.control.input.getClass().getName());
            }

            Vars.control.setInput(new Ra2Input());

            // 模组生效期间强制指挥模式为"切换"进入（RA2 语义）；原版"按住"设置由模组托管
            if(Core.settings.getBool("commandmodehold", false)){
                Core.settings.put("commandmodehold", false);
                Log.info("[ra2-controls] forced commandmodehold=false (toggle, RA2 style).");
            }

            migrateKeybinds();

            Log.info("[ra2-controls] RA2 input installed. Left=select+order, Right=clear. (v0.1.0)");
        } catch (Throwable t) {
            Log.err("[ra2-controls] install failed", t);
        }
    }

    /**
     * 原版指挥专属键解除绑定（用户定案：指挥操作 100% 由模组键位定义）。
     *
     * <p>不是代码层屏蔽——原版 update()/pollInputPlayer() 里的 keyTap(Binding.xxx)
     * 分支原样保留，但这些绑定被 unset 后 keyTap 永远为 false，等效于原版指挥
     * 快捷键全部失效；模组在 设置→控制→RA2 分组重新定义全部指挥操作。
     *
     * <p>解除清单（v8/159.7 反汇编实测默认键，PLAN §4.1 矩阵 R7/R9）：
     * <ul>
     *   <li>select_all_units（G，全选指挥单位）→ G 让位模组攻击移动</li>
     *   <li>select_all_unit_factories（H，全选工厂）→ 模组停止键在 F，原 H 语义不需要</li>
     *   <li>select_all_unit_transport（未设默认键）→ 同上，全选运输机不需要</li>
     *   <li>commandQueue（鼠标中键，追加队列指令）→ 模组队列语义 = Shift+左键</li>
     * </ul>
     * 保留不解绑：commandMode（指挥模式开关）、createControlGroup（编组 Ctrl+数字）、
     * blockSelect01..10（数字选队）、selectAcrossScreen（Alt 全屏选）——语义与 RA2 一致。
     *
     * <p>每次安装时幂等执行（unset 对已 unset 绑定无副作用）；玩家想要回某个
     * 原版功能，在 设置→控制 给对应原版条目重新设键即可（改绑由引擎持久化）。
     */
    private static void migrateKeybinds(){
        try {
            String[] toUnset = {
                "select_all_units",
                "select_all_unit_factories",
                "select_all_unit_transport",
                "command_queue",
            };
            int n = 0;
            for(String name : toUnset){
                KeyBind bind = KeyBind.all.find(b -> name.equals(b.name));
                if(bind != null && !bind.isUnset()){
                    bind.unset();
                    bind.save();
                    n++;
                    Log.info("[ra2-controls] original command keybind unset: @", name);
                }
            }
            Log.info("[ra2-controls] original command keybinds cleared: @", n);
        } catch(Throwable t){
            Log.err("[ra2-controls] keybind migration failed", t);
        }
    }
}
