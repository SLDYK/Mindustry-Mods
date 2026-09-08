#!/bin/bash
# Mindustry 模组打包 & 安装脚本（ra2-controls 专用，macOS/Linux 版）
# 用法:
#   ./tools/pack.sh              # 构建 ra2-controls（gradle jar）
#   ./tools/pack.sh --install    # 构建并安装到游戏 mods 目录
#   ./tools/pack.sh --zip        # 构建并输出 zip 到 dist/
set -euo pipefail

MOD="ra2-controls"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
INSTALL=0
ZIP=0

for arg in "$@"; do
  case "$arg" in
    --install) INSTALL=1 ;;
    --zip) ZIP=1 ;;
    *) echo "[!] 未知参数: $arg（支持 --install / --zip）"; exit 1 ;;
  esac
done

PROJECT="$ROOT/$MOD"

# ---- Java 模组：gradle 构建 ----
if [ ! -f "$PROJECT/build.gradle" ]; then
  echo "[!] 未找到模组工程: $PROJECT" >&2
  exit 1
fi

cd "$PROJECT"
./gradlew jar
JAR="$PROJECT/build/libs/$MOD.jar"
if [ ! -f "$JAR" ]; then
  echo "[!] 未找到构建产物: $JAR" >&2
  exit 1
fi
echo "[OK] 已构建: $JAR"

if [ "$ZIP" = "1" ]; then
  mkdir -p "$DIST"
  ditto -c -k --sequesterRsrc --keepParent "$JAR" "$DIST/$MOD.zip" 2>/dev/null \
    || (cd "$PROJECT/build/libs" && zip -q "$DIST/$MOD.zip" "$MOD.jar")
  echo "[OK] 已打包: $DIST/$MOD.zip"
fi

if [ "$INSTALL" = "1" ]; then
  # Steam 版数据目录：macOS = ~/Library/Application Support/Mindustry/mods
  MODS_DIR="$HOME/Library/Application Support/Mindustry/mods"
  if [ ! -d "$MODS_DIR" ]; then
    mkdir -p "$MODS_DIR"
    echo "[i] 已创建 mods 目录: $MODS_DIR"
  fi
  cp -f "$JAR" "$MODS_DIR/$MOD.jar"
  echo "[OK] 已安装: $MODS_DIR/$MOD.jar"
  echo "启动游戏 -> 设置 -> 模组 应显示 RA2 Controls。"
fi
