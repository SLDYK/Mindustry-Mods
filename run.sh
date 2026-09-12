#!/usr/bin/env bash
# 构建 + 部署 + 启动游戏 + 跟随日志，一条龙开发循环
#
# 用法: ./run.sh ["项目目录"]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/config.sh"

PROJECT="Enemy Pause"
if [ $# -gt 0 ] && [ -d "$ROOT/$1" ]; then
  PROJECT="$1"
fi

"$ROOT/build.sh" "$PROJECT"
"$ROOT/deploy.sh" "$PROJECT"

# 真在使用的数据目录 = 第一个存在的候选
LOG=""
for data in "${MINDUSTRY_DATA_DIRS[@]}"; do
  if [ -f "$data/settings.bin" ]; then
    LOG="$data/last_log.txt"
    break
  fi
done

if [ ! -d "$MINDUSTRY_APP" ]; then
  echo "错误：找不到游戏 $MINDUSTRY_APP" >&2
  exit 1
fi

echo "==> 启动游戏"
open "$MINDUSTRY_APP"

if [ -n "$LOG" ]; then
  echo "==> 跟随日志（Ctrl+C 退出，不影响游戏）: $LOG"
  # 等日志文件被这一轮启动刷新
  until [ -f "$LOG" ]; do :; done
  tail -f "$LOG"
else
  echo "没定位到日志文件，请手动查看游戏数据目录里的 last_log.txt"
fi
