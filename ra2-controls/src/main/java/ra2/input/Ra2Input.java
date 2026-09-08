package ra2.input;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.util.Log;
import mindustry.ai.UnitStance;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.input.DesktopInput;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import ra2.Ra2Mod;

import static mindustry.Vars.*;

/**
 * RA2 风格指挥输入处理器（Tier A，纯客户端输入层）。
 *
 * <p>覆写原则（PLAN §4.3）：只动指挥模式相关的公共钩子，建造/采矿/蓝图全走父类。
 *
 * <p>已适配操作（RA2-CONTROLS §2 / §3 / §5）：
 * <ul>
 *   <li>左键单击己方单位/可指挥建筑 → <b>替换</b>选择（原版是 toggle 语义，F8）</li>
 *   <li>Shift + 左键单击 → 加入/移出选择（toggle，保留原版）</li>
 *   <li>双击单位 → 选中屏幕内同类型（沿用原版 {@code selectTypedUnits}）</li>
 *   <li>有选择时左键点空地/敌人 → 下令 移动/攻击（原版右键下令翻转至左键）</li>
 *   <li>Shift + 左键点空地 → 队列下令（原版 commandTap queue=true）</li>
 *   <li>右键单击 → <b>清空全部选择</b>；右键拖拽 → 无操作（原版右键下令被完整拦截）</li>
 *   <li>左键拖拽框选 → 替换选择；Shift 框选 → 并入（覆写 {@code multiUnitSelect}，F7）</li>
 *   <li>停止 → 清空全部指令（模组键位默认 F，设置→控制 可改绑；走 UnitStance.stop）</li>
 *   <li>散开 → 所选单位向自身周围就近散点下令（模组键位默认 X）</li>
 *   <li>回核心 → 镜头一次性跳回最近核心（模组键位默认 Home；走原版 panCamera）</li>
 * </ul>
 *
 * <p>镜头机制零改动（2026-09-04 已定）：update() 仅做快捷键轮询 + 托管指挥模式
 * 进入方式（强制切换式），不触碰任何镜头/每帧输入逻辑。
 */
public class Ra2Input extends DesktopInput {

    /** 散开指令的临时向量（避免每单位分配）。 */
    private static final Vec2 tmp = new Vec2();

    // ================= 选择与下令（RA2 §2 / §3） =================

    @Override
    public boolean tap(float x, float y, int count, KeyCode button){
        // 非指挥模式：完全走原版（建造/采矿等由父类键盘轮询处理，tap 不参与）
        if(Core.scene.hasMouse() || !commandMode){
            return super.tap(x, y, count, button);
        }

        // 原版语义：tap 视为"单击了一次"，阻止 selectUnitsRect 把它当框选处理
        tappedOne = true;

        if(button != KeyCode.mouseLeft){
            // RA2：右键单击 = 清空全部选择。
            // GestureDetector 只在"单击"（按下到抬起位移 < tap square 且未进入 panning）时回调 tap，
            // 天然区分拖拽 → 右键拖拽不会走到这里（= 无操作，镜头零改动）。
            // 原版右键 commandTap 下令已在 touchDown 被拦截，见下。
            if(button == KeyCode.mouseRight){
                clearSelection();
            }
            return false;
        }

        float mx = Core.input.mouseWorldX(), my = Core.input.mouseWorldY();
        Unit unit = selectedCommandUnit(mx, my);
        Building build = world.buildWorld(mx, my);
        boolean ownCommandableBuild = build != null && build.team == player.team() && build.isCommandable();
        boolean shift = Core.input.shift();

        // 双击优先（含 Shift+双击，与原版 selectTypedUnits 语义一致）：
        // 选中屏幕内所有同类型单位（RA2 §2 双击语义）。
        if(count >= 2){
            selectTypedUnits();
            return false;
        }

        // Shift + 单击：点单位/己方可指挥建筑 = toggle 加减选（原版 tapCommandUnit）；点空地/敌人 = 队列下令
        if(shift){
            if(unit != null || ownCommandableBuild){
                tapCommandUnit();
            }else if(selectedUnits.any() || commandBuildings.any()){
                commandTap(x, y, true);
            }
            return false;
        }

        // RA2 核心差异：点谁选谁（替换当前选择），而非原版的 toggle
        if(unit != null){
            selectedUnits.clear();
            selectedUnits.add(unit);
            commandBuildings.clear();
            Events.fire(Trigger.unitCommandChange);
            return false;
        }

        // 点击己方可指挥建筑：替换选择为该建筑（战斗建筑选中后可下令攻击）
        if(ownCommandableBuild){
            selectedUnits.clear();
            commandBuildings.clear();
            commandBuildings.add(build);
            Events.fire(Trigger.unitCommandChange);
            return false;
        }

        // 点空地/敌人/不可指挥建筑：有选择 → 下令（移动/攻击）；无选择 → 无操作（RA2 §2）
        if(selectedUnits.any() || commandBuildings.any()){
            commandTap(x, y, false);
        }

        return false;
    }

