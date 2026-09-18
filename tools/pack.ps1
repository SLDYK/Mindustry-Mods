# Mindustry 模组打包 & 安装脚本（两个模组通用）
# 用法:
#   .\tools\pack.ps1                                        # 构建 ra2-controls（gradle jar）
#   .\tools\pack.ps1 -Install                               # 构建并安装到游戏 mods 目录
#   .\tools\pack.ps1 -Zip                                   # 构建并输出 zip 到 dist/
#   .\tools\pack.ps1 -Mod "Enemy Pause" -Install -Desktop    # 构建 Enemy Pause，装到游戏并拷一份到桌面
param(
    [string]$Mod = "ra2-controls",
    [switch]$Install,
    [switch]$Zip,
    [switch]$Desktop
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot   # 仓库根目录
$dist = Join-Path $root "dist"

function Get-ModsDir {
    # Steam 版数据目录 = 游戏目录\saves\（settings/log/mods 都在游戏目录内）；
    # 游戏目录自动探测；非 Steam 安装回退 %APPDATA%\Mindustry\mods。
    $gameDir = $null
    foreach ($cand in @("E:\SteamLibrary\steamapps\common\Mindustry")) {
        if (Test-Path (Join-Path $cand "saves\mods")) { $gameDir = $cand; break }
    }
    if ($gameDir) { return Join-Path $gameDir "saves\mods" }
    return Join-Path $env:APPDATA "Mindustry\mods"
}

function Install-ToGameMods([string]$src, [string]$name) {
    $modsDir = Get-ModsDir
    if (-not (Test-Path $modsDir)) {
        New-Item -ItemType Directory -Path $modsDir | Out-Null
        Write-Host "[i] 已创建 mods 目录: $modsDir"
    }
    $destJar = Join-Path $modsDir "$name.jar"
    if (Test-Path $destJar) { Remove-Item $destJar -Force }
    Copy-Item $src $destJar
    Write-Host "[OK] 已安装: $destJar" -ForegroundColor Green
}

function Copy-ToDesktop([string]$src, [string]$name) {
    # 用系统的“桌面”路径，比 $env:USERPROFILE\Desktop 可靠（含 OneDrive 重定向的情况）
    $desktop = [Environment]::GetFolderPath("Desktop")
    if (-not (Test-Path $desktop)) { throw "找不到桌面目录: $desktop" }

    $destJar = Join-Path $desktop "$name.jar"
    Copy-Item $src $destJar -Force
    Write-Host "[OK] 已拷贝到桌面: $destJar" -ForegroundColor Green
}

# ---- Java 模组：gradle 构建 ----
$proj = Join-Path $root $Mod
if (-not (Test-Path (Join-Path $proj "build.gradle"))) {
    Write-Error "未找到模组工程: $proj"
}

Push-Location $proj
try {
    & .\gradlew.bat jar
    if ($LASTEXITCODE -ne 0) { throw "gradle 构建失败" }
} finally {
    Pop-Location
}
$jar = Join-Path $proj "build\libs\$Mod.jar"
if (-not (Test-Path $jar)) {
    # 目录名不一定等于 jar 文件名：Enemy Pause 的产物叫 EnemyPause.jar
    # （jar 名取自 settings.gradle 里的项目名），取不到就退回 build\libs 下最新的那个
    $jar = (Get-ChildItem (Join-Path $proj "build\libs\*.jar") -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $jar -or -not (Test-Path $jar)) { throw "未找到构建产物: $proj\build\libs\*.jar" }
Write-Host "[OK] 已构建: $jar" -ForegroundColor Green

if ($Zip) {
    if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }
    Compress-Archive -Path $jar -DestinationPath (Join-Path $dist "$Mod.zip") -Force
    Write-Host "[OK] 已打包: $(Join-Path $dist "$Mod.zip")" -ForegroundColor Green
}
if ($Install) {
    # 安装用的文件名取 jar 自身的名字（游戏按文件名识别模组）
    Install-ToGameMods $jar ([System.IO.Path]::GetFileNameWithoutExtension($jar))
}
if ($Desktop) {
    Copy-ToDesktop $jar ([System.IO.Path]::GetFileNameWithoutExtension($jar))
}
if ($Install) {
    Write-Host "启动游戏 -> 设置 -> 模组 应能看到它。"
}
