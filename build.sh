#!/usr/bin/env bash
# 构建 Mindustry 模组
#
# 用法:
#   ./build.sh                    构建 Enemy Pause
#   ./build.sh "Enemy Pause"      指定项目目录
#   ./build.sh "Enemy Pause" clean jar   把参数透传给 gradle
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 默认项目
PROJECT="Enemy Pause"
if [ $# -gt 0 ] && [ -d "$ROOT/$1" ]; then
  PROJECT="$1"
  shift
fi

# ---- 工具链全部留在本文件夹内，完全不碰系统里那套 Java ----
export JAVA_HOME="$ROOT/tools/jdk/Contents/Home"
export GRADLE_USER_HOME="$ROOT/tools/gradle-home"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "错误：找不到内置 JDK（$JAVA_HOME）" >&2
  exit 1
fi

cd "$ROOT/$PROJECT"

# ---- 编译依赖缺失时自动补下载（只需要联网一次） ----
VERSION="$(grep -E '^mindustryVersion=' gradle.properties | tail -1 | cut -d= -f2 | tr -d '[:space:]')"
if [ ! -f libs/dependencies.jar ]; then
  echo "==> 下载编译依赖 dependencies.jar ($VERSION)"
  mkdir -p libs

  # github.com 在国内经常连不上（SSL_ERROR_SYSCALL），失败就改走 api.github.com
  if ! curl -fL --connect-timeout 10 --retry 2 -o libs/dependencies.jar.part \
      "https://github.com/Anuken/Mindustry/releases/download/$VERSION/dependencies.jar"; then
    echo "==> 直连 github.com 失败，改用 api.github.com 取资产"
    ASSET_ID="$(curl -fsSL -m 25 -H 'Accept: application/vnd.github+json' \
      "https://api.github.com/repos/Anuken/Mindustry/releases/tags/$VERSION" \
      | python3 -c 'import sys,json;print(next(a["id"] for a in json.load(sys.stdin)["assets"] if a["name"]=="dependencies.jar"))')"
    curl -fL --retry 2 -o libs/dependencies.jar.part \
      -H 'Accept: application/octet-stream' \
      "https://api.github.com/repos/Anuken/Mindustry/releases/assets/$ASSET_ID"
  fi

  mv -f libs/dependencies.jar.part libs/dependencies.jar
fi

echo "==> 开始构建：$PROJECT"
if [ $# -gt 0 ]; then
  exec ./gradlew "$@"
else
  exec ./gradlew build
fi
