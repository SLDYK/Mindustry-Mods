# Mindustry 模组开发工作区

本工作区原先是两个独立的 GitHub 仓库，2026-09-12 合并为一个仓库（历史均已保留）：

| 模组 | 目录 | 说明 | 主要开发机 |
|------|------|------|------------|
| **ra2-controls** | `ra2-controls/` | RA2 风格指挥操作 Java 模组，见 `RA2-CONTROLS.md` / `PLAN.md` / `ra2-controls/README.md` | Windows |
| **Enemy Pause** | `Enemy Pause/` | 战役模式下按键暂停 / 继续敌方侧的各种倒计时（进攻 / 扩建 / 生产），见下文 | macOS |

两个模组相互独立（各自的 gradle 工程、各自的构建脚本），互不影响：

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

游戏 `desktop.jar` 与 mods 目录自动跨平台探测（Windows Steam 库盘符 / macOS `Mindustry.app` / Linux）；找不到时设环境变量 `MINDUSTRY_JAR` 指向 `desktop.jar`。

## VS Code 任务

| 任务 | 平台 | 说明 |
|------|------|------|
| RA2: 构建 (ra2-controls) | 双平台 | gradle 构建，输出 `ra2-controls/build/libs/ra2-controls.jar`；Windows 走 `pack.ps1`，macOS/Linux 走 `pack.sh` |
| RA2: 构建并安装 | 双平台 | 构建并安装到游戏 mods 目录（自动探测：Windows Steam = 游戏目录 `saves/mods`；macOS = `~/Library/Application Support/Mindustry/mods`） |
| Mindustry: 构建模组 | macOS | 跑 `build.sh`（默认构建 `Enemy Pause/`），同时是默认构建任务（`Cmd+Shift+B`） |
| Mindustry: 构建并部署 | macOS | `build.sh && deploy.sh` |
| Mindustry: 校验产物 | macOS | `check.sh` 离线校验产物 |

> `RA2:` 任务用 `windows` 覆盖做了分平台：Windows 上走 `tools/pack.ps1`，macOS/Linux 上走 `tools/pack.sh`。
> `Mindustry:` 三个任务目前是 macOS 专属（依赖 `build.sh` 等脚本和 `tools/jdk` 内置工具链）。
>
> `.vscode/settings.json` 里 Java 工具链的绝对路径属于 macOS 开发机，已按注释保留，Windows 上使用系统 JDK。

---

## Mindustry 模组开发环境

全自包含：工具链都在这个文件夹里，**不依赖也不改动系统里那套 Java**（那套是给 Minecraft 用的）。
编译用的 JDK 单独下在 `tools/jdk`，Gradle 缓存也留在 `tools/gradle-home`，不会写 `~/.gradle`。

### 环境概况

| 项目 | 值 |
| --- | --- |
| 游戏 | **Mindustry v160.1**（Steam 版，内置 JRE 25） |
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

`Enemy Pause/` 里的模组做一件事：**战役模式下按一个键，暂停 / 继续敌方侧的倒计时。**

冻的是「下一件事什么时候发生」，**不是敌人本身**：已经在场的敌方单位照常行动开火，
敌方工厂照常生产、照常激活，玩家侧也不受影响。

| 项 | 值 |
| --- | --- |
| 默认按键 | **Y**（在 设置 → 按键 的「常规」分组里可改） |
| 生效范围 | 只在战役模式，且只对单机有效；`rules.waves` 或 `rules.attackMode` 至少开着一个 |
| 游戏内反馈 | 暂停 / 继续时屏幕下方弹一条提示 |

之所以选 `Y`：Mindustry 自带的绑定已经占掉了 a~z 里除 `i k l o u y` 以外的所有字母，
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
- 多人游戏无效：这些状态都由服务端说了算，客户端改写会被同步覆盖。
- **不覆盖**：已经在场上的敌方单位、敌方工厂生产与激活、敌方基地 AI 的扩建/调度节奏，
  以及行星图上「敌方入侵已占领星区」的回合倒计时。

> 演进记录：v0.1 只冻波次；v0.2 误把「敌方行为」也冻了（工厂激活、生产进度、基地 AI 计时器）；
> v0.3 按需求移除行为类；v0.4 才找到真正该冻的第 2 项——地图目标里的计时目标。

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

另有 `tools/epverify`：针对 `EnemyTimers` 的离线验证，用真实游戏类构造最小环境（`Vars.state` + 敌方
`TeamData`），校验三件事，不用启动游戏：

1. 反射能拿到 `BaseBuilderAI.timer` / `RtsAI.timer`
2. 暂停期间波次倒计时与 AI 计时器是否真的停住（含对照组：不暂停时 60 tick 会正常触发）
3. 恢复后 AI 计时器是否接着剩余时间走，而不是重新数满一轮

```bash
CP="Enemy Pause/libs/dependencies.jar:Enemy Pause/build/libs/EnemyPause.jar"   # Windows 下分隔符改成 ;
javac -encoding UTF-8 -implicit:none -sourcepath tools/epverify -cp "$CP" \
  -d tools/epverify/out tools/epverify/VerifyEnemyTimers.java
java -Dfile.encoding=UTF-8 -cp "tools/epverify/out:$CP" VerifyEnemyTimers
```

> `-sourcepath` 必须显式指定：`dependencies.jar` 里同时打包了 `.java` 源码，
> 不指定的话 javac 会去隐式编译那些源码，报一堆找不到符号。

### 模组项目结构（`Enemy Pause/`）

```
build.gradle            构建脚本：本地 jar 依赖优先，离线即可编译
settings.gradle         项目名 EnemyPause（目录名有空格，产物名不能带空格）
gradle.properties       目标游戏版本 mindustryVersion=v160.1（脚本共用）
mod.hjson               模组元数据，必须打进 jar 根目录
src/enemypause/
    EnemyPauseMod.java      入口：注册按键、挂事件监听
    EnemyPause.java         暂停状态机：生效范围判定、自动解除
    EnemyTimers.java        四类敌方倒计时的冻结 / 还原
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

**改按键的默认值 / 名称**：改 `EnemyPauseMod` 里的 `DEFAULT_KEY`（按键）或 `BIND_NAME`
（标识）。注意 `BIND_NAME` 会拼成翻译键 `keybind.<BIND_NAME>.name`，改了要同步改两个语言包。
按键所在的分类由 `BIND_CATEGORY` 决定，当前用的是游戏自带的 `general`，
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

1. `./run.sh`（构建 + 部署 + 启动游戏 + 跟日志）
2. 开一局**战役**地图，日志里应出现 `Loaded mod 'enemy-pause'`
3. 按 `Y`：弹出「敌方倒计时已暂停（进攻 / 扩建 / 生产）」；再按 `Y` 弹出「敌方倒计时已继续」
4. 看倒计时有没有真的停住：波次读数应定住不动；有敌方核心的图可以看敌方是否停止扩建/出单位
   ——打不开游戏时用 `./check.sh` 先排掉打包/元数据问题

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
