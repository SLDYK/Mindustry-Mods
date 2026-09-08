# PLAN — RA2 风格指挥操作模组（ra2-controls）

> 目标：重写 Mindustry 桌面端指挥模式（command mode）的操作逻辑，使操作手感与《红色警戒2》一致。
> 配套文档：**`RA2-CONTROLS.md`** — RA2 操作逻辑与快捷键的权威参考（实现时以其为准）。**鼠标方案只做 RA2 原版默认：左键=选择+下令，右键单击=清空**（用户已确认，不做"左选右令"变体）。**镜头跟随/平移完全保留 Mindustry 原版机制，模组零改动**（用户已确认 2026-09-04）。
> 状态：**P2 进行中**（Tier A 输入层约 70%：鼠标语义翻转 + F/X/Home 快捷键已落地并实机验证加载；剩 G 攻击移动、Z 路径点、设置界面）。本文档为唯一执行依据，后续实现严格按里程碑推进。
> 前置调研：已核对 Mindustry 159 官方源码 `DesktopInput.java` / `InputHandler.java` / `CommandAI.java` / `UnitCommand.java` / `Control.java`（2026-09-03）。

---

## 1. 目标与范围

### 1.1 做（In Scope）
- 桌面端（Windows/Linux/Mac）指挥模式的**选择、下令、编队、快捷键**全面 RA2 化
- 新增 **攻击移动（Attack-Move）**、**停止（Stop）** 等经典 RTS 指令
- 多人模式兼容（Tier A 仅客户端安装即可；Tier B 需双方安装）
- 全部功能可在设置中逐项开关，可一键还原原版操作

### 1.2 不做（Out of Scope）
- 移动端（触屏输入模型完全不同，`MobileInput` 不在改造范围）
- 建造、采矿、蓝图等非指挥模式的输入逻辑（复用原版）
- 强制攻击地面（force-fire，原版网络协议无对应字段，仅做近似或砍掉）
- **镜头跟随/平移的全部改动**（右键拖拽平移、屏幕边缘滚动等）：镜头机制 100% 保留原版（2026-09-04 已定）
- 伺服端玩法改造（不做服务器插件）

---

## 2. 调研结论（已核实的事实）

以下均为对官方 master 源码的核对结果，是实现的前提，不再重复验证：

| # | 事实 | 出处 |
|---|------|------|
| F1 | `Control.setInput(InputHandler)` 是官方公开的输入处理器替换入口，会正确 remove 旧 processor 并 add 新的 | `Control.java` |
| F2 | 桌面端输入处理器为 `DesktopInput extends InputHandler`；原版在 `createPlayer()` 中实例化，并在 `ClientLoadEvent` 时调用 `input.add()` | `Control.java#createPlayer` |
| F3 | 指挥模式字段全部在 `InputHandler`：`commandMode`、`commandRect`、`commandRectX/Y`、`selectedUnits`、`commandBuildings`、`controlGroups[10]`（`IntSeq`，绑定 `blockSelect01..10`）、`tappedOne` | `InputHandler.java` |
| F4 | 下令走现有网络调用：`Call.commandUnits(player, unitIds, buildTarget, unitTarget, posTarget, queueCommand, finalBatch)`（自动按 200 个分批）、`Call.commandBuilding`、`Call.setUnitCommand`、`Call.setUnitStance` | `InputHandler.java` |
| F5 | `CommandAI` 已具备：`commandQueue`（上限 50）、`commandPosition/commandTarget`、阵型 `UnitGroup.calculateFormation`（按物理层分组）、`UnitStance.stop` 即清除全部指令 | `CommandAI.java` |
| F6 | `UnitCommand` 是 `MappableContent`（即注册型内容），字段含 `icon/controller/switchToMove/drawTarget/resetTarget/snapToBuilding/exactArrival/keybind`；`ai.command(x)` 会检查 `unit.type.commands.contains(command)` | `UnitCommand.java` / `CommandAI.java` |
| F7 | 桌面端 `multiUnitSelect()` 恒为 `false`（源码留有 `//TODO when shift is held? ctrl?`）→ **框选永远清空重选，Shift 框选加选在桌面端缺失** | `InputHandler.java` |
| F8 | 单击选择语义是 **toggle**：`tapCommandUnit()` 对点中的单位"已选则移除，未选则添加"→ 与 RA2 的"点谁选谁（替换）"不同 | `InputHandler.java#tapCommandUnit` |
| F9 | 双击全选同类已有（`selectTypedUnits`，全屏范围）；编组双击 400ms 内居中镜头已有 | `DesktopInput.java` |
| F10 | 指挥模式开关键为 `Binding.commandMode`，支持按住/切换两种设置（`commandmodehold`）；玩家单位 `canBoost` 且键位冲突时指挥模式自动禁用 | `DesktopInput.java#update` |
| F11 | 攻击目标光标已存在：`ui.targetCursor`（右键点敌人 / 光标悬停可选目标时） | `DesktopInput.java` |
| F12 | 阵型分配以"同一次下令批次 + 同一目标点 + 同一物理层"为单位，`finalBatch=true` 时结算 | `InputHandler.java#commandUnits` |

