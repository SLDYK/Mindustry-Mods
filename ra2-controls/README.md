# ra2-controls

Mindustry 桌面端 RA2 风格指挥操作模组。当前进度见 `../PLAN.md`（P1 脚手架 + P2 第一个操作）。

## 构建

需要 JDK 17+。本模组以本机 Steam 版 `desktop.jar` 为编译基准（v8 / 159.7），构建时自动跨平台探测：

- **macOS**：`Mindustry.app/Contents/Resources/desktop.jar`（含外置盘 Steam 库）
- **Windows**：`{C,D,E}:/...SteamLibrary/steamapps/common/Mindustry/jre/desktop.jar`
- **Linux**：`~/.steam/steam/...` 或 `~/.local/share/Steam/...`

找不到时设环境变量 `MINDUSTRY_JAR` 指向 `desktop.jar` 即可。

```
cd ra2-controls
./gradlew jar          # macOS/Linux；Windows 用 gradlew.bat
./gradlew installMod   # 构建并复制到 mods 目录
```

或直接用根目录脚本 / VS Code 任务（任务已跨平台，Windows 自动走 pack.ps1）：

```
./tools/pack.sh              # 构建
./tools/pack.sh --install    # 构建并安装
./tools/pack.sh --zip        # 构建并输出 zip 到 dist/
```

## 安装

模组安装目录（自动探测）：

- **macOS（Steam 版）**：`~/Library/Application Support/Mindustry/mods/`
- **Windows（Steam 版）**：游戏目录 `saves/mods/`
- 其它：`%APPDATA%/Mindustry/mods/`

运行 `./gradlew installMod`，或用工作区任务「RA2: 构建并安装」。

## 已实现（P2 第一批）

- **左键单击己方单位 → 替换选择**（原版是 toggle，RA2 语义是替换；Shift+单击保留 toggle 加减选）
- **左键单击己方建筑（可指挥）→ 替换选择建筑**（Shift toggle 同理）
- **左键单击空地/敌人（有选择时）→ 下令**：移动 / 攻击（原版右键下令翻转为左键）
- **右键单击 → 清空全部选择**（原版右键下令被拦截；右键拖拽无操作）
- **指挥模式进入方式强制为切换**（原版"按住"设置 `commandmodehold` 由模组托管，改回会被自动翻回；卸载模组后可在 设置→游戏 手动改回）
- **原版指挥专属键全部解除绑定**：select_all_units(G)、select_all_unit_factories(H)、select_all_unit_transport、command_queue(鼠标中键) 安装时 unset——指挥操作 100% 由模组键位定义；想找回某项原版功能可在 设置→控制 重新设键
- 指挥模式切换键保持原版默认（左 Ctrl），可在 设置→控制 自行改绑（如 Tab）；模组不强制修改
- 编队 Ctrl+0-9 / 0-9 / 双击居中、Shift 框选并入、双击同类全选：沿用原版逻辑不改（Shift 单击/框选均为加选语义）

对应规范：`../RA2-CONTROLS.md` §2、§3（第一个操作=§2 左键替换选择 / 右键清空）。

## 尚未实现（后续里程碑）

- G 攻击移动（Tier A 近似：移动 + pursueTarget stance；Tier B 真追击）
- Z+左键可视化路径点（RA2 原版键位）
- T 选择同类按键触发（双击已等价）、Ctrl 框选剔除、三击全图同类
- 设置界面（逐项开关 + 一键还原原版）
- Tier B 内容层（AttackMoveCommand / ChaseReturnAI）、联机双端测试
