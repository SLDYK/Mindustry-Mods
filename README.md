# Mindustry 模组开发工作区

本工作区原先是两个独立的 GitHub 仓库，2026-09-12 合并为一个仓库（历史均已保留）：

| 模组 | 目录 | 说明 | 主要开发机 |
|------|------|------|------------|
| **ra2-controls** | `ra2-controls/` | RA2 风格指挥操作 Java 模组，见 `RA2-CONTROLS.md` / `PLAN.md` / `ra2-controls/README.md` | Windows |
| **Enemy Pause** | `Enemy Pause/` | 战役模式下按键暂停 / 继续敌方侧的各种倒计时（进攻 / 扩建 / 生产），见下文 | macOS |
| **erekir-items** | `erekir-items/` | 埃里克尔内容模组（容器工程）：可调电力节点 + 资源源（凭空产物品/液体/热量，类型与速率都可设），见 `erekir-items/README.md` | Windows |

三个模组相互独立（各自的 gradle 工程、各自的构建脚本），互不影响：

- **Windows 侧**：`tools/pack.ps1` 构建并安装到游戏 mods 目录（自动探测 `E:\SteamLibrary\...\Mindustry\saves\mods`）
- **macOS 侧**：`build.sh` / `deploy.sh` / `run.sh` / `check.sh` + `config.sh`，工具链在 `tools/jdk`、`tools/gradle-home`

## 当前进度

**P2 进行中**（Tier A 输入层约 70%）：鼠标语义翻转（左键=选择+下令、右键单击=清空）、Shift 框选并入、F 停止 / X 散开 / Home 回核心 已实现并实机加载验证；剩 G 攻击移动、Z 路径点、设置界面。里程碑勾选见 `PLAN.md` §5。

## 构建 / 安装

需要 JDK 17+，双平台脚本：

```
./tools/pack.sh              # 构建（macOS/Linux）
./tools/pack.sh --install    # 构建并安装到游戏 mods 目录
tools\pack.ps1 -Install      # Windows 等价操作
```

`pack.ps1` 三个模组通用，`-Mod` 传目录名即可（`-Desktop` 额外拷一份到桌面）：

```powershell
# Enemy Pause：构建 -> 装到游戏 mods -> 拷一份到桌面
tools\pack.ps1 -Mod "Enemy Pause" -Install -Desktop

# 埃里克尔可调电力节点：构建 -> 装到游戏 mods
tools\pack.ps1 -Mod "erekir-items" -Install
```

> 目录名不一定等于 jar 文件名（`Enemy Pause` 的产物是 `EnemyPause.jar`，取自 `settings.gradle`
> 里的项目名），所以脚本在 `build\libs\<目录名>.jar` 不存在时会退回取 `build\libs` 下最新的那个 jar。

游戏 `desktop.jar` 与 mods 目录自动跨平台探测（Windows Steam 库盘符 / macOS `Mindustry.app` / Linux）；找不到时设环境变量 `MINDUSTRY_JAR` 指向 `desktop.jar`。

## VS Code 任务

| 任务 | 平台 | 说明 |
|------|------|------|
| RA2: 构建 (ra2-controls) | 双平台 | gradle 构建，输出 `ra2-controls/build/libs/ra2-controls.jar`；Windows 走 `pack.ps1`，macOS/Linux 走 `pack.sh` |
| RA2: 构建并安装 | 双平台 | 构建并安装到游戏 mods 目录（自动探测：Windows Steam = 游戏目录 `saves/mods`；macOS = `~/Library/Application Support/Mindustry/mods`） |
| Enemy Pause: 构建 / 安装 / 拷桌面 | 双平台 | macOS 走 `build.sh && deploy.sh`；**Windows 走 `pack.ps1 -Mod "Enemy Pause" -Install -Desktop`** |
| Erekir Items: 构建 / 安装 | Windows | `pack.ps1 -Mod "erekir-items" -Install`，产物 `erekir-items/build/libs/erekir-items.jar` |
| Erekir Items: 校验产物 | Windows | 跑 `tools/eiverify/verify.ps1`，三个校验器共 197 项，离线校验 jar 结构与两个方块的逻辑（不启动游戏） |
| Erekir Items: 重新生成贴图 | Windows | 跑 `tools/gen-sprites.ps1` 重新生成全部方块贴图（PS + System.Drawing，无需美术工具） |
| Mindustry: 构建模组 | macOS | 跑 `build.sh`（默认构建 `Enemy Pause/`），同时是默认构建任务（`Cmd+Shift+B`） |
| Mindustry: 构建并部署 | macOS | `build.sh && deploy.sh` |
| Mindustry: 校验产物 | macOS | `check.sh` 离线校验产物 |

