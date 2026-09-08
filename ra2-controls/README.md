# ra2-controls

Mindustry 桌面端 RA2 风格指挥操作模组。当前进度见 `../PLAN.md`（P1 脚手架 + P2 第一个操作）。

## 构建

需要 JDK 17+（本机 21 可用）。本模组以本机 Steam 版 `desktop.jar` 为编译基准（v8 / 159.7）。

```
cd ra2-controls
.\gradlew.bat jar        # 输出 build/libs/ra2-controls.jar
.\gradlew.bat installMod # 构建并复制到 %APPDATA%\Mindustry\mods\
```

若游戏 jar 不在默认位置，设环境变量 `MINDUSTRY_JAR` 指向 `desktop.jar`。

## 安装

运行 `gradlew.bat installMod`，或用工作区任务「RA2: 打包并安装」。

## 已实现（P2 第二批：快捷键）

- **F → 停止**：清空所选单位全部指令，原地待命（仍自动还击射程内敌人；走 `UnitStance.stop`，即 RA2 的 S 因 WASD 占用改到 F——原版 H 被"选中全部单位工厂"占用）
- **X → 散开**：所选单位各自向自身周围 4~10 格随机点移动（纯输入层，阵型由原版分配）
- **Home → 回核心**：镜头一次性跳回最近核心（走原版 `panCamera`，不改跟随机制）
- **以上键位全部可配置**：注册为自定义 KeyBind，在 **设置→控制→RA2 指令** 分组中改绑/重置；指挥模式切换键本身是原版键位（默认左 Ctrl，不强制修改，同样可改绑）

## 已实现（P2 第一批）

- **左键单击己方单位 → 替换选择**（原版是 toggle，RA2 语义是替换；Shift+单击保留 toggle 加减选）
- **左键单击己方建筑（可指挥）→ 替换选择建筑**（Shift toggle 同理）
- **左键单击空地/敌人（有选择时）→ 下令**：移动 / 攻击（原版右键下令翻转为左键）
- **右键单击 → 清空全部选择**（原版右键下令被拦截；右键拖拽无操作）
- **指挥模式进入方式强制为切换**（原版"按住"设置 `commandmodehold` 由模组托管，改回会被自动翻回；卸载模组后可在 设置→游戏 手动改回）
- **指挥模式切换键默认改绑为 Tab**（原版默认左 Ctrl；首次启用一次性迁移，之后手动改绑不被覆盖）
- 编队 Ctrl+0-9 / 0-9 / 双击居中、Shift 框选并入、双击同类全选：沿用原版逻辑不改（Shift 单击/框选均为加选语义）

对应规范：`../RA2-CONTROLS.md` §2、§3（第一个操作=§2 左键替换选择 / 右键清空）。

## 尚未实现（后续里程碑）

- G 攻击移动、H 停止、X 散开、Home 回核心（P2 后续）
- Ctrl 框选剔除、三击全图同类
- Z+左键可视化路径点（RA2 原版键位）
- 设置界面与键位改绑
- Tier B 攻击移动真追击（AttackMoveCommand / ChaseReturnAI）