**待 P0 运行时确认的假设**（源码静态分析无法 100% 确定）：
- A1：`Core.settings` 中动态注册 `KeyBind` 是否可行（决定 G/S/X 等新键的注册方式；fallback：引导用户在游戏设置中重绑现有关键位）
- A2：~~桌面端是否有屏幕边缘滚动~~ → 已核实无，且已决定不做（镜头零改动）
- A3：`Version.number` 与 `minGameVersion` 校验的精确取值（159 / 146）

---

## 3. 操作对照表（RA2 → 实现方案）

| 操作 | RA2 行为 | 原版现状 | 方案 | Tier |
|------|---------|---------|------|------|
| 左键单击单位 | 点谁选谁（替换当前选择） | F8：toggle | 改写 `tap()`：默认替换选择 | A |
| 左键单击空地 | **有选择时=移动下令**；无选择时无操作 | 点空地=清空 | **翻转语义**：空地左键→`commandTap` 下移动令 | A |
| 左键点敌人 | 攻击 | 左键无下令功能 | **新增**：有选择时左键点敌人→`commandTarget` | A |
| 左键框选 | 框内全选（替换） | ✅ 相同 | 保留 | A |
| Shift+左键单击 | 加选/减选该单位 | 单击无 Shift 语义 | 补齐 | A |
| Shift+左键框选 | 并入当前选择 | ❌ F7 | 补齐（`multiUnitSelect` 覆写 + 语义修正） | A |
| Ctrl+左键框选 | 从选择中剔除 | 无 | 补齐（可选，默认关） | A |
| 双击单位 | 全选屏幕内同类型 | ✅ F9 | 保留 | A |
| 右键单击 | **清空全部选择** | 右键=下令 | **翻转语义**：拦截右键单击→清空 `selectedUnits` | A |
| 右键拖拽 | **平移镜头** | 右键=下令 | ❌ 不做：镜头跟随完全保留原版机制（2026-09-04 已定）；指挥模式内右键拖拽无操作 | — |
| Shift+左键（有选择时空地） | 队列指令 | ✅（`commandQueue` 键） | 保留，改挂左键；**另做可视化路径点模式（Z 按住左键连点，画编号旗帜，RA2 原版键位）** | A |
| 攻击移动 | 沿路自动接敌，交战后继续/到点恢复 | ❌ 无 | Tier A 近似：移动 + 开启 `pursueTarget` stance；Tier B 真·追击；键位默认 G | A/B |
| 停止 | S | F5：`UnitStance.stop` 已有，无快捷键 | F 键（原定 H，实测原版 H=`selectAllUnitFactories` 全选工厂被占用，改 F；其原版绑定仅蓝图模式生效） | A |
| 散开 | X | 无 | 各单位向自身周围随机点下令（纯输入层可做）；注意原版 X 无绑定冲突（`deselect`=X? 实测 X 空闲） | A |
| 全选同类（RA2 T 键语义） | — | 原 G=`selectAllUnits` 全选指挥单位 | **R7 解除绑定**：G 释放给攻击移动；原"全选单位"功能如需保留可走模组键位（待定） | A(可选) |
| Home | 跳回最近核心视角 | 无 | 相机平移至最近核心 | A(可选) |
| T | 选择同类型 | 双击已有 | 加按键触发 `selectTypedUnits` | A(可选) |
| Ctrl+1..0 / 1..0 | 编队 / 选队 / 双击居中 | ✅ F9（绑定 `blockSelect01..10`、`createControlGroup`） | 保留；确认默认键位后写入文档 | — |
| 屏幕边缘滚动 | ✅ | A2 待确认 | ❌ 不做：属镜头行为改动，与"镜头零改动"决定冲突 | — |
| 光标样式 | 攻击移动=红色十字 | F11 已有 `targetCursor` | 复用 | A |