> `RA2:` 与 `Enemy Pause:` 任务用 `windows` 覆盖做了分平台：Windows 上走 `tools/pack.ps1`，
> macOS/Linux 上走 `tools/pack.sh` / `build.sh`。
> `Mindustry:` 三个任务目前是 macOS 专属（依赖 `build.sh` 等脚本和 `tools/jdk` 内置工具链）；
> Windows 上等价操作直接跑 `build.sh`/`check.sh` 对应的 PowerShell 命令（见模组章节）。
>
> `.vscode/settings.json` 里 Java 工具链的绝对路径属于 macOS 开发机，已按注释保留，Windows 上使用系统 JDK。

---

## Mindustry 模组开发环境

全自包含：工具链都在这个文件夹里，**不依赖也不改动系统里那套 Java**（那套是给 Minecraft 用的）。
编译用的 JDK 单独下在 `tools/jdk`，Gradle 缓存也留在 `tools/gradle-home`，不会写 `~/.gradle`。

### 环境概况

| 项目 | 值 |
| --- | --- |
| 游戏 | **Mindustry v160.1**（Steam 版，内置 JRE 25；实测 `Version.build = 160`，即 `steam build 160.4`） |
| 游戏目录（Windows） | `E:\SteamLibrary\steamapps\common\Mindustry`，数据目录就是游戏目录下的 `saves\`（mods 在 `saves\mods`） |
| 游戏应用包（macOS） | `/Volumes/ORICO/SteamLibrary/steamapps/common/Mindustry/Mindustry.app` |
| 真正生效的数据目录 | Windows：游戏目录下的 `saves\`；macOS：`Mindustry.app/Contents/Resources/saves/`（判断依据：里面有 `settings.bin`） |
| 编译用 JDK | macOS：`tools/jdk` — Temurin **21.0.12.1**；Windows：系统 JDK 21（`D:\OpenJDK`） |
| Gradle | **9.4.1**，分发包缓存在 `tools/gradle-home`，首次下完即离线可用 |
| 编译依赖 | `Enemy Pause/libs/dependencies.jar` — 官方 v160.1 版，15 MB |
| 模组项目 | `Enemy Pause/` |

> 游戏自带的 `jre` 是精简过的运行时，里面只有 `java` 没有 `javac`，所以编译必须另用 JDK。
> v160.1 的类文件是 Java 17，所以源码编译目标也是 `--release 17`。

### Enemy Pause 模组

`Enemy Pause/` 里的模组管敌方侧的倒计时：**暂停 / 继续**，以及**跳到终点**。

动的是倒计时，**不是敌人本身**：已经在场的敌方单位照常行动开火，
敌方工厂照常生产、照常激活，玩家侧也不受影响。

| 按键 | 功能 | 说明 |
| --- | --- | --- |
| **Y** | 暂停 / 继续 | 冻住倒计时，或让它接着走 |
| **U** | 跳过本轮 | 直接推到终点，让后果立刻发生 |

| 项 | 值 |
| --- | --- |
| 按键设置 | 在 设置 → 按键 的「常规」分组里可改（`keybind.enemy_pause` / `keybind.enemy_pause_skip`） |
| 生效范围 | 暂停只在战役模式生效（`rules.waves` 或 `rules.attackMode` 至少开着一个）；跳过不限战役。**两者在联机里都只有主机端能操作** |
| 游戏内反馈 | 每次操作都在屏幕下方弹一条提示；**联机时主机端按 Y 弹出/收回的提示会在所有客户端同时弹**，客户端自己按 Y / U 则弹「联机时仅主机端可控制」 |

之所以选 `Y` / `U`：Mindustry 自带的绑定已经占掉了 a~z 里除 `i k l o u y` 以外的所有字母，
其中语义最贴近的 `p`（地图标记 ping）和空格（游戏暂停）都被占了。

#### 冻结哪两项

| # | 倒计时 | 游戏里的数据 | 冻结方式 |
| --- | --- | --- | --- |
| 1 | 下一波进攻（HUD 上的波次倒计时） | `state.wavetime` | 每帧写回暂停时的读数 |
| 2 | **地图目标里的计时目标**，例如「敌人来袭：9:35」「敌方在 N 后扩大单位生产」 | `MapObjectives.TimerObjective.countup` | 每帧写回暂停时的读数（反射，`countup` 是 protected） |

> 第 2 项是最初漏掉的那一个：截图里那个「敌人来袭」**不是**波次倒计时。
> 那几条文案（`objective.enemiesapproaching` / `objective.enemyescalating` 等）是**由关卡地图数据**
> 以 `@objective.enemiesapproaching` 的形式引用的，Java 代码里搜不到字面量，所以按代码搜索会完全找不到。

`TimerObjective` 的推进与完成判定（`MapObjectives` 内部类）：

```java
public boolean update(){
    return (countup += Time.delta) >= duration * state.rules.objectiveTimerMultiplier;
}
// 界面显示的 = duration * 倍率 - countup，格式化成 MM:SS
int i = (int)((duration * state.rules.objectiveTimerMultiplier - countup) / 60f);
```

> 注意这个 `text()` 算的是 `limit - countup`：**countup 一旦越过上限，界面上的数字就是负数**
> （`m = i / 60`、`s = i % 60` 都是负的，显示成类似 `-1:-5`）。这正是联机客户端的那个现象，
> 原因见下文「联机同步」。

第 1 项的时机：游戏在 `Logic.update()` 里这样推进倒计时

```java
if(!state.isEditor()) state.rules.objectives.update();   // ← 第 2 项在这里推进

