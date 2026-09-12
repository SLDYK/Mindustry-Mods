#!/usr/bin/env bash
# 把构建好的模组 jar 复制到 Mindustry 的 mods 目录
#
# 用法: ./deploy.sh ["项目目录"]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/config.sh"

PROJECT="Enemy Pause"
if [ $# -gt 0 ] && [ -d "$ROOT/$1" ]; then
  PROJECT="$1"
fi

# 取最新的产物，避免硬编码 jar 名
JAR="$(ls -t "$ROOT/$PROJECT"/build/libs/*.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ]; then
  echo "错误：没找到构建产物，先执行 ./build.sh" >&2
  exit 1
fi

deployed=0
for data in "${MINDUSTRY_DATA_DIRS[@]}"; do
  [ -d "$data" ] || continue
  mkdir -p "$data/mods"
  cp -f "$JAR" "$data/mods/"
  echo "==> 已部署: $data/mods/$(basename "$JAR")"
  deployed=1
done

if [ "$deployed" -eq 0 ]; then
  echo "错误：没找到游戏数据目录，请手动复制 $JAR" >&2
  exit 1
fi

echo "==> 模组只在游戏启动时加载，重启游戏后生效"