**鼠标语义翻转是本项目最大的行为改动**：Mindustry 原版指挥模式是"左选右令"，RA2 原版默认是"左选+左令、右键单击清空"。本模组忠实复刻后者（用户已确认只保留原版默认，不做 Alternate 变体）。左键下令仅在 `commandMode` 且有选择时生效，不影响建造/采矿（那些走父类逻辑）。

**键位冲突预案**：A/S 与 WASD 相机移动冲突 → 默认 `G`=攻击移动、`H`=停止（或按 A1 结论定），设置页可改；文档中说明改绑 A/S 的副作用。

---

## 4. 架构设计

### 4.1 分层与"接管矩阵"架构（2026-09-09 定案）

> **架构决策（用户已确认，2026-09-09 二次定案）**：采用**行为层替换 + 状态层保留**的接管矩阵方案，而非"commandMode 恒 false 的纯替换式"。**原版指挥专属键一律解除绑定（unset）**——不是代码层屏蔽，而是让原版指挥快捷键的绑定全部清空：原版 `keyTap(Binding.xxx)` 分支原样存在但永不触发，指挥操作 100% 由模组键位（设置→控制→RA2 分组）定义，与原版按键零冲突。
> 理由（经反汇编本机 desktop.jar v8/159.7 + 官方 v8 源码逐行核对）：
> 1. 原版指挥逻辑全部有 `if(commandMode)` 守卫，是**封闭集合**（下表 12 项），`minGameVersion` 锁定后不会新增——冲突面天然有限；
> 2. `pollInputPlayer()` 为包私有不可覆写，若 commandMode 恒 false，指挥模式下左键会掉进原版普通分支（误采矿/误开枪/弹建筑配置 UI），只能每帧消毒 3 个状态，脆且不可靠；
> 3. `commandMode=true` 本身是引擎护栏（`canShoot()` 排除指挥模式、`pollInputPlayer` 分支互斥），保留它 = 保留引擎送的保护。
> 由此冲突面从"每个钩子与原版的隐式交互"收敛为"下表 12 项"，每次 Mindustry 升级只需对照复核。

**接管矩阵（v8 / build 159.7 实测；每项处置 = 模组的最终行为）**：

