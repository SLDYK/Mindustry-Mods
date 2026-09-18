# Generates every block sprite used by the erekir-items mod.
#
# Sprites are 32x32 top-down pixel art in Erekir style: dark metal plate with a
# bevelled edge and a few cyan/colored details.
#
# Why a script instead of hand-drawn art: the mod is a container that keeps
# growing, and 32x32 block icons are cheap to describe procedurally. Add a new
# Draw-<Something> function and call it from the bottom of this file.
#
# Naming rule (important): mod sprites are packed into the atlas as
#   sprites/<file base name>.png  ->  atlas region "<mod internal name>-<file base name>"
# so the FILE must NOT repeat the mod name, otherwise the block looks up
# "erekir-items-erekir-items-..." and finds nothing.
#
# Pure ASCII on purpose: PowerShell 5.1 reads non-BOM script files as cp936.
#
# Usage:  powershell -ExecutionPolicy Bypass -File tools\gen-sprites.ps1

Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$spriteDir = Join-Path $root "erekir-items\assets\sprites"

# ---- tiny drawing helpers (they operate on the script-scope canvas) ----

function New-Canvas([int]$size) {
    $script:bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $script:g = [System.Drawing.Graphics]::FromImage($script:bmp)
    $script:g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $script:g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $script:g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $script:g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
}

function Col([string]$hex, [int]$alpha = 255) {
    $r = [Convert]::ToInt32($hex.Substring(0, 2), 16)
    $gg = [Convert]::ToInt32($hex.Substring(2, 2), 16)
    $b = [Convert]::ToInt32($hex.Substring(4, 2), 16)
    return [System.Drawing.Color]::FromArgb($alpha, $r, $gg, $b)
}

function Fill([int]$x, [int]$y, [int]$w, [int]$h, [string]$hex, [int]$alpha = 255) {
    $brush = New-Object System.Drawing.SolidBrush((Col $hex $alpha))
    $script:g.FillRectangle($brush, $x, $y, $w, $h)
    $brush.Dispose()
}

function Circle([double]$cx, [double]$cy, [double]$r, [string]$hex, [int]$alpha = 255) {
    $brush = New-Object System.Drawing.SolidBrush((Col $hex $alpha))
    $script:g.FillEllipse($brush, [float]($cx - $r), [float]($cy - $r), [float]($r * 2), [float]($r * 2))
    $brush.Dispose()
}

function Save-Canvas([string]$name) {
    if (-not (Test-Path $spriteDir)) { New-Item -ItemType Directory -Path $spriteDir -Force | Out-Null }
    $out = Join-Path $spriteDir "$name.png"
    if (Test-Path $out) { Remove-Item $out -Force }
    $script:bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $script:bmp.Dispose()
    $script:g.Dispose()
    Write-Host "[OK] sprite written: $out"
}

# ---- shared plate: the bevelled dark base every block in this mod sits on ----

function Draw-BasePlate {
    Fill 1 1 30 30 "3d3b38"
    # bevel: bright on the top/left, shadow on the bottom/right
    Fill 1 1 30 1 "57534e"
    Fill 1 1 1 30 "57534e"
    Fill 1 30 30 1 "232120"
    Fill 30 1 1 30 "232120"
}

function Draw-CornerRivets {
    foreach ($p in @(@(3, 3), @(26, 3), @(3, 26), @(26, 26))) {
        Fill $p[0] $p[1] 3 3 "55524e"
        Fill ($p[0] + 1) ($p[1] + 1) 1 1 "2b2a27"
    }
}

# ---- tunable-node: power node with four emitter arms and a glowing core ----

function Draw-TunableNode {
    Draw-BasePlate

    # inner recessed panel
    Fill 5 5 22 22 "2e2d2a"
    Fill 5 5 22 1 "232120"
    Fill 5 5 1 22 "232120"
    Fill 26 5 1 22 "44413d"
    Fill 5 26 22 1 "44413d"

    # four emitter arms (the beam node connects in cardinal directions)
    Fill 13 1 6 8 "45423e"
    Fill 13 23 6 8 "45423e"
    Fill 1 13 8 6 "45423e"
    Fill 23 13 8 6 "45423e"
    # bright channel down the middle of each arm
    Fill 15 1 2 8 "7fe0f0"
    Fill 15 23 2 8 "7fe0f0"
    Fill 1 15 8 2 "7fe0f0"
    Fill 23 15 8 2 "7fe0f0"
    # bright tips
    Fill 14 1 4 1 "d6f7ff"
    Fill 14 30 4 1 "d6f7ff"
    Fill 1 14 1 4 "d6f7ff"
    Fill 30 14 1 4 "d6f7ff"

    # glowing core
    Circle 15.5 15.5 8.0 "1d5c66" 160
    Circle 15.5 15.5 6.5 "35a7bb" 200
    Circle 15.5 15.5 5.0 "8fe8f7"
    Circle 15.5 15.5 3.0 "e2fbff"
    Circle 15.5 15.5 1.5 "ffffff"

    Draw-CornerRivets
}

# ---- resource-source: a water tank on one side, phase fabric stacks on the other ----

function Draw-ResourceSource {
    Draw-BasePlate

    # inner recessed panel
    Fill 4 4 24 24 "2b2a27"
    Fill 4 4 24 1 "1e1d1b"
    Fill 4 4 1 24 "1e1d1b"
    Fill 27 4 1 24 "44413d"
    Fill 4 27 24 1 "44413d"

    # ---- left half: water tank ----
    Fill 5 6 11 20 "1f1e1c"
    Fill 6 12 9 13 "33509a"      # body of the water
    Fill 6 12 9 1 "8fb0ee"       # bright waterline
    Fill 6 24 9 1 "26407c"       # darker at the bottom
    Fill 7 15 2 8 "4f74c0"       # highlight stripe
    # tank walls
    Fill 5 5 11 1 "5a564f"
    Fill 5 26 11 1 "5a564f"
    Fill 5 5 1 22 "5a564f"
    Fill 15 5 1 22 "5a564f"
    # little inlet on top of the tank
    Fill 9 3 3 2 "6b665d"

    # ---- right half: stacked phase fabric (item color f4ba6e) ----
    Fill 17 5 10 22 "1f1e1c"
    for ($i = 0; $i -lt 4; $i++) {
        $y = 8 + $i * 5
        Fill 18 $y 8 4 "f4ba6e"
        Fill 18 $y 8 1 "ffd9a8"          # lit top edge
        Fill 18 ($y + 3) 8 1 "b8843f"    # shaded bottom edge
        Fill 21 ($y + 1) 1 2 "d99f57"    # fibre line
        Fill 19 ($y + 1) 1 2 "ffe6c2"
    }
    # frame around the stacks
    Fill 17 5 10 1 "5a564f"
    Fill 17 26 10 1 "5a564f"
    Fill 17 5 1 22 "5a564f"
    Fill 26 5 1 22 "5a564f"

    Draw-CornerRivets
}

# ---- run ----

New-Canvas 32
Draw-TunableNode
Save-Canvas "tunable-node"

New-Canvas 32
Draw-ResourceSource
Save-Canvas "resource-source"
