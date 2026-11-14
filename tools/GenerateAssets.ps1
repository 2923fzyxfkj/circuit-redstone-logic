# 生成 CircuitRedstone 的占位美术与数据资源。
#
# 说明：
#  - 贴图是 16x16 的纯色方块（带 1px 描边），满足"最简纯色模型"的占位要求；
#  - 导线用 multipart：中心方块 + 六个方向的细臂，通电时换成高亮贴图；
#  - 电路方块用 multipart 只按 facing 旋转，电平由 luminance 表现；
#  - 物品模型直接继承方块模型，图形变换来自 minecraft:block/block。
#
# 用法：pwsh -File tools/GenerateAssets.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$resources = Join-Path $root 'src\main\resources'
$assets = Join-Path $resources 'assets\circuit-redstone_logic'
$textures = Join-Path $assets 'textures\block'
$blockstates = Join-Path $assets 'blockstates'
$blockModels = Join-Path $assets 'models\block'
$itemModels = Join-Path $assets 'models\item'
$langDir = Join-Path $assets 'lang'
$recipes = Join-Path $resources 'data\circuit-redstone_logic\recipes'

foreach ($dir in @($textures, (Join-Path $assets 'textures\item'), $blockstates, $blockModels, $itemModels, $langDir, $recipes)) {
	New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$modId = 'circuit-redstone_logic'

function Write-Json {
	param([string]$Path, $Value)
	$json = $Value | ConvertTo-Json -Depth 20
	Set-Content -Path $Path -Value $json -Encoding utf8NoBOM
}

# ---------------------------------------------------------------- 贴图

# 贴图全部用 ImageMagick 的 `magick` 命令生成（不再依赖 System.Drawing，跨平台也更稳）。
function Invoke-Magick {
	param([string[]]$Arguments)
	& magick @Arguments
	if ($LASTEXITCODE -ne 0) {
		throw "magick 调用失败（exit $LASTEXITCODE）：magick $($Arguments -join ' ')"
	}
}

# Minecraft 只认 8-bit 的 PNG：ImageMagick 默认可能是 16-bit 或灰度，
# 所以统一在输出前固定成 8-bit、sRGB、带 alpha。
$pngOutputArguments = @('-depth', '8', '-colorspace', 'sRGB', '-type', 'TrueColorAlpha',
	'-define', 'png:color-type=6', '-strip')

# 纯色方块贴图：整块填充 + 1px 描边 + （可选）中心强调块。
function New-Texture {
	param(
		[string]$Path,
		[string]$Fill,
		[string]$Border,
		[string]$Accent,
		[int]$Size = 16
	)
	$arguments = @('-size', "${Size}x${Size}", "xc:$Border")
	$arguments += @('-fill', $Fill, '-draw', "rectangle 1,1 $($Size - 2),$($Size - 2)")
	if ($Accent) {
		$inset = [int]($Size / 4)
		$arguments += @('-fill', $Accent, '-draw', "rectangle $inset,$inset $($Size - 1 - $inset),$($Size - 1 - $inset)")
	}
	$arguments += $script:pngOutputArguments
	$arguments += $Path
	Invoke-Magick -Arguments $arguments
}

# 带方向箭头的贴图（数据线用）：箭头指向纹理上方，对应模型顶面的"北"。
function New-ArrowTexture {
	param(
		[string]$Path,
		[string]$Fill,
		[string]$Border,
		[string]$Arrow,
		[int]$Size = 16,
		[int]$Rotation = 0
	)
	$center = [int]($Size / 2)
	$shaftWidth = [int][Math]::Max(2, $Size / 5)
	$headHeight = [int][Math]::Max(4, $Size / 3)
	$shaftX0 = [int](($Size - $shaftWidth) / 2)
	$shaftX1 = $shaftX0 + $shaftWidth - 1
	$shaftY0 = $headHeight
	$shaftY1 = $Size - 3
	$headBaseY = $headHeight + 1
	$headLeft = $center - $headHeight + 1
	$headRight = $center + $headHeight - 1

	$arguments = @('-size', "${Size}x${Size}", "xc:$Border")
	$arguments += @('-fill', $Fill, '-draw', "rectangle 1,1 $($Size - 2),$($Size - 2)")
	$arguments += @('-fill', $Arrow, '-draw', "rectangle $shaftX0,$shaftY0 $shaftX1,$shaftY1")
	$arguments += @('-draw', "polygon $center,1 $headLeft,$headBaseY $headRight,$headBaseY")
	if ($Rotation -ne 0) {
		# 旋转成水平箭头（侧面贴图用）：90 = 指向右，-90 = 指向左。
		$arguments += @('-rotate', "$Rotation")
	}
	$arguments += $script:pngOutputArguments
	$arguments += $Path
	Invoke-Magick -Arguments $arguments
}

# 全透明贴图：给"方块本体不可见、只靠方块实体渲染器画东西"的方块用（隐形告示牌）。
function New-EmptyTexture {
	param(
		[string]$Path,
		[int]$Size = 16
	)
	$arguments = @('-size', "${Size}x${Size}", 'xc:none')
	$arguments += $script:pngOutputArguments
	$arguments += $Path
	Invoke-Magick -Arguments $arguments
}

# 悬空方块：中间透明 + 一圈**不透明**白框（cutout 渲染）。
# 用四条填充矩形拼边框；边框占 1/16（6.25%）：真的只有一圈细线（之前"隐形"是模型缺 parent，不是边框太细）。
# alpha 阈值化成 1-bit：cutout 只认"透明 / 不透明"，半透明边缘会被裁出锯齿。
function New-FrameTexture {
	param(
		[string]$Path,
		[string]$Line = 'white',
		[int]$Size = 16,
		[int]$Thickness = 4
	)
	$last = $Size - 1
	$thickLast = $Thickness - 1
	$arguments = @('-size', "${Size}x${Size}", 'xc:none')
	$arguments += @('-fill', $Line)
	$arguments += @('-draw', "rectangle 0,0 $last,$thickLast")
	$arguments += @('-draw', "rectangle 0,$($Size - $Thickness) $last,$last")
	$arguments += @('-draw', "rectangle 0,0 $thickLast,$last")
	$arguments += @('-draw', "rectangle $($Size - $Thickness),0 $last,$last")
	$arguments += @('-channel', 'A', '-threshold', '50%', '+channel')
	$arguments += $script:pngOutputArguments
	$arguments += $Path
	Invoke-Magick -Arguments $arguments
}

$palette = [ordered]@{
	'wire'       = @('#3C3C46', '#24242C', $null)
	'wire_lit'   = @('#E03828', '#8C1C14', '#FF7A50')
	'gate_base'  = @('#3C3C46', '#24242C', $null)
	'gate_side'  = @('#5A5A64', '#3A3A44', $null)
	'gate_top'   = @('#6E6E78', '#4A4A54', $null)
	'gate_and'   = @('#2EA85E', '#1C6B3C', '#7BE0A0')
	'gate_or'    = @('#3C78DC', '#204A90', '#84B4FF')
	'gate_not'   = @('#DCA83C', '#8E6A1C', '#FFD884')
	'gate_xor'   = @('#AA46C8', '#6A2280', '#E094F5')
	'gate_limiter' = @('#C8505A', '#7E2A32', '#F0909A')
	'gate_comparator' = @('#C8A050', '#7E6428', '#F0D890')
	'gate_scanner' = @('#3CC0C0', '#1E7474', '#90EEEE')
	'gate_amplifier' = @('#E8E8F0', '#9098A8', '#FFFFFF')
	'gate_brightness' = @('#F0DC8C', '#9C8A46', '#FFF6C8')
	# "通电"版本：只用于贴图变亮，不让方块发光（发光会污染亮度传感器的读数）
	'gate_and_on' = @('#4FE08A', '#2E8050', '#C8FFDC')
	'gate_or_on' = @('#6AA8FF', '#2E5AA8', '#C0DCFF')
	'gate_not_on' = @('#FFD060', '#A87E2E', '#FFF0B0')
	'gate_xor_on' = @('#D070F0', '#7E3AA8', '#F0C0FF')
	'gate_limiter_on' = @('#F07A86', '#A8484E', '#FFC0C6')
	'gate_comparator_on' = @('#F0D070', '#B0904A', '#FFF0B8')
	'gate_clock' = @('#5A8C9C', '#2E525C', '#A0DCE8')
	'gate_clock_on' = @('#7AD0E0', '#3E8C9C', '#D0F6FF')
	'gate_scanner_on' = @('#60E8E8', '#289090', '#C8FFFF')
	'gate_amplifier_on' = @('#FFFFFF', '#B0B8C8', '#FFFFFF')
	'gate_brightness_on' = @('#FFF6B0', '#C8B060', '#FFFFE0')
	# 数据方块（常量 / 字符串）：没有朝向与电平属性，所以只需要一套贴图。
	'gate_constant' = @('#C060A8', '#7A3666', '#F0A8E0')
	'gate_string' = @('#5AC8A0', '#2E7A5E', '#B0F0D8')
	# 变量方块：四种类型各一套贴图（未设置 / 空值 / 整数 / 文本），blockstate 按 kind 选模型。
	'gate_variable_unset' = @('#6A6A78', '#3A3A44', '#8A8A98')
	'gate_variable_null' = @('#9A7AD0', '#5A3A88', '#C8A8F0')
	'gate_variable_int' = @('#E08A3C', '#8A4A1C', '#FFC080')
	'gate_variable_str' = @('#3C8AE0', '#1C4A8A', '#80C0FF')
	# 数据线：箭头单独画（见 New-ArrowTexture），底色沿用电路壳
	'gate_data_wire' = @('#4A4A56', '#2C2C34', $null)
	'gate_data_wire_error' = @('#8C2A2A', '#4A1414', $null)
	# 纯色方块：纯白贴图，客户端按方块实体里的颜色着色（tint 相乘）
	'color_block' = @('#FFFFFF', '#D2D2D2', $null)
}

foreach ($name in $palette.Keys) {
	$entry = $palette[$name]
	New-Texture -Path (Join-Path $textures "$name.png") -Fill $entry[0] -Border $entry[1] -Accent $entry[2]
}

# 数据线的方向箭头：画在**东西两个侧面**上（水平方向），这样从侧面看是正的、不会被顶面透视压斜。
# 默认（facing=north）时数据向"北"流动：
#   东面：从外面看，屏幕右 = 北 → 箭头朝右；
#   西面：从外面看，屏幕左 = 北 → 箭头朝左。
New-ArrowTexture -Path (Join-Path $textures 'data_wire_arrow_right.png') -Fill '#4A4A56' -Border '#2C2C34' -Arrow '#8CF0C8' -Rotation 90
New-ArrowTexture -Path (Join-Path $textures 'data_wire_arrow_left.png') -Fill '#4A4A56' -Border '#2C2C34' -Arrow '#8CF0C8' -Rotation -90
New-ArrowTexture -Path (Join-Path $textures 'data_wire_arrow_error_right.png') -Fill '#8C2A2A' -Border '#4A1414' -Arrow '#FFD0D0' -Rotation 90
New-ArrowTexture -Path (Join-Path $textures 'data_wire_arrow_error_left.png') -Fill '#8C2A2A' -Border '#4A1414' -Arrow '#FFD0D0' -Rotation -90

# 电路终端是纯物品：单独的 item 贴图。
New-Texture -Path (Join-Path $assets 'textures\item\terminal.png') -Fill '#2A3442' -Border '#131A24' -Accent '#4FE0C0'

# 悬空方块：中间透明、四边一圈白线（客户端用 cutout 渲染层，见 CircuitRedstoneClient）。
New-FrameTexture -Path (Join-Path $textures 'hover_frame.png') -Line 'white' -Size 16 -Thickness 1

# 隐形告示牌：方块贴图全透明（文字由客户端 BER 画），物品图标单独画一个告示牌的样子。
New-EmptyTexture -Path (Join-Path $textures 'hidden_sign.png') -Size 16
$signIconArguments = @('-size', '16x16', 'xc:none')
$signIconArguments += @('-fill', '#8C6A46', '-draw', 'rectangle 3,4 12,12')
$signIconArguments += @('-fill', '#6E4E30', '-draw', 'rectangle 3,4 12,5')
$signIconArguments += @('-fill', '#C8B48C', '-draw', 'rectangle 4,6 11,6')
$signIconArguments += @('-fill', '#F0E0C0', '-draw', 'rectangle 5,8 10,8')
$signIconArguments += @('-fill', '#F0E0C0', '-draw', 'rectangle 5,10 9,10')
$signIconArguments += $script:pngOutputArguments
$signIconArguments += (Join-Path (Join-Path $assets 'textures\item') 'hidden_sign.png')
Invoke-Magick -Arguments $signIconArguments
# 模组图标：128x128，四象限取四个门的颜色（同样用 magick 画）
$quadrants = @(
	@('#2EA85E', 8, 8, 63, 63),
	@('#3C78DC', 64, 8, 119, 63),
	@('#DCA83C', 8, 64, 63, 119),
	@('#AA46C8', 64, 64, 119, 119)
)
$iconArguments = @('-size', '128x128', 'xc:#1E1E24')
foreach ($quadrant in $quadrants) {
	$iconArguments += @('-fill', $quadrant[0], '-draw',
		"rectangle $($quadrant[1]),$($quadrant[2]) $($quadrant[3]),$($quadrant[4])")
}
$iconArguments += $pngOutputArguments
$iconArguments += (Join-Path $assets 'icon.png')
Invoke-Magick -Arguments $iconArguments

# ---------------------------------------------------------------- 模型

function New-CubeElement {
	param($From, $To, [string]$Texture)
	$faces = [ordered]@{}
	foreach ($face in @('down', 'up', 'north', 'south', 'west', 'east')) {
		$faces[$face] = [ordered]@{ texture = $Texture }
	}
	return [ordered]@{
		from  = $From
		to    = $To
		faces = $faces
	}
}

$wireTexture = "${modId}:block/wire"
$wireLitTexture = "${modId}:block/wire_lit"

foreach ($state in @(@('off', $wireTexture), @('on', $wireLitTexture))) {
	$stateName = $state[0]
	$texture = $state[1]
	Write-Json (Join-Path $blockModels "wire_core_$stateName.json") ([ordered]@{
		ambientocclusion = $false
		textures         = [ordered]@{ particle = $texture; all = $texture }
		elements         = @(New-CubeElement -From @(6, 6, 6) -To @(10, 10, 10) -Texture '#all')
	})
}

$arms = [ordered]@{
	north = @(@(6, 6, 0), @(10, 10, 6))
	south = @(@(6, 6, 10), @(10, 10, 16))
	west  = @(@(0, 6, 6), @(6, 10, 10))
	east  = @(@(10, 6, 6), @(16, 10, 10))
	down  = @(@(6, 0, 6), @(10, 6, 10))
	up    = @(@(6, 10, 6), @(10, 16, 10))
}

foreach ($direction in $arms.Keys) {
	foreach ($state in @(@('off', $wireTexture), @('on', $wireLitTexture))) {
		$stateName = $state[0]
		$texture = $state[1]
		Write-Json (Join-Path $blockModels "wire_arm_${direction}_$stateName.json") ([ordered]@{
			ambientocclusion = $false
			textures         = [ordered]@{ particle = $texture; all = $texture }
			elements         = @(New-CubeElement -From $arms[$direction][0] -To $arms[$direction][1] -Texture '#all')
		})
	}
}

# 每个方块两套正面贴图：熄灭 / 通电（用贴图表示状态，而不是用发光）
$circuitModels = [ordered]@{
	and_gate = @('gate_and', 'gate_and_on')
	or_gate = @('gate_or', 'gate_or_on')
	not_gate = @('gate_not', 'gate_not_on')
	xor_gate = @('gate_xor', 'gate_xor_on')
	limiter = @('gate_limiter', 'gate_limiter_on')
	comparator = @('gate_comparator', 'gate_comparator_on')
	clock = @('gate_clock', 'gate_clock_on')
	scanner = @('gate_scanner', 'gate_scanner_on')
	amplifier = @('gate_amplifier', 'gate_amplifier_on')
	brightness_sensor = @('gate_brightness', 'gate_brightness_on')
}

foreach ($name in $circuitModels.Keys) {
	$textures = $circuitModels[$name]
	foreach ($variant in @(@('', $textures[0]), @('_on', $textures[1]))) {
		$suffix = $variant[0]
		$frontTexture = "${modId}:block/$($variant[1])"
		Write-Json (Join-Path $blockModels "$name$suffix.json") ([ordered]@{
			parent   = 'minecraft:block/cube'
			textures = [ordered]@{
				particle = "${modId}:block/gate_side"
				down     = "${modId}:block/gate_base"
				up       = "${modId}:block/gate_top"
				north    = $frontTexture
				east     = "${modId}:block/gate_side"
				south    = "${modId}:block/gate_side"
				west     = "${modId}:block/gate_side"
			}
		})
	}
	Write-Json (Join-Path $itemModels "$name.json") ([ordered]@{ parent = "${modId}:block/$name" })
}

Write-Json (Join-Path $itemModels 'wire.json') ([ordered]@{ parent = "${modId}:block/wire_core_off" })

# 数据方块（常量 / 字符串）：没有 facing / power 属性，所以是"一个 cube 模型 + 一个空 variant"。
$valueBlocks = [ordered]@{
	constant     = 'gate_constant'
	string_value = 'gate_string'
}

foreach ($name in $valueBlocks.Keys) {
	$valueTexture = "${modId}:block/$($valueBlocks[$name])"
	Write-Json (Join-Path $blockModels "$name.json") ([ordered]@{
		parent   = 'minecraft:block/cube'
		textures = [ordered]@{
			particle = $valueTexture
			down     = $valueTexture
			up       = $valueTexture
			north    = $valueTexture
			east     = $valueTexture
			south    = $valueTexture
			west     = $valueTexture
		}
	})
	Write-Json (Join-Path $itemModels "$name.json") ([ordered]@{ parent = "${modId}:block/$name" })
	# 没有属性时用 variants 需要空字符串键（部分 JSON 读取器不支持），因此改用一个无条件的 multipart。
	Write-Json (Join-Path $blockstates "$name.json") ([ordered]@{
		multipart = @(
			[ordered]@{ apply = [ordered]@{ model = "${modId}:block/$name" } }
		)
	})
}

# 变量方块：一个方块存一个 int / str / null，贴图按 BlockState 的 kind 切换。
$variableModels = [ordered]@{
	unset = 'gate_variable_unset'
	null  = 'gate_variable_null'
	int   = 'gate_variable_int'
	str   = 'gate_variable_str'
}

foreach ($kind in $variableModels.Keys) {
	$variableTexture = "${modId}:block/$($variableModels[$kind])"
	Write-Json (Join-Path $blockModels "variable_$kind.json") ([ordered]@{
		parent   = 'minecraft:block/cube'
		textures = [ordered]@{
			particle = $variableTexture
			down     = $variableTexture
			up       = $variableTexture
			north    = $variableTexture
			east     = $variableTexture
			south    = $variableTexture
			west     = $variableTexture
		}
	})
}

# 物品图标用"未设置"那套（物品栏里没有世界状态可看）。
Write-Json (Join-Path $itemModels 'variable.json') ([ordered]@{ parent = "${modId}:block/variable_unset" })

$variableParts = foreach ($kind in $variableModels.Keys) {
	[ordered]@{
		when  = [ordered]@{ kind = $kind }
		apply = [ordered]@{ model = "${modId}:block/variable_$kind" }
	}
}
Write-Json (Join-Path $blockstates 'variable.json') ([ordered]@{ multipart = @($variableParts) })

# 悬空方块：完整立方体，但贴图中间透明、只画一圈白线；透明靠客户端的 cutout 渲染层。
$hoverTexture = "${modId}:block/hover_frame"
# 注意：必须有 parent 或 elements，否则烘焙出来是**空模型**（方块不可见，只剩选择框）。
Write-Json (Join-Path $blockModels 'hover_block.json') ([ordered]@{
	parent           = 'minecraft:block/cube'
	# 1.20.4 不认模型里的 render_type（那是 1.20.5+ 的字段），渲染层由客户端 BlockRenderLayerMap 决定。
	ambientocclusion = $true
	textures         = [ordered]@{
		particle = $hoverTexture
		down     = $hoverTexture
		up       = $hoverTexture
		north    = $hoverTexture
		east     = $hoverTexture
		south    = $hoverTexture
		west     = $hoverTexture
	}
})
Write-Json (Join-Path $itemModels 'hover_block.json') ([ordered]@{ parent = "${modId}:block/hover_block" })
Write-Json (Join-Path $blockstates 'hover_block.json') ([ordered]@{
	multipart = @(
		[ordered]@{ apply = [ordered]@{ model = "${modId}:block/hover_block" } }
	)
})
# 隐形告示牌：方块本体全透明（cutout 丢弃所有像素），文字由客户端 BER 画；
# 物品图标用单独的 item 贴图（方块模型全透明的话，物品栏里会什么都看不到）。
# 隐形告示牌：**空模型**（没有 parent、也没有 elements）。
# 空模型会被烘焙成"没有任何面"，方块本体彻底不渲染——这正是"隐形"想要的效果；
# 比"全透明贴图"更稳：不依赖渲染层的 alpha 处理（solid 层会把透明像素画成黑块）。
$hiddenSignTexture = "${modId}:block/hidden_sign"
Write-Json (Join-Path $blockModels 'hidden_sign.json') ([ordered]@{
	textures = [ordered]@{ particle = $hiddenSignTexture }
})
Write-Json (Join-Path $itemModels 'hidden_sign.json') ([ordered]@{
	parent   = 'minecraft:item/generated'
	textures = [ordered]@{ layer0 = "${modId}:item/hidden_sign" }
})
Write-Json (Join-Path $blockstates 'hidden_sign.json') ([ordered]@{
	multipart = @(
		[ordered]@{ apply = [ordered]@{ model = "${modId}:block/hidden_sign" } }
	)
})

# 数据线：箭头画在东西两个侧面（水平指向前方），顶面保持纯色壳——
# 顶面箭头在斜视时会被透视压斜，所以方向标记只放在侧面。
foreach ($variant in @(
		@('', 'data_wire_arrow_right', 'data_wire_arrow_left', 'gate_data_wire'),
		@('_error', 'data_wire_arrow_error_right', 'data_wire_arrow_error_left', 'gate_data_wire_error'))) {
	$suffix = $variant[0]
	$rightTexture = "${modId}:block/$($variant[1])"
	$leftTexture = "${modId}:block/$($variant[2])"
	$shellTexture = "${modId}:block/$($variant[3])"
	Write-Json (Join-Path $blockModels "data_wire$suffix.json") ([ordered]@{
		parent   = 'minecraft:block/cube'
		textures = [ordered]@{
			particle = $shellTexture
			down     = "${modId}:block/gate_base"
			up       = $shellTexture
			north    = $shellTexture
			east     = $rightTexture
			south    = $shellTexture
			west     = $leftTexture
		}
	})
}
Write-Json (Join-Path $itemModels 'data_wire.json') ([ordered]@{ parent = "${modId}:block/data_wire" })

# facing → y 旋转：模型正面（front）落在 north 面上，多处共用这张表。
$facingRotations = [ordered]@{
	north = 0
	east  = 90
	south = 180
	west  = 270
}

$dataWireParts = foreach ($facing in $facingRotations.Keys) {
	[ordered]@{
		when  = [ordered]@{ facing = $facing; error = 'false' }
		apply = [ordered]@{ model = "${modId}:block/data_wire"; y = $facingRotations[$facing] }
	}
	[ordered]@{
		when  = [ordered]@{ facing = $facing; error = 'true' }
		apply = [ordered]@{ model = "${modId}:block/data_wire_error"; y = $facingRotations[$facing] }
	}
}
Write-Json (Join-Path $blockstates 'data_wire.json') ([ordered]@{ multipart = @($dataWireParts) })

# 纯色方块：白色贴图 + tintindex（客户端按方块实体颜色着色），shade 只是逼客户端重建网格的占位属性。
$colorTexture = "${modId}:block/color_block"
$colorFaces = [ordered]@{}
foreach ($face in @('down', 'up', 'north', 'south', 'west', 'east')) {
	$colorFaces[$face] = [ordered]@{ texture = '#all'; tintindex = 0 }
}
Write-Json (Join-Path $blockModels 'color_block.json') ([ordered]@{
	textures = [ordered]@{ particle = $colorTexture; all = $colorTexture }
	elements = @([ordered]@{ from = @(0, 0, 0); to = @(16, 16, 16); faces = $colorFaces })
})
Write-Json (Join-Path $itemModels 'color_block.json') ([ordered]@{ parent = "${modId}:block/color_block" })

$colorParts = foreach ($shade in 0..3) {
	[ordered]@{
		when  = [ordered]@{ shade = "$shade" }
		apply = [ordered]@{ model = "${modId}:block/color_block" }
	}
}
Write-Json (Join-Path $blockstates 'color_block.json') ([ordered]@{ multipart = @($colorParts) })

# 电路终端：普通物品，用扁平图标
Write-Json (Join-Path $itemModels 'terminal.json') ([ordered]@{
	parent   = 'minecraft:item/generated'
	textures = [ordered]@{ layer0 = "${modId}:item/terminal" }
})

# ---------------------------------------------------------------- 方块状态

$wireParts = New-Object System.Collections.ArrayList

[void]$wireParts.Add([ordered]@{
	when  = [ordered]@{ powered = 'false' }
	apply = [ordered]@{ model = "${modId}:block/wire_core_off" }
})
[void]$wireParts.Add([ordered]@{
	when  = [ordered]@{ powered = 'true' }
	apply = [ordered]@{ model = "${modId}:block/wire_core_on" }
})

foreach ($direction in $arms.Keys) {
	foreach ($state in @(@('off', 'false'), @('on', 'true'))) {
		$stateName = $state[0]
		$powered = $state[1]
		[void]$wireParts.Add([ordered]@{
			when  = [ordered]@{ powered = $powered; $direction = 'true' }
			apply = [ordered]@{ model = "${modId}:block/wire_arm_${direction}_$stateName" }
		})
	}
}

Write-Json (Join-Path $blockstates 'wire.json') ([ordered]@{ multipart = $wireParts.ToArray() })

foreach ($name in $circuitModels.Keys) {
	$parts = foreach ($facing in $facingRotations.Keys) {
		# 熄灭：power = 0
		[ordered]@{
			when  = [ordered]@{ facing = $facing; power = '0' }
			apply = [ordered]@{ model = "${modId}:block/$name"; y = $facingRotations[$facing] }
		}
		# 通电：power = 1..15（multipart 不能用比较，所以用取值并集）
		[ordered]@{
			when  = [ordered]@{ facing = $facing; power = '1|2|3|4|5|6|7|8|9|10|11|12|13|14|15' }
			apply = [ordered]@{ model = "${modId}:block/${name}_on"; y = $facingRotations[$facing] }
		}
	}
	Write-Json (Join-Path $blockstates "$name.json") ([ordered]@{ multipart = @($parts) })
}

# ---------------------------------------------------------------- 语言

$enUs = [ordered]@{
	"itemGroup.$modId.circuit" = 'Circuit Redstone - Logic'
	"block.$modId.wire" = 'Redstone Wire Bus'
	"block.$modId.and_gate" = 'AND Gate'
	"block.$modId.or_gate" = 'OR Gate'
	"block.$modId.not_gate" = 'NOT Gate'
	"block.$modId.xor_gate" = 'XOR Gate'
	"block.$modId.limiter" = 'Signal Limiter'
	"block.$modId.comparator" = 'Comparator'
	"block.$modId.clock" = 'Clock'
	"block.$modId.scanner" = 'Signal Scanner'
	"block.$modId.amplifier" = 'Signal Amplifier'
	"block.$modId.brightness_sensor" = 'Brightness Sensor'
	"block.$modId.constant" = 'Constant'
	"block.$modId.string_value" = 'String'
	"block.$modId.variable" = 'Variable'
	"block.$modId.data_wire" = 'Data Wire'
	"block.$modId.color_block" = 'Solid Color Block'
	"block.$modId.hover_block" = 'Hover Block'
	"block.$modId.hidden_sign" = 'Hidden Sign'
	"block.$modId.hidden_sign.placeholder" = 'Type something...'
	"item.$modId.terminal" = 'Circuit Terminal'
	"command.$modId.terminal.players_only" = 'Only a player can use this command.'
	"command.$modId.terminal.need_terminal" = 'You need a Circuit Terminal in your inventory.'
	"command.$modId.terminal.unknown" = 'Unknown circuit id: %s'
	"command.$modId.terminal.need_wires" = 'Not enough wires: needs %s, you have %s.'
	"command.$modId.terminal.granted" = 'Gave %sx %s for %s wires.'
	"command.$modId.terminal.exchange_terminal" = 'Error: you cannot exchange a Circuit Terminal for a Circuit Terminal.'
	"command.$modId.terminal.inventory_full" = 'Inventory is full; the exchanged item cannot fit.'
	"command.$modId.terminal.amount_too_small" = 'Amount must be at least %s (one batch = %s items)'
	"command.$modId.terminal.amount_not_multiple" = 'Amount must be a multiple of %s'
	"command.$modId.set.players_only" = 'Only a player can aim at a block; console must use /circuit-set <x> <y> <z> <value>.'
	"command.$modId.set.no_target" = 'Aim at a Constant, String or Variable block first.'
	"command.$modId.set.not_settable" = 'That block is not a Constant, String or Variable block.'
	"command.$modId.set.bad_number" = 'Not an integer: %s'
	"command.$modId.set.bad_color" = 'Not a hex colour (#RRGGBB): %s'
	"command.$modId.set.bad_period" = 'The frequency must be an integer >= 1 (ticks): %s'
	"command.$modId.set.clock" = '%s (%s) = one high pulse every %s ticks'
	"command.$modId.set.too_long" = 'Text is too long: at most %s characters.'
	"command.$modId.set.ok" = '%s (%s) = %s'
	"command.$modId.set.variable_need_equals" = 'A Variable takes "type=value", not a bare value: %s (try null, int=42 or str="hi")'
	"command.$modId.set.variable_bad_kind" = 'Unknown variable type: %s (use %s)'
	"command.$modId.set.variable_need_quotes" = 'Text values need double quotes around them: %s'
	"command.$modId.set.variable_null_takes_no_value" = 'null takes no value: write /circuit-set null (got %s)'
	"command.$modId.read.players_only" = 'Only a player can aim at a block; console must use /circuit-read <x> <y> <z>.'
	"command.$modId.read.no_target" = 'Aim at a Constant, String or Variable block first.'
	"command.$modId.read.not_data" = 'Nothing to read there: not a Constant, String or Variable block.'
	"command.$modId.read.result" = '%s (%s): %s'
	"command.$modId.read.variable_unset" = '(not set)'
	"tooltip.$modId.variable.current" = 'Stored: %s'
	"command.$modId.debug.not_circuit" = 'That is not a circuit block: aim at a wire or a gate, or point the coordinates at one.'
	"command.$modId.debug.no_network" = 'Nothing to simulate here: neither this block nor its six neighbours is a wire.'
	"command.$modId.debug.info_line" = '%s (%s) [%s]'
	"command.$modId.debug.info_empty" = 'No circuit blocks in the loaded part of this world.'
	"command.$modId.debug.chart_started" = 'run-chart: recording %s ticks over %s blocks...'
	"command.$modId.debug.chart_busy" = 'You already have a run-chart recording in progress.'
	"command.$modId.debug.chart_too_big" = 'Region too large: at most %s blocks.'
	"command.$modId.debug.chart_failed" = 'Could not render or save the chart (see the log).'
	"command.$modId.debug.chart_done" = 'Chart saved: %s (%s ticks, %s changes)'
	"command.$modId.debug.chart_empty" = 'No block changed during the recording, so the chart has no rows.'
	"command.$modId.debug.chart_truncated" = 'Too many changes; only the first %s changes per block are in the chart.'
	"command.$modId.debug.chart_saved_as" = 'Chart also saved to: %s'
	"command.$modId.debug.chart_dialog" = 'A save-as dialog was opened: pick a file to save the chart there.'
	"command.$modId.debug.chart_text" = 'Plain-text change list: %s'
	"command.$modId.debug.chart_help" = 'Reading: one row = one block, one column = one tick, colour/number = state; the .txt lists every change.'
	"command.$modId.debug.chart_dialog_game" = 'A save dialog was opened in-game: type a file name and press Save.'
	"command.$modId.debug.video_started" = 'run-video: recording %s ticks over %s blocks...'
	"command.$modId.debug.video_saved" = 'Video saved: %s (%s ticks, %s changes)'
	"command.$modId.debug.video_failed" = 'Could not render or save the video (see the log).'
	"command.$modId.debug.video_help" = 'Watching: each frame = one tick, top view, colour = block state; the .txt lists every change.'
	"command.$modId.debug.video_mp4_saved" = 'MP4 saved as well: %s (%s frames)'
	"command.$modId.debug.video_mp4_failed" = 'MP4 encoding failed; the GIF is still saved (see the log).'
	"command.$modId.debug.video_mp4_unavailable" = 'No ffmpeg on this machine''s PATH, so only the GIF was written (install ffmpeg for MP4).'
	"command.$modId.debug.video_too_long" = 'run-video is limited to %s ticks.'
	"screen.$modId.chart_save.title" = 'Save run-chart as'
	"screen.$modId.chart_save.title_video" = 'Save run-video as'
	"screen.$modId.chart_save.folder_label" = 'Save folder (absolute, or relative to the game dir)'
	"screen.$modId.chart_save.file_label" = 'File name'
	"screen.$modId.chart_save.hint" = 'Relative folders are resolved against the game dir; Cancel keeps the auto-saved copy.'
	"screen.$modId.chart_save.save" = 'Save'
	"screen.$modId.chart_save.cancel" = 'Cancel'
	"screen.$modId.chart_save.invalid" = 'Invalid file name (no / \ : and no ..)'
	"screen.$modId.chart_save.invalid_folder" = 'Invalid folder: %s'
	"screen.$modId.chart_save.failed" = 'Failed: %s'
	"screen.$modId.chart_save.system" = 'System dialog...'
	"screen.$modId.chart_save.no_system_dialog" = 'The system dialog is unavailable in this JVM; use Save above.'
	"command.$modId.debug.started" = 'Simulation id: %s (stop it with /circuit-debug test stop %s)'
	"command.$modId.debug.change" = '%s (%s) state changed from %s to %s at %s'
	"command.$modId.debug.finished" = 'Simulation finished: %s, %s ticks, %s changes'
	"command.$modId.debug.dead_loop" = '[WARNING] Dead loop detected! Period = %s ticks!'
	"command.$modId.debug.error" = 'Simulation aborted: %s'
	"command.$modId.debug.stopped" = 'Stopped simulation %s'
	"command.$modId.debug.stopped_all" = 'Stopped %s of your simulations'
	"command.$modId.debug.no_such_id" = 'No such simulation id: %s'
	"command.$modId.debug.too_many" = 'Too many simulations running (max %s), try again later'
	"command.$modId.debug.reason.stable" = 'stable'
	"command.$modId.debug.reason.dead_loop" = 'dead loop'
	"command.$modId.debug.reason.cancelled" = 'cancelled'
	"command.$modId.debug.reason.tick_limit" = 'tick limit reached'
	"command.$modId.debug.reason.budget_limit" = 'time budget exceeded'
	"command.$modId.debug.reason.error" = 'error'
}
$zhCn = [ordered]@{
	"itemGroup.$modId.circuit" = '红石数字逻辑'
	"block.$modId.wire" = '导线'
	"block.$modId.and_gate" = '与门'
	"block.$modId.or_gate" = '或门'
	"block.$modId.not_gate" = '非门'
	"block.$modId.xor_gate" = '异或门'
	"block.$modId.limiter" = '限制信号器'
	"block.$modId.comparator" = '比较器'
	"block.$modId.clock" = '时钟'
	"block.$modId.scanner" = '输出任意信号器'
	"block.$modId.amplifier" = '信号增强器'
	"block.$modId.brightness_sensor" = '亮度传感器'
	"block.$modId.constant" = '常量'
	"block.$modId.string_value" = '字符串'
	"block.$modId.variable" = '变量'
	"block.$modId.data_wire" = '数据线'
	"block.$modId.color_block" = '纯色方块'
	"block.$modId.hover_block" = '悬空方块'
	"block.$modId.hidden_sign" = '隐形告示牌'
	"block.$modId.hidden_sign.placeholder" = '请输入文本...'
	"item.$modId.terminal" = '电路终端'
	"command.$modId.terminal.players_only" = '只有玩家可以使用该指令。'
	"command.$modId.terminal.need_terminal" = '你需要随身带着「电路终端」。'
	"command.$modId.terminal.unknown" = '未知的电路 id：%s'
	"command.$modId.terminal.need_wires" = '导线不够：需要 %s 个，你只有 %s 个。'
	"command.$modId.terminal.granted" = '已获得 %s 个 %s（消耗 %s 个导线）。'
	"command.$modId.terminal.exchange_terminal" = '错误：你不能用电路终端兑换电路终端。'
	"command.$modId.terminal.inventory_full" = '背包已满，无法容纳兑换的物品。'
	"command.$modId.terminal.amount_too_small" = '数量至少 %s 个（一批 = %s 个）'
	"command.$modId.terminal.amount_not_multiple" = '数量必须是 %s 的倍数'
	"command.$modId.set.players_only" = '只有玩家可以瞄准方块；控制台请用 /circuit-set <x> <y> <z> <值>。'
	"command.$modId.set.no_target" = '请先把准星对准常量、字符串或变量方块。'
	"command.$modId.set.not_settable" = '这个方块不是常量、字符串或变量。'
	"command.$modId.set.bad_number" = '不是整数：%s'
	"command.$modId.set.bad_color" = '不是十六进制颜色（#RRGGBB）：%s'
	"command.$modId.set.bad_period" = '频率必须是 >= 1 的整数（tick）：%s'
	"command.$modId.set.clock" = '%s (%s) = 每 %s tick 输出一次高电平'
	"command.$modId.set.too_long" = '文本太长：最多 %s 个字符。'
	"command.$modId.set.ok" = '%s (%s) = %s'
	"command.$modId.set.variable_need_equals" = '变量要写成"类型=值"，不能只给一个值：%s（例如 null、int=42 或 str="hi"）'
	"command.$modId.set.variable_bad_kind" = '未知的变量类型：%s（可用：%s）'
	"command.$modId.set.variable_need_quotes" = '文本值必须用一对双引号包住：%s'
	"command.$modId.set.variable_null_takes_no_value" = 'null 不需要值：直接写 /circuit-set null（你写的是 %s）'
	"command.$modId.read.players_only" = '只有玩家可以瞄准方块；控制台请用 /circuit-read <x> <y> <z>。'
	"command.$modId.read.no_target" = '请先把准星对准常量、字符串或变量方块。'
	"command.$modId.read.not_data" = '那里没有可读的数据：不是常量、字符串或变量方块。'
	"command.$modId.read.result" = '%s (%s)：%s'
	"command.$modId.read.variable_unset" = '（未设置）'
	"tooltip.$modId.variable.current" = '当前值：%s'
	"command.$modId.debug.not_circuit" = '这里不是电路方块：请对准导线或逻辑门，或把坐标指向它们。'
	"command.$modId.debug.no_network" = '这里没有可推演的网络：该方块本身和它六个相邻方向都不是导线。'
	"command.$modId.debug.info_line" = '%s (%s) [%s]'
	"command.$modId.debug.info_empty" = '当前世界已加载区块里没有电路方块。'
	"command.$modId.debug.chart_started" = 'run-chart：开始记录 %s tick、%s 个方块……'
	"command.$modId.debug.chart_busy" = '你已经有一个 run-chart 记录在进行中了。'
	"command.$modId.debug.chart_too_big" = '区域太大：最多 %s 个方块。'
	"command.$modId.debug.chart_failed" = '图表渲染或保存失败（看日志）。'
	"command.$modId.debug.chart_done" = '图表已保存：%s（%s tick，%s 次变化）'
	"command.$modId.debug.chart_empty" = '记录期间没有任何方块变化，所以图里没有行。'
	"command.$modId.debug.chart_truncated" = '变化太多：每个方块只保留了前 %s 次变化。'
	"command.$modId.debug.chart_saved_as" = '图表也保存到了：%s'
	"command.$modId.debug.chart_dialog" = '已弹出"另存为"对话框：选一个文件即可保存到那里。'
	"command.$modId.debug.chart_text" = '逐条变化清单（纯文本）：%s'
	"command.$modId.debug.chart_help" = '看图：一行一个方块、一列一个 tick、颜色/数字表示状态；同目录 .txt 是逐条变化清单。'
	"command.$modId.debug.chart_dialog_game" = '已弹出游戏内"另存为"对话框：填个文件名按"保存"即可。'
	"command.$modId.debug.video_started" = 'run-video：开始记录 %s tick、%s 个方块……'
	"command.$modId.debug.video_saved" = '视频已保存：%s（%s tick，%s 次变化）'
	"command.$modId.debug.video_failed" = '视频渲染或保存失败（看日志）。'
	"command.$modId.debug.video_help" = '看视频：一帧一个 tick、俯视图、颜色表示状态；同目录 .txt 是逐条变化清单。'
	"command.$modId.debug.video_mp4_saved" = 'MP4 也保存到了：%s（%s 帧）'
	"command.$modId.debug.video_mp4_failed" = 'MP4 编码失败，GIF 仍然可用（看日志）。'
	"command.$modId.debug.video_mp4_unavailable" = '这台机器 PATH 里没有 ffmpeg，所以只生成了 GIF（想要 MP4 就装个 ffmpeg）。'
	"command.$modId.debug.video_too_long" = 'run-video 最多 %s tick。'
	"screen.$modId.chart_save.title" = '另存运行图'
	"screen.$modId.chart_save.title_video" = '另存视频'
	"screen.$modId.chart_save.folder_label" = '保存目录（可填绝对路径，或相对游戏目录的路径）'
	"screen.$modId.chart_save.file_label" = '文件名'
	"screen.$modId.chart_save.hint" = '相对路径以游戏目录为基准；取消则保留已自动保存的那一份。'
	"screen.$modId.chart_save.save" = '保存'
	"screen.$modId.chart_save.cancel" = '取消'
	"screen.$modId.chart_save.invalid" = '文件名不合法（不能含 / \ : 或 ..）'
	"screen.$modId.chart_save.invalid_folder" = '目录不合法：%s'
	"screen.$modId.chart_save.failed" = '保存失败：%s'
	"screen.$modId.chart_save.system" = '系统对话框…'
	"screen.$modId.chart_save.no_system_dialog" = '这个 JVM 里系统对话框不可用，请用上面的"保存"。'
	"command.$modId.debug.started" = '模拟 ID：%s（用 /circuit-debug test stop %s 停止）'
	"command.$modId.debug.change" = '%s (%s) 状态从 %s 变为 %s 在 %s'
	"command.$modId.debug.finished" = '模拟结束：%s，%s tick，%s 次变化'
	"command.$modId.debug.dead_loop" = '[警告] 检测到死循环！周期为 %s 个 Tick！'
	"command.$modId.debug.error" = '模拟异常终止：%s'
	"command.$modId.debug.stopped" = '已停止模拟 %s'
	"command.$modId.debug.stopped_all" = '已停止你的 %s 个模拟'
	"command.$modId.debug.no_such_id" = '没有找到该模拟 ID：%s'
	"command.$modId.debug.too_many" = '同时运行的模拟太多（上限 %s），请稍后再试'
	"command.$modId.debug.reason.stable" = '已稳定'
	"command.$modId.debug.reason.dead_loop" = '死循环'
	"command.$modId.debug.reason.cancelled" = '已取消'
	"command.$modId.debug.reason.tick_limit" = '达到 tick 上限'
	"command.$modId.debug.reason.budget_limit" = '超出时间预算'
	"command.$modId.debug.reason.error" = '异常终止'
}

# ---------------------------------------------------------------- Tooltip（一行一个键）

# MC 的物品提示框是"一行一个 Text 组件"，把 \n 塞进同一条文本在 1.20.4 里不会换行
# （中文没有空格可断行，整条会顶满一行）。所以这里把说明按行拆成 <id>.0 / .1 / … 多个键，
# 由 Tooltips.appendLines() 逐行追加组件。

$tooltipsEn = [ordered]@{
	wire = @(
		'Connects six ways: wire / redstone / blocks'
		'Binary signal, no decay'
		'Redstone -> wire: level > 0 is HIGH'
		'Wire -> redstone: HIGH = 15'
		'Adjacent dust is read at its real level'
	)
	and_gate = @(
		'Port A = back'
		'Port B = left/right (left wins)'
		'Output = A AND B on the facing face'
		'Output is converted by DAC to 0/15'
		'Zero delay: same-tick settle'
	)
	or_gate = @(
		'Port A = back'
		'Port B = left/right (left wins)'
		'Output = A OR B on the facing face'
		'Output is converted by DAC to 0/15'
		'Zero delay: same-tick settle'
	)
	not_gate = @(
		'Port A = back'
		'Output = NOT A on the facing face'
		'Output is converted by DAC to 0/15'
		'Zero delay: same-tick settle'
	)
	xor_gate = @(
		'Port A = back'
		'Port B = left/right (left wins)'
		'Output = A XOR B on the facing face'
		'Output is converted by DAC to 0/15'
		'Zero delay: same-tick settle'
	)
	limiter = @(
		'Port A = back'
		'Port B = left/right (left wins)'
		'Output = min(A, B), level 0..15'
		'Analog minimum with vanilla dust'
		'Readable by a vanilla comparator'
	)
	comparator = @(
		'Back = Constant (operand A)'
		'Right = Constant (operand B)'
		'Left = redstone level picks the mode'
		'1=less 2=less-or-equal 3=equal'
		'4=greater-or-equal 5=greater'
		'Pattern holds -> high bit (15), else 0'
		'Missing Constant or bad mode -> 0'
		'Compares the raw stored integers'
	)
	clock = @(
		'Aim at it and run /circuit-set <ticks>'
		'Outputs one high pulse every N ticks'
		'Pulse = 1 tick of 15, rest is 0'
		'N=1 means always high'
		'Output face = FACING'
		'Cycle phase is saved with the world'
		'No period set -> output stays 0'
	)
	scanner = @(
		'Scans 15 blocks left/right/up'
		'Counts non-air blocks'
		'Up wins if it sees anything'
		'Otherwise the larger side wins'
		'Rescans every 10 ticks'
		'Adjacent changes update instantly'
		'Dust, water and leaves all count'
		'Logs the three counts on change'
	)
	amplifier = @(
		'Input (back) > 0 outputs 15'
		'Zero delay, replaces a repeater'
		'Output face = FACING'
	)
	brightness_sensor = @(
		'Output = brightest light around it (0..15)'
		'Uses sky and block light'
		'Open sky=15; sealed in blocks=0'
		'Polls every 10 ticks'
		'The block shades light itself'
	)
	constant = @(
		'Stores one integer'
		'Aim at it and run /circuit-set <number>'
		'Emits no redstone of its own'
		'A Scanner outputs this value'
		'  instead of counting blocks'
		'Scanner rule: up > left > right'
		'Nearest Constant in a ray wins'
		'Values outside 0..15 clamp'
	)
	string_value = @(
		'Stores one line of text'
		'Aim at it and run /circuit-set <text>'
		'Max text length: 256 chars'
		'Text is saved with the world'
		'No redstone behaviour yet'
		'A Scanner never reads it'
	)
	variable = @(
		'Stores ONE variable per block'
		'Aim at it: /circuit-set int=42'
		'Or: /circuit-set str="hello"'
		'Or: /circuit-set null  (empty value)'
		'Type can be changed any time'
		'Text values need double quotes'
		'null through a data wire: 0 / ""'
		'Emits no redstone of its own'
		'Data wire can read OR write it'
		'Keeps its value when broken and replaced'
	)
	data_wire = @(
		'Arrow shows the data direction'
		'Reads the datablock behind it'
		'Writes into the datablock in front'
		'Redstone level behind -> Constant value'
		'  (dust/lever/wire/gate output, 0..15)'
		'Constants and Strings convert both ways'
		'Non-numeric text into a Constant'
		'  shows the red error texture'
		'Chains relay up to 64 wires'
		'Transfers once per tick'
	)
	color_block = @(
		'Stores one RGB colour'
		'Placed as black (#000000)'
		'Aim at it: /circuit-set #RRGGBB'
		'#RRGGBB, RRGGBB and 0xRRGGBB all work'
		'White model tinted on the client'
		'No redstone or data-wire logic'
	)
	hover_block = @(
		'White frame: see through the middle'
		'Counts as a full block: redstone dust'
		'  and torches can sit on top of it'
		'Walk straight through it'
		'No redstone or data logic of its own'
		'4 wires -> 16 blocks via the terminal'
	)
	hidden_sign = @(
		'The block itself is invisible'
		'Only the text is drawn'
		'Aim at it: /circuit-set <text>'
		'Empty text shows a grey hint'
		'Write "" to clear it'
	)
	terminal = @(
		'Required for this command'
		'/circuit-terminal list'
		'  -> list every mod item'
		'/circuit-terminal get <id> [amount]'
		'  -> 4 wires per batch'
		'  -> 1 per batch (16 for Hover/Color)'
		'  -> amount must be whole batches'
		'Example: get and_gate 3'
	)
}

$tooltipsZh = [ordered]@{
	wire = @(
		'六向可连：导线/红石线/方块'
		'二进制信号，不衰减'
		'红石 → 导线：>0 即高'
		'导线 → 红石：高电平 = 15'
		'相邻红石线按真实强度读取'
	)
	and_gate = @(
		'端口：A = 后方'
		'端口：B = 左/右（左优先）'
		'输出 = A AND B（FACING 面）'
		'输出经 DAC 变 0 或 15'
		'零延迟：同游戏刻收敛'
	)
	or_gate = @(
		'端口：A = 后方'
		'端口：B = 左/右（左优先）'
		'输出 = A OR B（FACING 面）'
		'输出经 DAC 变 0 或 15'
		'零延迟：同游戏刻收敛'
	)
	not_gate = @(
		'端口：A = 后方'
		'输出 = NOT A（FACING 面）'
		'输出经 DAC 变 0 或 15'
		'零延迟：同游戏刻收敛'
	)
	xor_gate = @(
		'端口：A = 后方'
		'端口：B = 左/右（左优先）'
		'输出 = A XOR B（FACING 面）'
		'输出经 DAC 变 0 或 15'
		'零延迟：同游戏刻收敛'
	)
	limiter = @(
		'端口：A = 后方'
		'端口：B = 左/右（左优先）'
		'输出 = min(A, B)，0..15'
		'接原版红石线时为模拟取小'
		'输出可被比较器读取'
	)
	comparator = @(
		'后方 = 常量（操作数 A）'
		'右方 = 常量（操作数 B）'
		'左方 = 红石等级决定比较模式'
		'1=小于 2=小于等于 3=等于'
		'4=大于等于 5=大于'
		'成立输出高 bit（15），否则 0'
		'缺常量或模式非法 → 输出 0'
		'比较用常量里的原始整数'
	)
	clock = @(
		'瞄准它执行 /circuit-set <tick>'
		'每 N tick 输出一次高电平'
		'脉冲长度 = 1 tick（15），其余时间 0'
		'N=1 就是一直高电平'
		'输出面 = FACING'
		'计时相位随世界存档保存'
		'没设置频率时不输出'
	)
	scanner = @(
		'左/右/上各扫描 15 格'
		'统计非空气方块数量'
		'上方有则取上方'
		'否则左右取较大者'
		'每 10 tick 重扫一次'
		'相邻变化时立即重算'
		'红石线/水/树叶都会算进去'
		'电平变化时日志打印计数'
	)
	amplifier = @(
		'输入（后方）> 0 即输出 15'
		'零延迟，替代中继器'
		'输出面 = FACING'
	)
	brightness_sensor = @(
		'输出 = 周围最亮的光照 0..15'
		'含天空光与方块光'
		'露天=15；被方块包住=0'
		'每 10 tick 轮询一次'
		'方块自身遮光，天光不漏过'
	)
	constant = @(
		'保存一个整数'
		'瞄准它执行 /circuit-set <数字>'
		'自身不输出红石信号'
		'扫描器会输出它保存的值'
		'  而不是统计方块数量'
		'扫描优先级：上 > 左 > 右'
		'同方向取最近的常量'
		'超出 0..15 会被夹取'
	)
	string_value = @(
		'保存一行文本'
		'瞄准它执行 /circuit-set <文本>'
		'文本最长 256 个字符'
		'文本会随世界存档保存'
		'暂不参与红石逻辑'
		'扫描器不会把它当常量'
	)
	variable = @(
		'一个方块存一个变量'
		'瞄准它：/circuit-set int=42'
		'或者：/circuit-set str="你好"'
		'或者：/circuit-set null（空值）'
		'类型可以随时改'
		'文本值必须用双引号包住'
		'null 走数据线：常量得 0、字符串得 ""'
		'自身不发射红石信号'
		'数据线能读它，也能写它'
		'挖掉再放下，值还在'
	)
	data_wire = @(
		'箭头表示数据流动方向'
		'读取背面的常量/字符串'
		'写入正面的常量/字符串'
		'背面是红石信号时按等级写入常量'
		'  （红石线/拉杆/导线/门的输出 0..15）'
		'常量与字符串可双向转换'
		'文本不是数字却写进常量'
		'  会显示红色错误贴图'
		'最多串联 64 根数据线'
		'每 tick 传输一次'
	)
	color_block = @(
		'保存一个 RGB 颜色'
		'放下时是黑色 #000000'
		'瞄准它执行 /circuit-set #RRGGBB'
		'支持 #RRGGBB / RRGGBB / 0xRRGGBB'
		'客户端给白色模型着色'
		'不参与红石与数据线逻辑'
	)
	hover_block = @(
		'中间透明、一圈白框：能看穿也看得见'
		'但算完整方块：红石粉、火把'
		'  等需要支撑的方块可以放在上面'
		'玩家可以直接穿过去'
		'自身不参与红石或数据逻辑'
		'4 导线换 16 个（电路终端）'
	)
	hidden_sign = @(
		'方块本体是不可见的'
		'只会画出文字'
		'瞄准它：/circuit-set <文字>'
		'文本为空时显示灰色提示'
		'输入 "" 可以清空'
	)
	terminal = @(
		'持有它才能用该指令'
		'/circuit-terminal list'
		'  → 列出本模组物品'
		'/circuit-terminal get <id> [数量]'
		'  → 每批 4 个导线'
		'  → 一批 = 1 个（悬空/纯色 16 个）'
		'  → 数量必须是整批'
		'例：get and_gate 3'
	)
}

foreach ($id in $tooltipsEn.Keys) {
	$lines = $tooltipsEn[$id]
	for ($index = 0; $index -lt $lines.Count; $index++) {
		$enUs["tooltip.$modId.$id.$index"] = $lines[$index]
	}
}
foreach ($id in $tooltipsZh.Keys) {
	$lines = $tooltipsZh[$id]
	for ($index = 0; $index -lt $lines.Count; $index++) {
		$zhCn["tooltip.$modId.$id.$index"] = $lines[$index]
	}
}

Write-Json (Join-Path $langDir 'en_us.json') $enUs
Write-Json (Join-Path $langDir 'zh_cn.json') $zhCn

# ---------------------------------------------------------------- 配方

function Write-ShapelessRecipe {
	param([string]$Name, [array]$Ingredients, [string]$Result)
	$items = foreach ($ingredient in $Ingredients) {
		[ordered]@{ item = $ingredient }
	}
	Write-Json (Join-Path $recipes "$Name.json") ([ordered]@{
		type        = 'minecraft:crafting_shapeless'
		ingredients = @($items)
		result      = [ordered]@{ item = $Result; count = 1 }
	})
}

function Write-ShapedRecipe {
	param([string]$Name, [array]$Pattern, [hashtable]$Key, [string]$Result)
	$keyJson = [ordered]@{}
	foreach ($symbol in $Key.Keys) {
		$keyJson[$symbol] = [ordered]@{ item = $Key[$symbol] }
	}
	Write-Json (Join-Path $recipes "$Name.json") ([ordered]@{
		type    = 'minecraft:crafting_shaped'
		pattern = @($Pattern)
		key     = $keyJson
		result  = [ordered]@{ item = $Result; count = 1 }
	})
}

# 设计约定：**只有导线（红石粉互转）和电路终端有工作台配方**。
# 其余方块一律不给工作台配方——它们要走「电路终端」的 `/circuit-terminal get <id>` 花导线换取，
# 否则终端就失去意义了。
Write-ShapelessRecipe -Name 'wire' -Ingredients @('minecraft:redstone') -Result "${modId}:wire"
Write-ShapelessRecipe -Name 'redstone_from_wire' -Ingredients @("${modId}:wire") -Result 'minecraft:redstone'

# 电路终端：四角红石、四边导线、中心工作台
Write-ShapedRecipe -Name 'terminal' -Pattern @('RWR', 'WCW', 'RWR') -Key @{
	R = 'minecraft:redstone'
	W = "${modId}:wire"
	C = 'minecraft:crafting_table'
} -Result "${modId}:terminal"

# 清理历史上被误加的配方（其余方块应当走电路终端指令换取），只保留上面 3 条；
# 同时清掉当时配套加的"配方书解锁" advancement（本模组不需要）。
$keptRecipes = @('wire', 'redstone_from_wire', 'terminal')
foreach ($file in (Get-ChildItem -Path $recipes -File -Filter *.json -ErrorAction SilentlyContinue)) {
	if ($keptRecipes -notcontains $file.BaseName) {
		Remove-Item -LiteralPath $file.FullName -Force
	}
}
$advancementsDir = Join-Path $resources "data\$modId\advancements"
if (Test-Path $advancementsDir) {
	Remove-Item -LiteralPath $advancementsDir -Recurse -Force
}

# ---------------------------------------------------------------- 掉落表
# 没有 loot table 的方块被挖掉后什么都不掉（生存模式等于白送方块），
# 所以每个方块都要有"掉自己"的 loot table。

$lootDir = Join-Path $resources "data\$modId\loot_tables\blocks"
New-Item -ItemType Directory -Force -Path $lootDir | Out-Null

$allBlockIds = @('wire') + @($circuitModels.Keys) + @($valueBlocks.Keys) + @('variable', 'data_wire', 'color_block', 'hover_block', 'hidden_sign')
foreach ($name in $allBlockIds) {
	# 变量方块的值存在方块实体里：掉落时用 copy_nbt 把 kind/number/text 塞进物品的 BlockEntityTag，
	# 这样"挖起来再放下"数据不会丢（放置时由 VariableBlockItem 读回来）。
	$entry = [ordered]@{ type = 'minecraft:item'; name = "${modId}:$name" }
	if ($name -eq 'variable') {
		$entry['functions'] = @(
			[ordered]@{
				function = 'minecraft:copy_nbt'
				source   = 'block_entity'
				ops      = @(
					[ordered]@{ source = 'kind'; target = 'BlockEntityTag.kind'; op = 'replace' }
					[ordered]@{ source = 'number'; target = 'BlockEntityTag.number'; op = 'replace' }
					[ordered]@{ source = 'text'; target = 'BlockEntityTag.text'; op = 'replace' }
				)
			}
		)
	}
	Write-Json (Join-Path $lootDir "$name.json") ([ordered]@{
		type  = 'minecraft:block'
		pools = @(
			[ordered]@{
				rolls       = 1
				bonus_rolls = 0
				entries     = @($entry)
				conditions  = @([ordered]@{ condition = 'minecraft:survives_explosion' })
			}
		)
	})
}

Write-Output ("assets generated under {0}" -f $assets)