| # | 原版指挥行为 | 原版位置 | 处置 | 模组实现 |
|---|-------------|---------|------|---------|
| R1 | 指挥模式开关（toggle/hold 二态 + boost 冲突自动禁用） | `DesktopInput.update()` | **托管** | 强制切换式（`commandmodehold` 每帧翻回 false） |
| R2 | 左键单击=toggle 选择 / 双击=屏幕同类 | `DesktopInput.tap()` | **替换** | RA2 替换语义（`Ra2Input.tap` 已实现） |
| R3 | 右键=下令移动/攻击 | `DesktopInput.touchDown()` | **替换** | 右键=清空选择（`touchDown` 拦截 + `tap` 判单击） |
| R4 | 左键按下启动框选 `commandRect` | `pollInputPlayer()`（包私有） | **复用** | 直接借用生命周期（含 `tap` 的 `tappedOne` 抑制单击误判） |
| R5 | 松开左键结算框选（内部调 `multiUnitSelect()`） | `update()` 尾部 | **适配** | 覆写 `multiUnitSelect()`=Shift（框选并入，F7） |
| R6 | `commandTap()` 下令路径 | `InputHandler` | **替换** | 左键触发移动/攻击（`tap` → `commandTap`，已实现） |
| R7 | G=全选指挥单位 / H=全选工厂（默认键实测） | `update()` 指挥块 | **解除绑定** | `unset()` 清空 select_all_units(G)/select_all_unit_factories(H)/select_all_unit_transport，G 彻底释放给模组攻击移动 |
| R8 | 编组 Ctrl+数字 / 数字选队 / 双击居中 | `update()` 指挥块 | **保留绑定** | 与 RA2 语义一致，零改动 |
| R9 | commandQueue 键=鼠标中键追加队列指令 | `pollInputPlayer()` | **解除绑定** | 模组队列语义 = Shift+左键，原版中键队列不需要 |
| R10 | 有选择时悬停敌人→攻击光标 | `pollInputPlayer()` 尾部 | **保留** | RA2 §4 光标状态机直接复用 |
| R11 | 选中圈/目标线/队列线绘制 | `drawCommanded()` 系列 | **保留** | 复用；未来 Z 路径点旗帜用 `drawTop` 追加绘制 |
| R12 | 指挥模式禁止射击/采矿/配置误触发 | `canShoot()`/`pollInputPlayer` | **依赖** | 引擎护栏，保留 commandMode 即免费获得 |

```
Tier A（纯客户端输入层）        Tier B（内容层，可选）
├─ Ra2Mod (Mod 主类)           ├─ AttackMoveCommand (UnitCommand 内容)
├─ Ra2Input (extends DesktopInput) ├─ ChaseReturnAI (extends CommandAI)
├─ Ra2Settings (开关/键位)      └─ CommandInjector (注入 UnitType.commands)
├─ Ra2Bundles (中英)
└─ 安装器（control.setInput）
```

- **Tier A** 不注册任何内容、不下发新协议 → 只用 F4 的现有 remote → `hidden: true`，联机单端可用。
- **Tier B** 注册内容 → `hidden: false`，联机需双端；`AttackMoveCommand.controller` 返回 `ChaseReturnAI`（沿 `commandQueue`/`targetPos` 移动，遇敌 `commandTarget(target, false)`，目标死亡或 leash 超限回 `commandPosition(恢复路径)`，到达终点自动 `command(moveCommand)`）。
- 两层独立开关：设置里可只开 Tier A（近似攻击移动）或全开（真追击）。

### 4.2 安装时序（关键陷阱）

```java
Events.on(ClientLoadEvent.class, e -> {
    // 必须用 app.post：原版 input.add() 在此事件中调用，直接 setInput 会抢在 add() 之前
    Core.app.post(() -> {
        if (!(control.input instanceof Ra2Input) && settings enabled) {
            control.setInput(new Ra2Input());
        }
    });
});
```

### 4.3 `Ra2Input` 改造点（只动这些公共钩子）