    // ================= 右键语义翻转（RA2 §0/§2） =================

    @Override
    public boolean touchDown(float x, float y, int pointer, KeyCode button){
        if(Core.scene.hasMouse() || !commandMode){
            return super.touchDown(x, y, pointer, button);
        }

        if(button == KeyCode.mouseRight){
            // 关键：原版 DesktopInput 在此对右键调用 commandTap 下令（PLAN §6 高风险项）。
            // 此处完整拦截、绝不调用父类 —— 右键不再下令；清空动作交由 tap() 的"单击"判定。
            return true;
        }

        // 其余按键（含 commandQueue 绑定的鼠标键）保持原版行为
        return super.touchDown(x, y, pointer, button);
    }

    /** 清空全部选择（单位 + 指挥建筑）。 */
    private void clearSelection(){
        selectedUnits.clear();
        commandBuildings.clear();
        Events.fire(Trigger.unitCommandChange);
    }

    // ================= 快捷键轮询 + 进入方式托管（RA2 §5） =================

    private boolean holdOverrideLogged;

    @Override
    public void update(){
        super.update();

        // 模组生效期间强制"切换"进入指挥模式（RA2 无按住式概念）。
        // 原版 DesktopInput.update() 每帧读 commandmodehold 决定按住/切换，
        // 因此托管该设置即可翻转语义；用户在设置里改回"按住"也会被下一帧自动翻回。
        if(Core.settings.getBool("commandmodehold", false)){
            Core.settings.put("commandmodehold", false);
            if(!holdOverrideLogged){
                holdOverrideLogged = true;
                Log.info("[ra2-controls] commandmodehold=true detected, forced back to toggle mode.");
            }
        }

        // 快捷键（RA2 §5）：仅指挥模式、无 UI 焦点时生效。
        // 键位为模组自定义 KeyBind（设置→控制→RA2 分组，玩家可改绑），默认 F/X/Home。
        if(state.isGame() && !Core.scene.hasField() && !Core.scene.hasDialog() && commandMode){
            // 停止：清空全部指令，原地待命（仍自动还击射程内敌人）
            if(Core.input.keyTap(Ra2Mod.bindStop)){
                stopSelected();
            }

            // 散开：各单位向自身周围就近散点下令
            if(Core.input.keyTap(Ra2Mod.bindSpread)){
                spreadSelected();
            }

            // 回核心：一次性跳回最近核心视角（走原版 panCamera，不改跟随机制）
            if(Core.input.keyTap(Ra2Mod.bindPanCore)){
                panToCore();
            }
        }
    }

    /** 停止：对所选单位下发 UnitStance.stop（服务端会清除其全部指令，F5）。 */
    private void stopSelected(){
        if(selectedUnits.isEmpty()) return;
        // stop 不是真姿态：setUnitStance(stop) 在服务端等价于 clearCommands（F5）
        Call.setUnitStance(player, selectedUnitIds(), UnitStance.stop, true);
        Events.fire(Trigger.unitCommandChange);
    }

    /** X = 散开：每个单位向自身周围 4~10 格内的随机点下令移动（纯输入层，PLAN §3）。 */
    private void spreadSelected(){
        if(selectedUnits.isEmpty()) return;
        for(Unit u : selectedUnits){
            float ang = (float)(Math.random() * 360.0);
            float dist = (4f + (float)Math.random() * 6f) * tilesize;
            tmp.trns(ang, dist).add(u.x, u.y);
            // 逐单位单独下令（各散各的点）；非队列、单单位即 finalBatch
            Call.commandUnits(player, new int[]{u.id}, null, null,
                new Vec2(tmp.x, tmp.y), false, true);
        }
        Events.fire(Trigger.unitCommandChange);
    }

    /** Home = 跳回最近核心（无核心则不动作）；走 panCamera，镜头跟随机制零改动。 */
    private void panToCore(){
        if(player.team() == null) return;
        var data = player.team().data();
        CoreBuild core = data.lastCore != null ? data.lastCore
            : (!data.cores.isEmpty() ? data.cores.first() : null);
        if(core != null){
            panCamera(new Vec2(core.x, core.y));
        }
    }

    private int[] selectedUnitIds(){
        int[] ids = new int[selectedUnits.size];
        for(int i = 0; i < ids.length; i++){
            ids[i] = selectedUnits.get(i).id;
        }
        return ids;
    }

    // ================= Shift 框选并入（RA2 §2，补齐 F7 缺失） =================

    @Override
    public boolean multiUnitSelect(){
        // 原版桌面端恒为 false（框选永远清空重选）；RA2：Shift 框选 = 并入当前选择
        return Core.input.shift();
    }
}
