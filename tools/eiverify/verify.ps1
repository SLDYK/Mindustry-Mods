# Offline verifiers for the erekir-items mod.
#
# Compiles everything in tools/eiverify against the local game jar plus the freshly
# built mod jar, then runs each verifier in turn. No game process is started.
#
# Verifiers:
#   VerifyModJar          - jar layout, main class, per-content sprite region names,
#                           and every bundle key the code reads (both languages)
#   VerifyTunableNode     - tunable-node block: flags, config round-trip, power math, save IO
#   VerifyResourceSource  - resource-source block: flags, both production loops, save IO
#
# NOTE: keep this file pure ASCII -- PowerShell 5.1 reads non-BOM script files as cp936.
#
# Usage:  powershell -ExecutionPolicy Bypass -File tools\eiverify\verify.ps1 [-Mod erekir-items]

param(
    [string]$Mod = "erekir-items"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$srcDir = $PSScriptRoot
$outDir = Join-Path $PSScriptRoot "out"

function Find-GameJar {
    if ($env:MINDUSTRY_JAR -and (Test-Path $env:MINDUSTRY_JAR)) { return $env:MINDUSTRY_JAR }

    $home2 = $env:USERPROFILE
    $candidates = @(
        "E:\SteamLibrary\steamapps\common\Mindustry\jre\desktop.jar",
        "D:\SteamLibrary\steamapps\common\Mindustry\jre\desktop.jar",
        "C:\Program Files (x86)\Steam\steamapps\common\Mindustry\jre\desktop.jar",
        "$home2\Library\Application Support\Steam\steamapps\common\Mindustry\Mindustry.app\Contents\Resources\desktop.jar",
        "$home2\.steam\steam\steamapps\common\Mindustry\desktop.jar",
        "$home2\.local\share\Steam\steamapps\common\Mindustry\desktop.jar"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    throw "Mindustry desktop.jar not found. Set MINDUSTRY_JAR to point at it."
}

$gameJar = Find-GameJar

$modJar = Join-Path $root "$Mod\build\libs\$Mod.jar"
if (-not (Test-Path $modJar)) {
    $modJar = (Get-ChildItem (Join-Path $root "$Mod\build\libs\*.jar") -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $modJar -or -not (Test-Path $modJar)) {
    throw "Mod jar not found. Build it first: tools\pack.ps1 -Mod `"$Mod`""
}

if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

$javac = "javac"
$java = "java"
if ($env:JAVA_HOME) {
    $javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
    $java = Join-Path $env:JAVA_HOME "bin\java.exe"
}

Write-Host "[eiverify] game jar: $gameJar"
Write-Host "[eiverify] mod jar : $modJar"

$classpath = "$gameJar;$modJar"
$sources = Get-ChildItem (Join-Path $srcDir "*.java") | ForEach-Object { $_.FullName }

& $javac -encoding UTF-8 -implicit:none -sourcepath $srcDir -cp $classpath -d $outDir $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$verifiers = @("VerifyModJar", "VerifyTunableNode", "VerifyResourceSource")
$failed = @()

foreach ($verifier in $verifiers) {
    Write-Host ""
    Write-Host "############################################################"
    Write-Host "# $verifier"
    Write-Host "############################################################"

    & $java '-Dfile.encoding=UTF-8' -cp "$outDir;$classpath" $verifier $modJar
    if ($LASTEXITCODE -ne 0) { $failed += $verifier }
}

Write-Host ""
if ($failed.Count -gt 0) {
    throw "verification failed: $($failed -join ', ')"
}
Write-Host "[eiverify] all verifiers passed."