| 方法 | 改什么 |
|------|--------|
| `tap(x,y,count,button)` | 左键单击：无 Shift→替换选择；Shift→toggle；双击→同类全选（沿用 `selectTypedUnits`）；**有选择时左键点空地/敌人→下令**（RA2 语义） |
| `touchDown`（右键拦截） | 右键单击（位移小于阈值）→清空 `selectedUnits`（RA2 语义）并屏蔽父类右键下令；右键拖拽不处理（镜头零改动） |
| `update()` | 托管 commandmodehold（强制切换式）；新快捷键轮询（H/X/T、攻击移动待命态）；攻击移动待命光标；**不做任何镜头改动** |
| 安装器（Ra2Mod） | 指挥模式切换键一次性改绑为 Tab（记录标记后尊重用户手动改绑）；commandmodehold 强制 false |
| `multiUnitSelect()` | 返回"Shift 或 Ctrl 按下"，并配合 `selectUnitsRect` 修正加选/减选语义（见 F7 注释的反直觉逻辑） |
| `commandTap` 调用键位 | 原 `touchDown` 中的 `mouseRight → commandTap` 翻转为左键触发；waypoint 模式改为 Z 按住左键连点（RA2 原版键位），本地记录点位并绘制编号旗帜，松开时按序批量 `commandTap(x,y,true)` |
| `drawUnitSelection()` / `drawCommanded()` | 框选预览跟随加/减选语义；waypoint 旗帜绘制 |
| 其余 | **一律不碰**，建造/采矿/蓝图全走父类 |

### 4.4 工程结构

```
f:\SteamTools\Mindustry\ra2-controls\
├── mod.hjson                  # main: ra2.Ra2Mod; hidden 由 Tier 决定
├── build.gradle               # 参考 MindustryJavaModTemplate，JDK 21 + mindustry 依赖
├── src/ra2/
│   ├── Ra2Mod.java
│   ├── input/Ra2Input.java
│   ├── input/WaypointPath.java
│   ├── settings/Ra2Settings.java
│   └── (tier B) content/AttackMoveCommand.java, ai/ChaseReturnAI.java
├── assets/bundles/bundle_zh_CN.properties, bundle_en.properties
├── assets/sprites-override/ (可选光标)
└── tools/pack.ps1             # 扩展现有脚本：支持指定模组目录打包+安装
```

---

## 5. 里程碑

### P0 — Spike 验证（0.5–1 天）
- [x] 运行时打印 `Version.number`，确认 `minGameVersion` 取值（A3）
- [x] 最小模组（仅 `Log.info`）跑通 `gradlew jar` → 游戏加载
- [x] 实测 `control.setInput` 替换后：建造/采矿/指挥模式全部正常（F1/F2 的时序假设）
- [x] 验证 A1（动态注册 KeyBind，已实现 `KeyBind.add` + RA2 分类）；A2 已结案（边缘滚动无且不做）
- [ ] 产出：`DESKTOP_INPUT_HOOKS.md`（DesktopInput 可重写方法清单及调用时机）
- **验收**：最小模组进对局无报错，替换输入后原版功能无损 ✅

### P1 — 工程脚手架（0.5 天）
- [x] 建 `ra2-controls` Gradle 工程（编译基准为本机 Steam 版 desktop.jar，compileOnly 离线构建）
- [x] `mod.hjson`、`Ra2Mod` 骨架、安装器（含"已是 Ra2Input 则跳过"防重入）
- [x] 扩展 `tools/pack.ps1`：`-Mod ra2-controls` 参数化打包/安装；加 VS Code 任务；**已补 macOS 侧 `tools/pack.sh` + build.gradle 跨平台探测（2026-09-09）**
- **验收**：空壳模组安装进游戏，任务一键构建+安装 ✅（macOS/Windows 双平台构建链均验证通过）