if(state.rules.waves && state.rules.waveTimer && !state.gameOver){
    if(!isWaitingWave()) state.wavetime = Math.max(state.wavetime - Time.delta, 0);
}
if(!net.client() && state.wavetime <= 0 && state.rules.waves) runWave();

updateEntities();
Events.fire(Trigger.afterGameUpdate);                    // ← 模组的写回挂在这里
```

两项都在 `Trigger.afterGameUpdate` **之前**推进，所以每帧写回读数就等于把这一帧的时间推进抹掉。

几个细节：

- 两项的冻结值都最少保留 10 tick（1/6 秒）。**这个边界保护对第 2 项尤其关键**：两项都是
  「够到阈值就触发」，而触发判定在每帧推进里、比我们的写回更早。若在只剩最后一两帧时按暂停，
  原样冻结的话那次判定仍会通过——波次是 `runWave()` 把这一波放出来，计时目标则是**直接判定完成
  并触发后果**（比如敌人真的开始进攻）。抬到 1/6 秒堵住这个缺口，代价是恢复后最多差 0.17 秒。
  （`Time.delta` 上限是 3，所以 10 tick 有 3 帧余量。）
- 第 2 项的 `countup` 是 `protected`，模组和它不同包，只能反射。取不到时只放弃这一项，
  波次倒计时照常工作（启动日志里会有 warn）。
- 计时目标可以挂在别的目标下面（`MapObjective.parents`），父目标没完成前它根本不参与更新，
  所以不能只在按 Y 的瞬间扫一遍——每帧都要补记新出现的计时目标。
- 换地图 / 退出战役 / 游戏结束 / 玩法状态变了（`waves`、`attackMode` 都关掉）/ 期间放出去过一波
  （比如点了 HUD 的提前进攻），暂停会自动解除，不会残留到下一局。
- 恢复时不需要「还原」：两个读数一直停在冻结值上，游戏自己会从那里接着加。
- **联机时只有主机端能操作**（v0.5.1 起强制）：客户端上两个键都只弹「联机时仅主机端可控制」，
  不写任何状态。挡住的是 `Net.client()`（`!server && active`），所以单机与**自己开房的主机端**都不算
  客户端、功能照常。必须在客户端侧拦住的原因：`skipWave()` 会在本地凭空刷出一批敌人并自行
  `state.wave++`，目标完成也传不到别人那儿，随后都被服务端的状态覆盖——纯属不同步。
- 主机端冻结的 `state.wavetime` 会随状态快照（`Call.stateSnapshot`，客户端直接赋值）下发，
  **但地图目标的 `countup` 不参与同步**（`MapObjectives.update()` 的注释写明「客户端只更新计时、
  不能完成目标」）。所以联机时客户端必须自己按住这两项——见下文「联机同步」。
- **联机两端的分工**：主机端冻结后广播给所有客户端，客户端弹同样的提示并把自己的倒计时也停住；
  客户端的 `countup` 只能就地取值（拿不到主机端的数字），若已经越过上限会被钳回阈值前 10 tick。
- **不覆盖**：已经在场上的敌方单位、敌方工厂生产与激活、敌方基地 AI 的扩建/调度节奏，
  以及行星图上「敌方入侵已占领星区」的回合倒计时。

> 演进记录：v0.1 只冻波次；v0.2 误把「敌方行为」也冻了（工厂激活、生产进度、基地 AI 计时器）；
> v0.3 按需求移除行为类；v0.4 才找到真正该冻的第 2 项——地图目标里的计时目标；v0.5 加入 U 键跳过；
> v0.5.1 联机时把 Y / U 都限制在主机端（客户端只弹提示）；
> v0.6.0 联机同步：主机端的状态广播给客户端，客户端弹同样的提示并把倒计时也停住
> （`EnemyPausePacket`），跳过的目标完成改走 `Call.completeObjective` 以同步到客户端。

#### 联机同步（v0.6.0）

联机里的分工：**主机端真正操作，客户端跟着停**。两端各自持有一份 `EnemyTimers`。

```
主机端按 Y
  ├─ 本地：EnemyTimers 冻住 wavetime / countup，弹「已暂停」
  └─ Net.send(EnemyPausePacket(paused=true, waveTime=冻结读数), reliable)
        └─ 客户端：onRemote() → 弹「已暂停」+ EnemyTimers.remote(waveTime) 把自己的也冻住
