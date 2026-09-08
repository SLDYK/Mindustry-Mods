# Mindustry 模组打包 & 安装脚本（ra2-controls 专用）
# 用法:
#   .\tools\pack.ps1                       # 构建 ra2-controls（gradle jar）
#   .\tools\pack.ps1 -Install              # 构建并安装到游戏 mods 目录
#   .\tools\pack.ps1 -Zip                  # 构建并输出 zip 到 dist/
param(
    [string]$Mod = "ra2-controls",
    [switch]$Install,
    [switch]$Zip
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
if (-not (Test-Path $jar)) { throw "未找到构建产物: $jar" }
Write-Host "[OK] 已构建: $jar" -ForegroundColor Green

if ($Zip) {
    if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }
    Compress-Archive -Path $jar -DestinationPath (Join-Path $dist "$Mod.zip") -Force
    Write-Host "[OK] 已打包: $(Join-Path $dist "$Mod.zip")" -ForegroundColor Green
}
if ($Install) {
    Install-ToGameMods $jar $Mod
    Write-Host "启动游戏 -> 设置 -> 模组 应显示 RA2 Controls。"
}
