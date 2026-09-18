# erekir-items — 埃里克尔内容模组

模组是个**容器工程**：以后陆续往里加埃里克尔的建筑 / 物品，每个内容一项。
当前有两项：可调电力节点、资源源。

## 模组级信息

| 项 | 值 |
| --- | --- |
| 模组内部名 | `erekir-items`（内容名前缀 / 贴图前缀） |
| 主类 | `erekiritems.ErekirItemsMod` |
| 游戏内模组名 | 埃里克尔物品（`mod.hjson` 的 `displayName`） |
| 公共属性 | 都只在**埃里克尔**出现、**开局即可建造**（`alwaysUnlocked`，不占科技树）；配置走游戏自带的配置同步（单机/存档/蓝图/联机都通） |

## 当前内容

### 可调电力节点（`tunable-node`）

给行星**埃里克尔**加一个电力节点：接进电网后往电网里送电，**送多少由玩家自己填**。

| 项 | 值 |
| --- | --- |
| 方块名（游戏内） | 可调电力节点 / Tunable Power Node |
| 内容名 | `erekir-items-tunable-node` |
| 建造位置 | 电力分类，**仅埃里克尔**，**开局即可建造**（不需要研究） |
| 造价 | 铍 20 + 硅 10 |
| 输出范围 | 0 ~ 1000000 电力/秒（默认 1000） |
| 连接方式 | 与原版光束节点一致：向四个正方向自动连接 12 格内第一个带电网的建筑 |

### 资源源（`resource-source`）

凭空产出**相织物 + 水**的资源源，不需要输入，也不用采矿。

| 项 | 值 |
| --- | --- |
| 方块名（游戏内） | 资源源 / Resource Source |
| 内容名 | `erekir-items-resource-source` |
| 建造位置 | 运输分类，**仅埃里克尔**，**开局即可建造** |
| 造价 | 铍 40 + 硅 20 |
| 产出 | 相织物（默认 10 个/秒，可调 0 ~ 10000）+ 水（默认 100 单位/秒，可调 0 ~ 10000）|
| 输出方式 | 物品用传送带接走，水用管道接走；它只出不进 |

## 游戏内怎么用

### 可调电力节点

1. 在埃里克尔的**电力**分类里找到「可调电力节点」，放在电网附近。
   - 它会像原版光束节点那样自动拉出光束，接到四个正方向 12 格内第一个带电网的建筑上。
2. **点一下方块**打开设置面板：在输入框里填具体数字后按「应用」，或者直接点快捷按钮（`0 / 100 / 1k / 10k / 100k / 1M`）。
3. 面板里的数值就是**每秒**电量，和 HUD 上的 `/s` 是同一套单位。
4. **双击**已选中的节点 = 恢复默认的 1000/秒。
5. 方块上方的状态条显示这个节点当前的输出。

### 资源源

1. 在埃里克尔的**运输**分类里找到「资源源」，把它旁边接上传送带与管道。
2. **点一下方块**打开设置面板：上面一组是相织物速率（输入框 + `0 / 1 / 10 / 100 / 1k` 快捷按钮），
   下面一组是水速率（输入框 + `0 / 10 / 100 / 1k / 10k` 快捷按钮），填好后按「应用」。
   - 快捷按钮点一下立即生效，不必再按「应用」。
3. **双击**方块 = 两种速率都恢复到默认值（10 个/秒 + 100 单位/秒）。
4. 设置的值像其他可配置方块一样会记住：下一个新放的资源源直接沿用。

它**只产出、不消耗**：没被接走的部分就堆在自己的仓里（200 个物品 / 200 单位液体），堆满后自动停产；
把速率改成 0 只是不再补货，仓里剩下的照样能送出去。

联机时两个方块的配置都走游戏自己的配置同步流程：谁改都会同步给主机与所有客户端，和原版可配置方块一样。

## 实现要点（改代码前先看）

