# Generates the block sprite for the Erekir tunable power node mod.
#
# 32x32 top-down pixel art, Erekir style: dark metal plate, four emitter arms
# pointing north/east/south/west (the beam node connects in cardinal directions)
# and a cyan glowing core in the middle.
#
# Pure ASCII on purpose: PowerShell 5.1 reads non-BOM script files as cp936.
#
# Usage:  powershell -ExecutionPolicy Bypass -File tools\gen-tunable-node-sprite.ps1

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
# NOTE: mod sprites are packed under "<mod internal name>-<file base name>", so the file
# itself must NOT repeat the mod name (that would produce a double prefix).
$outFile = Join-Path $root "erekir-items\assets\sprites\tunable-node.png"
$size = 32

$bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))

function Col([string]$hex, [int]$alpha = 255) {
    $r = [Convert]::ToInt32($hex.Substring(0, 2), 16)
    $gg = [Convert]::ToInt32($hex.Substring(2, 2), 16)
    $b = [Convert]::ToInt32($hex.Substring(4, 2), 16)
    return [System.Drawing.Color]::FromArgb($alpha, $r, $gg, $b)
}

function Fill([int]$x, [int]$y, [int]$w, [int]$h, [string]$hex, [int]$alpha = 255) {
    $brush = New-Object System.Drawing.SolidBrush((Col $hex $alpha))
    $g.FillRectangle($brush, $x, $y, $w, $h)
    $brush.Dispose()
}

function Circle([double]$cx, [double]$cy, [double]$r, [string]$hex, [int]$alpha = 255) {
    $brush = New-Object System.Drawing.SolidBrush((Col $hex $alpha))
    $g.FillEllipse($brush, [float]($cx - $r), [float]($cy - $r), [float]($r * 2), [float]($r * 2))
    $brush.Dispose()
}

# ---- base plate (30x30, 1px transparent margin) ----
Fill 1 1 30 30 "3d3b38"
# bevel: light on the top/left, shadow on the bottom/right
Fill 1 1 30 1 "57534e"
Fill 1 1 1 30 "57534e"
Fill 1 30 30 1 "232120"
Fill 30 1 1 30 "232120"
# inner recessed panel
Fill 5 5 22 22 "2e2d2a"
Fill 5 5 22 1 "232120"
Fill 5 5 1 22 "232120"
Fill 26 5 1 22 "44413d"
Fill 5 26 22 1 "44413d"

# ---- four emitter arms ----
# dark arm body + bright cyan channel in the middle of each arm
Fill 13 1 6 8 "45423e"
Fill 13 23 6 8 "45423e"
Fill 1 13 8 6 "45423e"
Fill 23 13 8 6 "45423e"
Fill 15 1 2 8 "7fe0f0"
Fill 15 23 2 8 "7fe0f0"
Fill 1 15 8 2 "7fe0f0"
Fill 23 15 8 2 "7fe0f0"
# bright tips
Fill 14 1 4 1 "d6f7ff"
Fill 14 30 4 1 "d6f7ff"
Fill 1 14 1 4 "d6f7ff"
Fill 30 14 1 4 "d6f7ff"

# ---- glowing core ----
Circle 15.5 15.5 8.0 "1d5c66" 160
Circle 15.5 15.5 6.5 "35a7bb" 200
Circle 15.5 15.5 5.0 "8fe8f7"
Circle 15.5 15.5 3.0 "e2fbff"
Circle 15.5 15.5 1.5 "ffffff"

# ---- corner rivets ----
foreach ($p in @(@(3, 3), @(26, 3), @(3, 26), @(26, 26))) {
    Fill $p[0] $p[1] 3 3 "55524e"
    Fill ($p[0] + 1) ($p[1] + 1) 1 1 "2b2a27"
}

$g.Dispose()

$dir = Split-Path -Parent $outFile
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
if (Test-Path $outFile) { Remove-Item $outFile -Force }
$bmp.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Host "[OK] sprite written: $outFile"
