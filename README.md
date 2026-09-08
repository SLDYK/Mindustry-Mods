# Mindustry 模组开发工作区

当前项目：**ra2-controls**（RA2 风格指挥操作 Java 模组），详见 `PLAN.md` / `RA2-CONTROLS.md` / `ra2-controls/README.md`。

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

| 任务 | 说明 |
|------|------|
| RA2: 构建 (ra2-controls) | gradle 构建，输出 `ra2-controls/build/libs/ra2-controls.jar`（macOS 走 pack.sh，Windows 自动走 pack.ps1） |
| RA2: 构建并安装 | 构建并安装到游戏 mods 目录（自动探测：Windows Steam 版 = 游戏目录 `saves/mods`；macOS = `~/Library/Application Support/Mindustry/mods`） |
