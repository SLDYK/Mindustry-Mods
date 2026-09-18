# erekir-items — 埃里克尔内容模组

模组是个**容器工程**：以后陆续往里加埃里克尔的建筑 / 物品，每个内容一项。
当前只有一项。

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

| 模组级信息 | 值 |
| --- | --- |
| 模组内部名 | `erekir-items`（内容名前缀 / 贴图前缀） |
| 主类 | `erekiritems.ErekirItemsMod` |
| 游戏内模组名 | 埃里克尔物品（`mod.hjson` 的 `displayName`） |

## 游戏内怎么用

1. 在埃里克尔的**电力**分类里找到「可调电力节点」，放在电网附近。
   - 它会像原版光束节点那样自动拉出光束，接到四个正方向 12 格内第一个带电网的建筑上。
2. **点一下方块**打开设置面板：
   - 在输入框里填具体数字，按「应用」；
   - 或者直接点快捷按钮（`0 / 100 / 1k / 10k / 100k / 1M`）。
3. 面板里的数值就是**每秒**电量，和 HUD 上的 `/s` 是同一套单位。
4. **双击**已选中的节点 = 恢复默认的 1000/秒。
5. 方块上方的状态条显示这个节点当前的输出；放下去过的值会被记住，下一个新节点直接沿用（改完记得看上一行的条）。

联机时配置走游戏自己的配置同步流程：谁改都会同步给主机与所有客户端，和原版可配置方块一样。

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
| 10 | `version()` 用默认值 | 本模组只有一种方块状态，没有历史存档要兼容，所以不覆写 `version()`。 |

## 贴图

贴图由脚本生成，不需要美术工具：

```powershell
powershell -ExecutionPolicy Bypass -File tools\gen-tunable-node-sprite.ps1
```

> **命名陷阱（踩过一次）**：游戏的模组贴图打包规则是
> `sprites/<文件名>.png` → 图集里的区域名 `<模组内部名>-<文件名>`
> （见 `Mods.packSprites()`；只有当文件名第一个 `-` 之后的部分已经以 `<模组名>-` 开头时才不再加前缀）。
> 所以文件必须叫 **`tunable-node.png`**，不能叫 `erekir-items-tunable-node.png` —— 后者会被拼成
> `erekir-items-erekir-items-tunable-node`，方块就找不到自己的贴图了。
> 离线校验里的 `findSpriteForRegion()` 复刻了这条规则，改文件名后跑一下校验即可确认。

光束贴图不用自己做：`BeamNode` 的 `laser` / `laserEnd` 字段带 `@Load(value = "@-beam", fallback = "power-beam")`，
生成的加载代码对 `instanceof BeamNode` 生效（`mindustry.gen.ContentRegions`），找不到自己的 `-beam` 就自动用原版贴图。

## 加一个新建筑 / 物品

模组是容器工程，加内容的套路固定的：

1. 在 `src/erekiritems/` 下新建方块类（继承 `PowerBlock` / `GenericCrafter` 等），类名即内容名后缀。
2. 在 `ErekirItemsMod.loadContent()` 里 `new` 出来，赋给一个 `public static` 字段。
   - 内容创建**必须**在这个方法里：此时原版内容与两个行星都已就绪。
   - 想让它在建造菜单里出现，别忘了 `requirements(Category.xxx, ItemStack.with(...))`。
3. 丢一张 `assets/sprites/<方块名>.png`（**不要**带模组名前缀，游戏会自动加）。
4. 两份语言包都补上：`block.erekir-items-<方块名>.name` / `.description`（可选 `.details`），以及自己代码里用到的键。
5. 想加科技树节点：删掉 `alwaysUnlocked`，在 `loadContent()` 里往埃里克尔树上挂 `TechNode`（参考 `mindustry.content.ErekirTechTree`）。
6. 跑 `tools\pack.ps1 -Mod "erekir-items"` + `tools\eiverify\verify.ps1`。

> 新增内容后建议同步扩充 `tools/eiverify/VerifyTunableNode.java` 里的 `required[]` 数组
> —— 它就是「代码用到的语言包键清单」，漏写文案会被校验拦住。

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

`tools/eiverify/VerifyTunableNode.java` 会用**真实的无头内容加载流程**（`ContentLoader.createBaseContent()`）把方块创建出来，校验 62 项：

- jar 结构：`mod.hjson` 在根目录、主类在包里、贴图文件名能解析出正确的内容名、中英两份语言包都存在且定义了代码里用到的每一个键（含 `{0}/{1}` 占位符）；
- 方块属性：`alwaysUnlocked`、`shownPlanets == {erekir}`、`outputsPower && !consumesPower`、`configurable/saveConfig/clearOnDoubleTap`、分类与可见性、造价、配置处理器已注册；
- 数值逻辑：默认值、0 与上限的钳制、`configClear(null)` 回默认值、`getPowerProduction()` 的每 tick 换算（6000/秒 == 每 tick 100）、`enabled = false` 时不发电、快捷按钮文案；
- 存档 IO：`write()` → `read()` 往返后设置值不变。

## 文件结构

```
erekir-items/
├─ mod.hjson                          # 模组元信息（name 决定内容名前缀与贴图前缀）
├─ build.gradle / settings.gradle     # 依赖探测 + 打包规则（assets/ 内容铺到 jar 根目录）
├─ src/erekiritems/
│   ├─ ErekirItemsMod.java            # 模组入口，loadContent() 里注册方块
│   └─ TunableNode.java               # 方块 + TunableNodeBuild（配置界面、发电、存档）
└─ assets/
    ├─ sprites/tunable-node.png       # 方块贴图（脚本生成）
    └─ bundles/                       # bundle.properties（英文默认）+ bundle_zh_CN.properties
```

> 语言包必须是 **UTF-8**（游戏就按 UTF-8 读，和原版 `bundles/bundle_zh_CN.properties` 一致），
> 默认语言包必须叫 `bundle.properties` —— `bundle_en.properties` 永远不会被加载。
