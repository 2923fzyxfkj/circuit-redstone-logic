# 生成发布用品牌图：Modrinth / GitHub 的封面（16:9）与项目图标（512x512）。
#
# 为什么单独一个脚本：封面是发布物料，不属于游戏资源；但它依赖 assets 里的方块贴图，
# 所以放在 tools 里、和 GenerateAssets 用同一套 magick 调用。
#
# 用法：pwsh -File tools/GenerateBranding.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$textures = Join-Path $root 'src\main\resources\assets\circuit-redstone_logic\textures\block'
$outDir = Join-Path $root 'docs\branding'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# magick 把反斜杠当转义符，字体路径必须用正斜杠。
$fontBold = 'C:/Windows/Fonts/segoeuib.ttf'
$fontRegular = 'C:/Windows/Fonts/segoeui.ttf'
$fontMono = 'C:/Windows/Fonts/consolab.ttf'
foreach ($font in @($fontBold, $fontRegular, $fontMono)) {
	if (-not (Test-Path $font)) {
		throw "缺少字体：$font"
	}
}

$pngOutput = @('-depth', '8', '-colorspace', 'sRGB', '-type', 'TrueColorAlpha', '-strip')

function Invoke-Magick {
	param([string[]]$Arguments)
	& magick @Arguments
	if ($LASTEXITCODE -ne 0) {
		throw "magick 失败（exit $LASTEXITCODE）：magick $($Arguments -join ' ')"
	}
}