### P2 — Tier A 输入层（2–3 天，核心交付）
- [x] **鼠标语义翻转**：左键=选择+下令（有选择时）、右键单击=清空（右键拖拽无操作，镜头零改动；`touchDown` 完整拦截原版右键下令，§6 最高风险项已解除）
- [x] RA2 单击选择语义 + Shift 语义（点击与框选）
- [x] F 停止（原 H 被原版"选中全部单位工厂"占用，改 F）；X 散开；Home 回核心（各注册自定义 KeyBind，设置→控制→RA2 分组可改绑）
- [ ] Ctrl 框选剔除（可选开关）
- [ ] T 同类选择按键触发（双击已等价）
- [ ] 攻击移动 Tier A 近似（待命键→光标变红→左键下达 移动+pursueTarget）
- [ ] Z+左键可视化路径点模式（RA2 原版键位；编号旗帜、松开批量下发 queue 指令）
- [ ] 设置界面（逐项开关 + 键位说明）+ 中英 bundle
- **验收**：沙盒对局按《P2 手测清单》逐项通过；多模组冲突场景（恢复开关）可用

### P3 — Tier B 内容层（2–4 天）
- [ ] `AttackMoveCommand`（drawTarget、switchToMove=false、exactArrival=false）
- [ ] `ChaseReturnAI`：追击/leash/回路径/到点切回 move 四态
- [ ] `CommandInjector`：注入原版全部 `UnitType.commands`；对第三方模组单位默认注入 + 黑名单设置
- [ ] 验证与阵型（F12）、`commandQueue`、stance 的交互无回归
- [ ] 本地 host + client 双端联机测试
- **验收**：攻击移动真追击；死亡目标正确返回路径；联机同步无异常

### P4 — 打磨与发布（1–2 天）
- [ ] 光标/音效/旗帜贴图统一风格（优先用 `sprites-override`）
- [ ] 《完整手测清单》跑一遍（含编队、蓝图标等回归项）
- [ ] README（键位表、联机说明、Tier 差异）、GitHub Release（jar，加 `mindustry-mod` topic）
- **验收**：公开 zip/jar 可被他人直接游玩

---

## 6. 风险与对策

| 风险 | 等级 | 对策 |
|------|------|------|
| **鼠标语义翻转与原版右键下令冲突**：`DesktopInput.touchDown` 中右键触发 `commandTap` 的逻辑必须被完整拦截，否则双重下令 | 高 | P0 验证覆写 `touchDown`/`tap` 能否完全屏蔽父类右键下令；若父类为私有/内联无法拦截，则退化方案=保留右键下令但文档注明与 RA2 的差异 |
| `DesktopInput` 部分逻辑为私有/内联，公共钩子覆盖不到 | 中 | P0 先产出 hook 清单；个别点用字段访问 + 复制小段逻辑，不反射改私有态 |
| 官方 159.x 更新破坏父类结构 | 中 | `minGameVersion: 159` 锁定；CI 留适配 note |
| 与其他输入类模组冲突 | 低 | 安装前检查 `control.input` 类型；设置提供"还原原版"开关 |
| 键位与 WASD/原版冲突 | 中 | 默认避开 A/S；全部可改绑；文档说明 |
| Tier B 联机不同步 | 高 | 内容注册走标准 ContentLoader；双端实测后才发布；`hidden` 正确设置 |
| 输入无法自动化测试 | 中 | 每里程碑配手测清单，P4 全量回归 |

---

## 7. 全局验收清单（摘录）

- 左键：点单位→只选它；点空地→**下令移动**（有选择时）；点敌人→**攻击**；框选→替换；Shift 框选→并入；双击→屏幕同类
- 右键：单击→**清空选择**（拖拽无操作，镜头机制与原版完全一致）
- Shift+左键空地→排队指令；Z+左键连点→编号路径点（RA2 原版键位）
- 攻击移动待命键→点地：部队沿路接敌、越过敌后继续前往目标点（Tier B）
- S：原地停止并清空队列；编队 Ctrl+数字 建队 / 数字 选队 / 双击居中
- 关闭模组开关后：行为与原版逐字节一致
- **建造/采矿/蓝图不受影响**：非指挥模式下左键行为与原版完全一致
- 联机（Tier B）：host 装模组 + client 装模组 → 指令/阵型/追击两端一致