主机端再按 Y（或局面变了自动解除）
  └─ 广播 paused=false → 客户端弹「已继续」+ 放开
```

**为什么非要自己发封包**：倒计时的推进与目标完成都是服务端权威的，客户端既看不到
「主机端按了 Y」，也推不出该把数字停在哪一帧。封包只有两个字段：

| 字段 | 作用 |
| --- | --- |
| `paused` | 客户端据此弹提示、并按 / 放自己的倒计时 |
| `waveTime` | 主机端冻结住的波次读数，客户端照抄，两边显示同一个数字 |

实现要点：

- 注册在 `EnemyPauseMod.init()` 里，**放在无头服务端判断之前**：封包 ID 是按注册顺序递增分配的
  （`Net.registerPacket`），两端注册的东西不一样就会错位。且只在
  `Net.getPacketClassId(...) == -1` 时注册——mod 重新加载会再跑一次 `init()`，重复注册会白占一个 ID。
- `Packet.handleClient()` 跑在网络线程上，而这里要碰 `wavetime` / `countup`，所以用 `Core.app.post`
  丢回主线程再执行。
- 客户端不走 `usable()` 判断——它的 `!net.client()` 会让客户端永远「不可用」，
  那就变成主机端停了、客户端照跑。客户端的可用性由主机端说了算（广播只会从真的暂停后发出）。
- 安全性：Mindustry 在连接时会比对双方模组列表（`NetServer.handleConnect`，不一致直接踢），
  所以联机里两端一定都装着本模组、也都注册了这个封包，ID 一致。
- 已知限制：**改完代码后不要只在一端热重载**。封包 ID 是按注册顺序分配的，重载会让本地多分配一个 ID
  （`packetToId` 里新旧类各占一个），与未重载的对端错位。两端一起重启就没有这个问题。

**修掉的那个现象**：以前主机端暂停后，客户端会一直倒计时到负数。原因有两层：

1. 地图目标的 `countup` **完全不参与同步**（只有 `wavetime` 走状态快照）。`MapObjectives.update()`
   的注释写明「客户端只更新计时、不能完成目标」：客户端每帧照旧 `countup += delta`，却永远不会判定完成，
   于是越过上限后一路累加，`TimerObjective.text()` 显示的 `limit - countup` 就变成负数。
2. 波次倒计时虽然会被快照纠正，但快照间隔内客户端仍在自己递减，看起来“没停”。

所以客户端收到广播后也用自己的 `EnemyTimers` 把两者按住；`countup` 已经在阈值外时会被钳回
阈值前 10 tick（`MIN_HOLD_TICKS`），负数随即消失。

#### 跳过（U 键）

把正在走的倒计时**直接推到终点**，让后果立刻发生。实现见 `EnemySkip`：

| 倒计时 | 跳过方式 | 后果 |
| --- | --- | --- |
| 波次 | `Vars.logic.skipWave()` —— 就是 HUD 上那个「提前进攻」按钮干的事 | 立刻出兵，并把 `wavetime` 重置成一整轮 |
| 目标计时 | `MapObjective.done()` | 目标立刻完成，执行 `objectiveFlags` 增减与 `completionLogicCode` |

关于目标计时为什么要自己调 `done()` 而不是 `Call.completeObjective(index)`：
后者的方法体只有 `objectives.get(index).done()` 一句（已对 160.1 的字节码确认），
直接调完全等价，还省掉了对 `Vars.net` 的依赖（`Call` 内部要用 `net.server()`）。

跳过时的几个判断：

- **只跳「此刻正在跑」的计时目标**（`qualified()`），而且是**先收集、再逐个完成**。
  计时目标可以挂在父目标下面（`MapObjective.parents`），父目标没完成前它根本不参与更新。
  如果边遍历边完成，刚被父目标解锁的子目标会在同一轮里变得 `qualified` 而被一起跳掉——
  但它其实才刚开始数，不该跳。
- 波次要满足 `rules.waves && rules.waveSending`，且游戏没有自己按住倒计时
  （`logic.isWaitingWave()`，比如场上还有敌人又开了 `waitEnemies`）、也没有正在放兵
  （`spawner.isSpawning()`）。
- 没有可跳的东西时弹「当前没有可跳过的倒计时」，不会静默失败。
- **如果在暂停状态下按 U，会先解除暂停**（按「自动解除」处理，不还原读数）——
  跳过之后时间正常流逝。
- 菜单 / 编辑器里不做任何事：既没有「本轮倒计时」，也不能让 `runWave()` 在空世界上跑。
- **联机里的客户端不做任何事**，只弹「联机时仅主机端可控制」。
- 目标完成走的是 `Call.completeObjective(index)`，**不是直接 `done()`**：
  单机下它就是本地执行 `done()`（已对 160.1 的字节码确认：`!net.server() && !net.active()` 时
  本地调用），主机端除了本地执行还会额外把 `CompleteObjectiveCallPacket` 发给所有客户端。
  直接调 `done()` 在联机下客户端什么也收不到，它的计时目标会一直跑下去、显示成负数，
  而且永远不会从目标列表里消失。

### 目录结构

```
build.sh                 构建模组
deploy.sh                把产物复制到游戏的 mods 目录
check.sh                 离线校验产物，不用启动游戏
run.sh                   构建 + 部署 + 启动游戏 + 跟随日志
config.sh                游戏路径配置（换位置只改这里）
tools/jdk                内置 JDK
tools/gradle-home        Gradle 分发包与缓存（GRADLE_USER_HOME）
tools/modcheck           产物校验器源码
.vscode/                 Java/Gradle 扩展配置与构建任务
Enemy Pause/             模组项目（见下）
```

### 用法

```bash
./build.sh                  # 只编译
./check.sh                  # 离线校验产物（建议改完 mod.hjson / 文案后就跑）
./build.sh && ./deploy.sh   # 编译 + 部署到游戏
./run.sh                    # 编译 + 部署 + 启动游戏 + tail 日志（一条龙开发循环）
```

Windows 侧（本机游戏目录 `E:\SteamLibrary\steamapps\common\Mindustry`）没有对应的 shell 脚本，
直接用 gradle + 复制两步：

```powershell
cd "Enemy Pause"
$env:GRADLE_USER_HOME = "$PWD\..\tools\gradle-home"   # 缓存不写 ~/.gradle
.\gradlew.bat jar --offline
Copy-Item build\libs\EnemyPause.jar "E:\SteamLibrary\steamapps\common\Mindustry\saves\mods\" -Force
```

> 模组只在游戏启动时加载，换 jar 后必须重启游戏。

在 VS Code 里：`Cmd+Shift+B` 触发默认构建任务；`Cmd+Shift+P` → `Tasks: Run Task` 里还有
「Mindustry: 构建并部署」和「Mindustry: 校验产物」。

`check.sh` 会检查：

1. jar 根目录有 `mod.hjson`，能按 HJSON 解析，必需字段（含 `java: true`）齐全
2. `main` 指向的类能加载、继承 `mindustry.mod.Mod`、有 public 无参构造
3. 语言包放在 jar 内的 `bundles/` 目录下（放到根目录会静默失效，见下文）
4. 代码里以模组内部名为前缀的文案键（如 `enemy-pause.paused`）都在语言包里定义了

另有 `tools/epverify`：离线验证，用真实游戏类构造最小环境（`Vars.state` + 目标 / 敌方 `TeamData`），
不用启动游戏。三个程序都靠“先把结论写死、再跑实际代码对照”的方式抓 bug。

| 程序 | 验证什么 |
| --- | --- |
| `VerifyEnemyTimers` | 计时目标与波次倒计时确实停住；且不会在暂停瞬间漏出“完成判定”（含只剩 0.5 tick 的边界）；客户端镜像按主机端读数冻结、越界读数被拉回 |
| `VerifyEnemySkip` | 只跳“此刻正在跑”的计时目标；挂在未完成父目标下的子目标不被误跳；菜单状态与无目标时安全退化 |
| `VerifyEnemyPause` | 联机同步：封包注册幂等与序列化往返；客户端收到广播后两个倒计时都停住、不判定完成；继续后恢复；主机端忽略自己的广播；客户端按键只提示不改状态 |

```bash
CP="Enemy Pause/libs/dependencies.jar:Enemy Pause/build/libs/EnemyPause.jar"   # Windows 下分隔符改成 ;
for V in VerifyEnemyTimers VerifyEnemySkip VerifyEnemyPause; do
  javac -encoding UTF-8 -implicit:none -sourcepath tools/epverify -cp "$CP" -d tools/epverify/out tools/epverify/$V.java
  java -Dfile.encoding=UTF-8 -cp "tools/epverify/out:$CP" $V
