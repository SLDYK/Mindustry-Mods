#!/usr/bin/env bash
# 离线校验构建产物：mod.hjson 语法 / 必需字段 / 主类能否被加载
#
# 改完 mod.hjson 或换主类后先跑这个，不用启动游戏就能发现最常见的两类错误。
# 用法: ./check.sh ["项目目录"]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export JAVA_HOME="$ROOT/tools/jdk/Contents/Home"
export GRADLE_USER_HOME="$ROOT/tools/gradle-home"
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT="Enemy Pause"
if [ $# -gt 0 ] && [ -d "$ROOT/$1" ]; then
  PROJECT="$1"
fi

DEPS="$ROOT/$PROJECT/libs/dependencies.jar"
JAR="$(ls -t "$ROOT/$PROJECT"/build/libs/*.jar 2>/dev/null | head -1 || true)"

if [ -z "$JAR" ] || [ ! -f "$DEPS" ]; then
  echo "错误：缺少构建产物或编译依赖，先执行 ./build.sh" >&2
  exit 1
fi

SRC="$ROOT/tools/modcheck"
OUT="${TMPDIR:-/tmp}/mindustry-modcheck-classes"
mkdir -p "$OUT"

# 源码没变就不重复编译，避免每次都刷一堆 javac 提示
if [ ! -f "$OUT/CheckMod.class" ] || [ "$SRC/CheckMod.java" -nt "$OUT/CheckMod.class" ]; then
  javac -encoding UTF-8 -cp "$DEPS" -d "$OUT" "$SRC/CheckMod.java"
fi

echo "==> 校验: $(basename "$JAR")"
java -Dfile.encoding=UTF-8 -cp "$OUT:$DEPS" CheckMod "$JAR" "$DEPS"