| # | 事实 | 说明 |
| --- | --- | --- |
| 1 | 基类是 `BeamNode` 而不是 `PowerNode` | 埃里克尔的节点是光束节点（四向自动连接）。**不能**用 `PowerNode`：它自己占用了 `config(Integer.class)` 做手动连线，会和本模组的输出配置撞车。 |
| 2 | 纯电源 = `outputsPower = true; consumesPower = false;` | `PowerGraph.add()` 按这两个标志把建筑分到 producers / consumers / batteries。只有 `outputsPower && !consumesPower` 才是「只发电」。 |
| 3 | 输出走 `Building.getPowerProduction()` | `PowerGraph.update()` 每帧对 producers 调用它，返回值是**每 tick** 电量，再乘 `delta()` 累加。 |
| 4 | 内部单位是每 tick，界面才是每秒 | 全游戏的 `powerProduction` 都是 per-tick，UI 统一 ×60 显示成 `/s`。所以玩家填的每秒值要 `/60f` 再返回。 |
| 5 | 设置值走游戏的配置流程 | `config(Integer.class, ...)` + `configClear(...)`，再覆写 `config()`。`Building.configure(value)` → `Call.tileConfig`（`@Remote(called = Loc.both, forward = true)`）→ `configured()` 分发到上面的处理器。单机立即生效，联机自动同步，存档/蓝图都跟着走。 |
| 6 | `saveConfig = true` 的含义 | 放下新方块时沿用 `block.lastConfig`（上一次设的值），并且存档会带上配置。 |
| 7 | `clearOnDoubleTap = true` | 双击已选中的方块 → `configure(null)` → 走 `configClear` 回到默认值。 |
| 8 | 只在埃里克尔 | `shownPlanets.add(Planets.erekir)`：决定数据库分类、地图生成器、编辑器筛选等。 |
| 9 | 开局可用 | `alwaysUnlocked = true`，不挂科技树，不需要研究。想改成研究解锁就把这行删掉，再在 `loadContent()` 里往埃里克尔科技树挂 `TechNode`。 |
| 10 | 一个方块可以有多种配置值 | `TypeIO.writeObject` 只认基本类型 / 内容 / `Point2` / `IntSeq` 等，所以资源源的「两个速率」打包进 `Point2`（x = 物品，y = 液体）一次性提交，`lastConfig` 也就同时记住了两个值。 |
| 11 | **液体输出不能照抄 `dumpLiquid()`** | 它是靠液位比例差推动的（只有自己液位比例高于对方才送），想精确限速就得灌满储罐，一灌满又会一次推掉大半罐。资源源改成按存量逐个邻居 `transferLiquid()`，送多少自己说了算。 |
| 12 | 物品输出可以直接用 `dump(Item)` | 它是按「我有的 + 对方能收的」转让，送多少取决于我给多少，所以攒在 `items` 里再 dump 就能限流。 |
| 13 | 容量同时是每帧产出的天花板 | `itemCapacity` / `liquidCapacity` 必须 ≥ `maxRate / 60`，否则高速率会被静默截断（资源源取 200，上限速率每帧约 167）。 |

## 贴图

贴图由脚本生成，不需要美术工具（两个方块的贴图一起生成）：

```powershell
powershell -ExecutionPolicy Bypass -File tools\gen-sprites.ps1
```

脚本里一个方块一个 `Draw-<名字>` 函数，加新方块时照抄一份改改就是。

> **命名陷阱（踩过一次）**：游戏的模组贴图打包规则是
> `sprites/<文件名>.png` → 图集里的区域名 `<模组内部名>-<文件名>`
> （见 `Mods.packSprites()`；只有当文件名第一个 `-` 之后的部分已经以 `<模组名>-` 开头时才不再加前缀）。
> 所以文件必须叫 **`tunable-node.png`**，不能叫 `erekir-items-tunable-node.png` —— 后者会被拼成
> `erekir-items-erekir-items-tunable-node`，方块就找不到自己的贴图了。
> 离线校验里的 `VerifyModJar.findSpriteForRegion()` 复刻了这条规则，改文件名后跑一下校验即可确认。

光束贴图不用自己做：`BeamNode` 的 `laser` / `laserEnd` 字段带 `@Load(value = "@-beam", fallback = "power-beam")`，
生成的加载代码对 `instanceof BeamNode` 生效（`mindustry.gen.ContentRegions`），找不到自己的 `-beam` 就自动用原版贴图。

## 加一个新建筑 / 物品

模组是容器工程，加内容的套路固定的：

1. 在 `src/erekiritems/` 下新建方块类（继承 `PowerBlock` / `GenericCrafter` 等），类名即内容名后缀。
2. 在 `ErekirItemsMod.loadContent()` 里 `new` 出来，赋给一个 `public static` 字段。
   - 内容创建**必须**在这个方法里：此时原版内容与两个行星都已就绪。
   - 想让它在建造菜单里出现，别忘了 `requirements(Category.xxx, ItemStack.with(...))`。
