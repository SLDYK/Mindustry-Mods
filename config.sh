# 环境路径配置，被 build.sh / deploy.sh / run.sh 引用
# 以后把游戏挪到别处，只改这个文件就行

# Steam 版 Mindustry 的应用包路径
MINDUSTRY_APP="/Volumes/ORICO/SteamLibrary/steamapps/common/Mindustry/Mindustry.app"

# 候选的游戏数据目录
# 注意：Steam 版在 macOS 上把数据放在应用包内部（有 settings.bin 的那个才是真在用的）
# 第二个是普通版/旧版的位置，两个都写的话部署时会同时覆盖，避免搞错
MINDUSTRY_DATA_DIRS=(
  "$MINDUSTRY_APP/Contents/Resources/saves"
  "$HOME/Library/Application Support/Mindustry"
)