$work = Join-Path $env:TEMP ("crl-branding-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $work | Out-Null

try {
	# ------------------------------------------------------------ 封面 1920x1080

	$width = 1920
	$height = 1080
	$cover = Join-Path $outDir 'cover-1920x1080.png'

	# 背景网格：80px 一格，颜色只比背景亮一点，避免抢文字。
	$gridLines = New-Object System.Collections.ArrayList
	for ($x = 0; $x -le $width; $x += 80) {
		[void]$gridLines.Add("line $x,0 $x,$height")
	}
	for ($y = 0; $y -le $height; $y += 80) {
		[void]$gridLines.Add("line 0,$y $width,$y")
	}
	$grid = ($gridLines.ToArray() -join ' ')

	$background = @('-size', "${width}x${height}", 'gradient:#1A1A26-#0B0B10',
		'-stroke', '#1C1C28', '-strokewidth', '1', '-draw', $grid)
	$background += $pngOutput
	$background += $cover
	Invoke-Magick -Arguments $background

	# 柔光：先画硬边圆再强模糊，避免出现"一个大圆盘"的硬边。
	$glowTeal = Join-Path $work 'glow-teal.png'
	Invoke-Magick -Arguments @('-size', "${width}x${height}", 'xc:none',
		'-fill', 'rgba(79,224,192,0.34)', '-draw', 'circle 1660,180 1940,180', '-blur', '0x150',
		'-depth', '8', $glowTeal)
	$glowPurple = Join-Path $work 'glow-purple.png'
	Invoke-Magick -Arguments @('-size', "${width}x${height}", 'xc:none',
		'-fill', 'rgba(170,70,200,0.26)', '-draw', 'circle 220,980 520,980', '-blur', '0x170',
		'-depth', '8', $glowPurple)
	Invoke-Magick -Arguments @($cover, $glowTeal, '-composite', $cover)
	Invoke-Magick -Arguments @($cover, $glowPurple, '-composite', $cover)

	# 文字层。
	$text = @($cover, '-font', $fontBold, '-pointsize', '116', '-fill', '#FFFFFF',
		'-annotate', '+120+330', 'CIRCUIT REDSTONE',
		'-fill', '#4FE0C0', '-annotate', '+120+460', 'LOGIC',
		'-font', $fontRegular, '-pointsize', '34', '-fill', '#A8B0C0',
		'-annotate', '+126+540', 'Binary logic for redstone: 0/1 wires, zero-delay gates, writable data blocks',
		'-font', $fontMono, '-pointsize', '24', '-fill', '#4FE0C0',
		'-annotate', '+126+606', 'MINECRAFT 1.20.4    ·    FABRIC    ·    MIT',
		'-font', $fontRegular, '-pointsize', '22', '-fill', '#6E7480',
		'-annotate', '+126+1006', '18 blocks · 3 recipes · 4 command groups · v1.28.7',
		'-pointsize', '20', '-fill', '#4A5060',
		'-annotate', '+126+1042', 'developed by DeepSeek-v4.1-Flash and DeepSeekHarness')
	$text += $pngOutput
	$text += $cover
	Invoke-Magick -Arguments $text

	# 右侧方块带：用真实贴图拼一条"导线—门—导线…"的链路。
	$chain = @(
		@{ File = 'wire_lit'; Label = 'wire' },
		@{ File = 'gate_or'; Label = 'OR' },
		@{ File = 'wire_lit'; Label = 'wire' },
		@{ File = 'gate_and'; Label = 'AND' },
		@{ File = 'wire_lit'; Label = 'wire' },
		@{ File = 'gate_xor'; Label = 'XOR' },
		@{ File = 'wire_lit'; Label = 'wire' }
	)
	$tileSize = 104
	$gap = 16
	$tiles = New-Object System.Collections.ArrayList
	foreach ($item in $chain) {
		$source = Join-Path $textures "$($item.File).png"
		if (-not (Test-Path $source)) {
			throw "缺少贴图：$source"
		}
		$tile = Join-Path $work "$($item.File)-$($tiles.Count).png"
		# point 过滤保留像素风；深色边框给方块卡片的观感。
		Invoke-Magick -Arguments @($source, '-filter', 'point', '-resize', "${tileSize}x${tileSize}",
			'-bordercolor', '#2E2E3C', '-border', '4', $tile)
		[void]$tiles.Add($tile)
	}

	$stripArgs = @('-background', 'none')
	foreach ($tile in $tiles) {
		$stripArgs += $tile
		$stripArgs += '-splice'
		$stripArgs += "${gap}x0"
	}
	$strip = Join-Path $work 'chain.png'
	$stripArgs += '+append'
	$stripArgs += $strip
	Invoke-Magick -Arguments $stripArgs

	$stripWidth = [int](& magick identify -format '%w' $strip)
	$chainX = $width - $stripWidth - 120
	$chainY = 700
	Invoke-Magick -Arguments @($cover, $strip, '-geometry', "+$chainX+$chainY", '-composite', $cover)

	# 方块链下方的一行说明：让这排色块能被读懂。
	$labels = ($chain | ForEach-Object { $_.Label }) -join '  ·  '
	Invoke-Magick -Arguments @($cover, '-font', $fontMono, '-pointsize', '21', '-fill', '#7A8090',
		'-annotate', "+$chainX+$($chainY + 156)", $labels, $cover)

	# ------------------------------------------------------------ 图标 512x512

	$icon = Join-Path $outDir 'icon-512.png'
	$iconArgs = @(
		'-size', '512x512', 'xc:none',
		'-fill', '#14141A', '-draw', 'roundrectangle 0,0 511,511 88,88',
		'-fill', '#2EA85E', '-draw', 'roundrectangle 56,56 248,248 26,26',
		'-fill', '#3C78DC', '-draw', 'roundrectangle 264,56 456,248 26,26',
		'-fill', '#DCA83C', '-draw', 'roundrectangle 56,264 248,456 26,26',
		'-fill', '#AA46C8', '-draw', 'roundrectangle 264,264 456,456 26,26',
		'-fill', '#14141A', '-draw', 'roundrectangle 190,190 322,322 24,24',
		'-fill', '#4FE0C0', '-draw', 'rectangle 236,112 276,400',
		'-fill', '#4FE0C0', '-draw', 'rectangle 112,236 400,276'
	)
	$iconArgs += $pngOutput
	$iconArgs += $icon
	Invoke-Magick -Arguments $iconArgs

	Write-Output ("branding written: {0}, {1}" -f $cover, $icon)
} finally {
	Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