3. 丢一张 `assets/sprites/<方块名>.png`（**不要**带模组名前缀，游戏会自动加），
   并在 `tools/gen-sprites.ps1` 里加一个 `Draw-<名字>` 函数把它生成出来（可选，手画也行）。
4. 两份语言包都补上：`block.erekir-items-<方块名>.name` / `.description`（可选 `.details`），以及自己代码里用到的键。
   - 键名约定：内容自己的文案以**完整内容名**开头（`erekir-items-<方块名>.xxx`），跨内容共用的界面词不带内容前缀（如 `erekir-items.apply`）。
5. 想加科技树节点：删掉 `alwaysUnlocked`，在 `loadContent()` 里往埃里克尔树上挂 `TechNode`（参考 `mindustry.content.ErekirTechTree`）。
6. 跑 `tools\pack.ps1 -Mod "erekir-items"` + `tools\eiverify\verify.ps1`。

> 新增内容后记得把方块登记到 `tools/eiverify/VerifyModJar.java` 的 `CONTENTS` 表里
> —— 它就是「内容清单 + 代码用到的语言包键清单」，漏写文案或贴图会被校验拦住。
> 方块自己的数值/存档逻辑建议再写一个 `Verify<方块名>.java`（照着 `VerifyResourceSource` 抄）。

## 构建 / 安装

```powershell
# 构建（产物 erekir-items/build/libs/erekir-items.jar）
tools\pack.ps1 -Mod "erekir-items"

# 构建并安装到游戏 mods 目录
tools\pack.ps1 -Mod "erekir-items" -Install
```

编译依赖按顺序探测：`MINDUSTRY_JAR` 环境变量 → 本模组 `libs/dependencies.jar` → 本机游戏 `jre/desktop.jar` → 联网下载官方 v159.7 依赖。
本机（Steam）用的是 `E:\SteamLibrary\steamapps\common\Mindustry\jre\desktop.jar`，`mod.hjson` 里 `minGameVersion: "160"` 就是对着它定的（实测 `Version.build = 160`，即 `steam build 160.4`）。

## 离线校验（不开游戏）

```powershell
tools\eiverify\verify.ps1          # 见 VS Code 任务「Erekir Items: 校验产物」
```

三个校验器共 131 项，用的是**真实的无头内容加载流程**（`ContentLoader.createBaseContent()`）
把方块真造出来，不启动游戏：

| 校验器 | 管什么 |
| --- | --- |
| `VerifyModJar` | 模组级：`mod.hjson` 位置与字段、主类在包里、**每个内容**的贴图文件名能拼出正确的内容名、两套语言包都定义了该内容用到的每个键（含 `{0}..` 占位符） |
| `VerifyTunableNode` | 可调电力节点：方块标志、配置往返（含钳制与 `configClear`）、每 tick 发电换算、存档 IO |
| `VerifyResourceSource` | 资源源：方块标志、两个速率辅助函数、配置往返、**实际跑生产循环**（用固定 `Time.delta` 跑 60 帧验算每秒产出）、缓冲上限、速率为 0 的行为、存档 IO |

共用的无头环境（`Vars.headless` / `Vars.state` / `ContentLoader` / 从 jar 里读语言包）在
`VerifySupport.java` 里，三个校验器各自是独立进程。

> 踩过的坑：`Vars.state` 必须初始化 —— `Building.produced()` 等会读 `state.rules`，
> 不初始化就会在跑生产循环时 NPE。

## 文件结构

```
erekir-items/
├─ mod.hjson                          # 模组元信息（name 决定内容名前缀与贴图前缀）
├─ build.gradle / settings.gradle     # 依赖探测 + 打包规则（assets/ 内容铺到 jar 根目录）
├─ src/erekiritems/
│   ├─ ErekirItemsMod.java            # 模组入口，loadContent() 里注册全部内容
│   ├─ TunableNode.java               # 可调电力节点（配置面板、发电、存档）
│   └─ ResourceSource.java            # 资源源（双速率配置面板、物品/液体输出、存档）
└─ assets/
    ├─ sprites/tunable-node.png       # 贴图（tools/gen-sprites.ps1 生成）
    ├─ sprites/resource-source.png
    └─ bundles/                       # bundle.properties（英文默认）+ bundle_zh_CN.properties
```

> 语言包必须是 **UTF-8**（游戏就按 UTF-8 读，和原版 `bundles/bundle_zh_CN.properties` 一致），
> 默认语言包必须叫 `bundle.properties` —— `bundle_en.properties` 永远不会被加载。