done
```

`VerifyEnemyPause` 用 `new Net(null)` + 反射改 `Net` 的私有字段 `active` / `server` 来伪造三种形态
（单机 `false,false` / 主机端 `true,true` / 客户端 `true,false`——`client()` 的定义是 `!server && active`），
所以不用真的开房就能把整条同步路径跑一遍。

> `-sourcepath` 必须显式指定：`dependencies.jar` 里同时打包了 `.java` 源码，
> 不指定的话 javac 会去隐式编译那些源码，报一堆找不到符号。

### 模组项目结构（`Enemy Pause/`）

```
build.gradle            构建脚本：本地 jar 依赖优先，离线即可编译
settings.gradle         项目名 EnemyPause（目录名有空格，产物名不能带空格）
gradle.properties       目标游戏版本 mindustryVersion=v160.1（脚本共用）
mod.hjson               模组元数据，必须打进 jar 根目录
src/enemypause/
    EnemyPauseMod.java      入口：注册 Y / U 两个按键、挂事件监听
    EnemyPause.java         状态机：生效范围判定、自动解除、跳过编排
    EnemyTimers.java        两类敌方倒计时的冻结（Y）
    EnemySkip.java          两类敌方倒计时的跳过（U）
assets/sprites/*.png    贴图；源码路径 assets/sprites/foo.png 会变成 jar 内的 sprites/foo.png
bundles/
    bundle.properties       默认（英文）语言包
    bundle_zh_CN.properties 简体中文语言包
libs/dependencies.jar   编译期依赖，不会被打进模组
build/libs/EnemyPause.jar   构建产物
```

### 常见改动

**本地化文件命名（很容易踩）**：文件名必须匹配 `bundle` + (locale 为空 ? "" : "_" + locale)。
游戏加载模组语言包的逻辑（`mindustry.mod.DataBundleLoader`）就是按这个拼出文件名，
再去 jar 的 `bundles/` 目录里找：

- `bundles/bundle.properties` —— 默认包，**所有语言缺键时的兜底**，等于英文
- `bundles/bundle_zh_CN.properties` —— 简体中文

所以**不要写 `bundle_en.properties`**：`en` 并不在语言链里（默认包的 locale 是空串），
这个文件永远不会被加载。之前 `ra2-controls` 里的 `bundle_en.properties` 就是这种情况——
死文件，不影响功能，但它的英文文案其实一直没生效。

打包方式也要注意：`from("bundles/")` 必须配 `into("bundles")`。Gradle 的 `from(dir)`
默认把**目录内容**铺到 jar 根目录，不加 `into` 语言包就会落到根目录，游戏静默读不到，
只表现为界面显示原始键名，光看日志查不出来。（`assets/` 相反，正需要落到 jar 根：
源码 `assets/sprites/foo.png` → jar 内 `sprites/foo.png`。）`check.sh` 现在会抓这个。

**改按键的默认值 / 名称**：改 `EnemyPauseMod` 里的 `DEFAULT_KEY`（默认 Y）/ `DEFAULT_SKIP_KEY`（默认 U），
或 `BIND_NAME` / `SKIP_BIND_NAME`（标识）。注意标识会拼成翻译键 `keybind.<标识>.name`，
改了要同步改两个语言包。按键所在的分类由 `BIND_CATEGORY` 决定，当前用的是游戏自带的 `general`，
所以设置里会显示已翻译好的「常规」；换成别的分类就得自己补 `category.<分类>.name`。

**换目标游戏版本**：改 `Enemy Pause/gradle.properties` 里的 `mindustryVersion`，
删掉 `libs/dependencies.jar`，再跑 `./build.sh` 会自动下载对应版本。

**依赖下载失败**：`build.sh` 先试
`https://github.com/Anuken/Mindustry/releases/download/<版本>/dependencies.jar`，
失败会自动改走 `api.github.com` 取资产。本机直连 github.com 经常报
`SSL_ERROR_SYSCALL`，这条兜底是必需的（api.github.com 通常可达）。

**Gradle 分发包**：wrapper 默认指向 `services.gradle.org`，它重定向到的 CDN 在国内经常超时，
所以 `gradle-wrapper.properties` 已改指**腾讯镜像**（实测 24 MB/s）。
只想下一次，缓存进 `tools/gradle-home` 之后就不再联网。

**游戏换位置**：只改 `config.sh`。
注意 Steam 版在 macOS 上把数据放在**应用包内部**的 `Contents/Resources/saves`，
不是 `~/Library/Application Support/Mindustry`；两个路径都列在 `config.sh` 里，
部署时两边都会覆盖，免得搞错在生效的是哪个。

**VS Code 配置**：`.vscode/settings.json` 里用的是绝对路径（Java 扩展启动语言服务器时
对 `${workspaceFolder}` 的替换不可靠），工作区挪位置要同步改。
推荐的 Java / Gradle 扩展在 `.vscode/extensions.json`。

### 游戏内验证

Windows 上手动构建 + 安装（游戏目录见上表）：

```powershell
cd "Enemy Pause"
$env:GRADLE_USER_HOME = "$PWD\..\tools\gradle-home"
.\gradlew.bat jar --offline
Copy-Item build\libs\EnemyPause.jar "E:\SteamLibrary\steamapps\common\Mindustry\saves\mods\" -Force
```

然后重启游戏（模组只在启动时加载）：

1. 开一局**战役**地图，日志里应出现 `[enemy-pause] loaded. Pause: y, Skip: u`
2. 按 `Y`：弹出「敌方倒计时已暂停」；再按 `Y` 弹出「已继续」。看 HUD 上的倒计时与
   「敌人来袭」这类目标计时是否真的定住不动
3. 按 `U`：弹出「敌方倒计时已跳过」。波次应立刻到来；目标计时应立刻完成
4. 日志里会有诊断行，可用来确认冻结接上了：

```
[enemy-pause] frozen: wave=14400 tick, timerObjectives=1
```

`timerObjectives` 为 0 说明这张图没有计时目标（正常）；若图上有「敌人来袭」却一直是 0，
那就是反射没拿到 `countup`，应能看到一条 warn。

### 注意事项

- 改完 `mod.hjson`、`assets/`、`bundles/` 要重新构建才会进 jar
- 模组**只在游戏启动时加载**，改完必须重启游戏
- 模组内部名 `enemy-pause` 决定两件事：贴图前缀（`assets/sprites/foo.png` 在游戏里用
  `enemy-pause-foo` 引用），以及文案键前缀的约定
- `Trigger` 是个**枚举**，监听要用 `Events.run(Trigger.xxx, runnable)`；
  `Events.on(...)` 是给类事件用的，套在 Trigger 上会收到所有触发器，是个静默的坑
- 想在设置里看到按键，分类必须能翻译出来（`category.<分类>.name`），否则界面会显示原始标识
- `minGameVersion` 是能运行的最低游戏版本，当前写 `"159"`
- v8 的 `mod.hjson` 仍然需要 `java: true` 才按 Java 模组处理（已从 `mindustry.mod.Mods` 的字段表确认）
- `EnemyWavePause.pause()` 会拒绝在非战役模式按下，只弹提示不生效——这是需求要求的范围
