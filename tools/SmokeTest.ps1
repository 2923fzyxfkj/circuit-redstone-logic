# CircuitRedstone 运行时冒烟自测。
#
# 为什么分三轮：
#   1. 控制台命令在一次 tick 内全部执行，而本模组的求值调度在 tick 末尾（END_WORLD_TICK），
#      因此"放置"与"断言"必须落在不同的 tick；
#   2. 用 LF 命令文件 + `type` 喂给服务器（cmd 的 echo 会带 \r，服务器不认）。
#
# 用法：pwsh -File tools/SmokeTest.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'
$env:GRADLE_USER_HOME = (Join-Path $root '.gradle-home')

$smokeDir = Join-Path $root 'run\smoke'
$levelName = 'smoketest3'
$worldDir = Join-Path $root "run\$levelName"

New-Item -ItemType Directory -Force -Path $smokeDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $root 'run') | Out-Null
Set-Content -Path (Join-Path $root 'run\eula.txt') -Value 'eula=true' -Encoding ascii
Set-Content -Path (Join-Path $root 'run\server.properties') -Encoding ascii -Value @(
	'online-mode=false'
	"level-name=$levelName"
	'level-seed=10086'
	'max-tick-time=-1'
	'spawn-protection=0'
	'view-distance=6'
	'simulation-distance=6'
)

function Write-CommandFile {
	param([string]$Name, [string[]]$Lines)
	# 只用 \n，避免服务器把 \r 当成命令的一部分。
	$content = ($Lines -join "`n") + "`n"
	[System.IO.File]::WriteAllText((Join-Path $smokeDir $Name), $content, (New-Object System.Text.UTF8Encoding($false)))
	return (Join-Path $smokeDir $Name)
}

# ---------------------------------------------------------------- 命令脚本

$placeLines = New-Object System.Collections.ArrayList
foreach ($line in @(
		'say PHASE1_PLACE_BEGIN'
		'fill -20 95 -20 40 118 -1 minecraft:air'
		'fill -20 95 0 40 118 19 minecraft:air'
		'fill -20 95 20 40 118 39 minecraft:air'
		'fill -20 95 40 40 118 50 minecraft:air'
		'setblock 0 100 0 minecraft:redstone_block'
		'setblock 1 100 0 circuit-redstone_logic:wire'
		'setblock 2 99 0 minecraft:stone'
		'setblock 2 100 0 minecraft:redstone_wire'
		'setblock 10 100 0 circuit-redstone_logic:wire'
		'setblock 11 100 0 circuit-redstone_logic:wire'
		'setblock 12 100 0 circuit-redstone_logic:wire'
		'setblock 12 100 1 circuit-redstone_logic:wire'
		'setblock 11 100 1 circuit-redstone_logic:wire'
		'setblock 10 100 1 circuit-redstone_logic:wire'
		'setblock 0 99 12 minecraft:stone'
		'setblock 0 100 12 minecraft:redstone_wire'
		'setblock -1 100 12 minecraft:redstone_block'
		'setblock 1 100 12 circuit-redstone_logic:wire'
		'setblock 0 100 5 circuit-redstone_logic:and_gate[facing=east]'
		'setblock -1 100 5 minecraft:redstone_block'
		'setblock 0 100 4 minecraft:redstone_block'
		'setblock 3 100 5 circuit-redstone_logic:not_gate[facing=east]'
		'setblock 2 100 5 minecraft:redstone_block'
		# 与门输出侧放一段导线：验证 test start 可以直接从逻辑门坐标开始（不必是导线）。
		'setblock 1 100 5 circuit-redstone_logic:wire'
		'setblock 8 100 5 circuit-redstone_logic:xor_gate[facing=east]'
		'setblock 7 100 5 minecraft:redstone_block'
		'setblock 0 100 8 circuit-redstone_logic:limiter[facing=east]'
		'setblock -1 100 8 minecraft:redstone_block'
		'setblock 0 100 7 minecraft:redstone_block'
		'setblock 5 100 8 circuit-redstone_logic:scanner[facing=east]'
		'setblock 5 100 7 minecraft:stone'
		'setblock 5 100 6 minecraft:stone'
		'setblock 5 100 5 minecraft:stone'
		'setblock 16 100 16 circuit-redstone_logic:scanner'
		'setblock 0 100 28 circuit-redstone_logic:amplifier[facing=east]'
		'setblock -1 100 28 minecraft:redstone_block'
		'setblock 9 100 32 circuit-redstone_logic:amplifier[facing=east]'
		'setblock 0 100 32 minecraft:redstone_block'
		'setblock 0 100 44 circuit-redstone_logic:brightness_sensor'
		'setblock 0 101 44 minecraft:stone'
		'setblock 0 99 44 minecraft:stone'
		'setblock 1 100 44 minecraft:stone'
		'setblock -1 100 44 minecraft:stone'
		'setblock 0 100 43 minecraft:stone'
		'setblock 0 100 45 minecraft:stone'
		'setblock 0 100 20 minecraft:redstone_block'
		'setblock 17 100 20 circuit-redstone_logic:wire'
		'setblock 0 100 24 minecraft:redstone_block'
		'setblock 4 100 24 circuit-redstone_logic:wire'
		# 常量 / 字符串：扫描器射线里有常量时输出常量的值，否则按数量统计
		'setblock 30 100 2 circuit-redstone_logic:scanner[facing=east]'
		'setblock 30 100 1 circuit-redstone_logic:constant'
		'setblock 31 100 1 circuit-redstone_logic:wire'
		'setblock 32 100 2 circuit-redstone_logic:scanner[facing=east]'
		'setblock 32 100 1 circuit-redstone_logic:string_value'
		'circuit-set 30 100 1 7'
		'circuit-set 32 100 1 hello world'
		# 数据线：常量→常量 / 常量→字符串 / 字符串→常量 / 转换失败 / 串联
		'setblock 40 100 2 circuit-redstone_logic:constant'
		'setblock 41 100 2 circuit-redstone_logic:data_wire[facing=east]'
		'setblock 42 100 2 circuit-redstone_logic:constant'
		'circuit-set 40 100 2 5'
		'setblock 40 100 4 circuit-redstone_logic:constant'
		'setblock 41 100 4 circuit-redstone_logic:data_wire[facing=east]'
		'setblock 42 100 4 circuit-redstone_logic:string_value'
		'circuit-set 40 100 4 -12'
		'setblock 40 100 6 circuit-redstone_logic:string_value'
		'setblock 41 100 6 circuit-redstone_logic:data_wire[facing=east]'
		'setblock 42 100 6 circuit-redstone_logic:constant'
		'circuit-set 40 100 6 42'
		'setblock 40 100 8 circuit-redstone_logic:string_value'
		'setblock 41 100 8 circuit-redstone_logic:data_wire[facing=east]'
		'setblock 42 100 8 circuit-redstone_logic:constant'
		'circuit-set 40 100 8 abc'
		'setblock 40 100 10 circuit-redstone_logic:constant'
		'setblock 41 100 10 circuit-redstone_logic:data_wire[facing=east]'
		'setblock 42 100 10 circuit-redstone_logic:data_wire[facing=east]'
		'setblock 43 100 10 circuit-redstone_logic:constant'
		'circuit-set 40 100 10 77'
		# 纯色方块：默认黑色，颜色用 /circuit-set 十六进制写入
		'setblock 40 100 12 circuit-redstone_logic:color_block'
	)) {
	[void]$placeLines.Add($line)
}
# 衰减测试：从 x=0 的红石块沿 +x 铺 16 段红石线，末端强度应为 0（15-15）。
for ($x = 1; $x -le 16; $x++) {
	[void]$placeLines.Add("setblock $x 99 20 minecraft:stone")
	[void]$placeLines.Add("setblock $x 100 20 minecraft:redstone_wire")
}
# 衰减测试（非零）：x=1..3 红石线，末端强度 13 > 0。
for ($x = 1; $x -le 3; $x++) {
	[void]$placeLines.Add("setblock $x 99 24 minecraft:stone")
	[void]$placeLines.Add("setblock $x 100 24 minecraft:redstone_wire")
}
# 增强器放大测试：x=1..8 的红石线从 15 衰减到 8，增强器读这级 8 并输出 15。
for ($x = 1; $x -le 8; $x++) {
	[void]$placeLines.Add("setblock $x 99 32 minecraft:stone")
	[void]$placeLines.Add("setblock $x 100 32 minecraft:redstone_wire")
}
# 比较器测试：后方常量 A、右方常量 B、左方用红石线衰减出的等级当比较模式。
# 左侧等级 p 的构造：红石块在 z = (左方位置 + 15 - p) + 1，红石线从该处一直铺到左方位置。
$comparatorCases = @(
	@{ X = 50; Z = 2;  Mode = 1; First = 3; Second = 5 },   # 3 < 5  -> 成立
	@{ X = 60; Z = 8;  Mode = 3; First = 4; Second = 4 },   # 4 == 4 -> 成立
	@{ X = 70; Z = 14; Mode = 5; First = 5; Second = 5 }    # 5 > 5  -> 不成立
)
foreach ($case in $comparatorCases) {
	$leftX = $case.X - 1
	$firstDustZ = $case.Z + (15 - $case.Mode)
	[void]$placeLines.Add("setblock $($case.X) 100 $($case.Z) circuit-redstone_logic:comparator[facing=north]")
	[void]$placeLines.Add("setblock $($case.X) 100 $($case.Z + 1) circuit-redstone_logic:constant")
	[void]$placeLines.Add("circuit-set $($case.X) 100 $($case.Z + 1) $($case.First)")
	[void]$placeLines.Add("setblock $($case.X + 1) 100 $($case.Z) circuit-redstone_logic:constant")
	[void]$placeLines.Add("circuit-set $($case.X + 1) 100 $($case.Z) $($case.Second)")
	[void]$placeLines.Add("setblock $leftX 100 $($firstDustZ + 1) minecraft:redstone_block")
	for ($z = $case.Z; $z -le $firstDustZ; $z++) {
		[void]$placeLines.Add("setblock $leftX 99 $z minecraft:stone")
		[void]$placeLines.Add("setblock $leftX 100 $z minecraft:redstone_wire")
	}
}
# 没有常量输入的比较器：模式来自左侧空气（等级 0，非法）→ 输出 0。
[void]$placeLines.Add('setblock 80 100 2 circuit-redstone_logic:comparator[facing=north]')

# 时钟：period=1 应该恒定高电平；period=5 只校验 NBT；未设置时恒定低电平。
[void]$placeLines.Add('setblock 90 100 2 circuit-redstone_logic:clock[facing=north]')
[void]$placeLines.Add('circuit-set 90 100 2 1')
[void]$placeLines.Add('setblock 90 100 4 circuit-redstone_logic:clock[facing=north]')
[void]$placeLines.Add('circuit-set 90 100 4 5')
[void]$placeLines.Add('setblock 90 100 6 circuit-redstone_logic:clock[facing=north]')
# period=2 的时钟：每 tick 交替高低，用来给 run-chart 制造"每 tick 都在变"的数据。
[void]$placeLines.Add('setblock 90 100 8 circuit-redstone_logic:clock[facing=north]')
[void]$placeLines.Add('circuit-set 90 100 8 2')

# 数据线新功能：背面是红石信号（这里用衰减到等级 7 的红石线）→ 正面常量拿到 7。
[void]$placeLines.Add('setblock 42 100 20 circuit-redstone_logic:constant')
[void]$placeLines.Add('setblock 41 100 20 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 40 100 29 minecraft:redstone_block')
for ($z = 20; $z -le 28; $z++) {
	[void]$placeLines.Add("setblock 40 99 $z minecraft:stone")
	[void]$placeLines.Add("setblock 40 100 $z minecraft:redstone_wire")
}

# 变量方块：一个方块存一个 int 或 str；数据线可读可写；挖掉后数据跟着物品走。
# 断言用的坐标集中在 x=55..62，z=30..36，与上面的电路不重叠。
[void]$placeLines.Add('setblock 55 100 30 circuit-redstone_logic:variable')                        # A 未设置
[void]$placeLines.Add('setblock 55 100 32 circuit-redstone_logic:variable')                        # B 文本
[void]$placeLines.Add('circuit-set 55 100 32 str="hello world"')
[void]$placeLines.Add('setblock 56 100 30 circuit-redstone_logic:variable')                        # C 变量(int) -> 常量
[void]$placeLines.Add('circuit-set 56 100 30 int=42')
[void]$placeLines.Add('setblock 57 100 30 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 58 100 30 circuit-redstone_logic:constant')
[void]$placeLines.Add('setblock 56 100 32 circuit-redstone_logic:variable')                        # D 变量(str) -> 字符串
[void]$placeLines.Add('circuit-set 56 100 32 str="hi"')
[void]$placeLines.Add('setblock 57 100 32 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 58 100 32 circuit-redstone_logic:string_value')
[void]$placeLines.Add('setblock 56 100 34 circuit-redstone_logic:constant')                        # E 常量 -> 未设置的变量（跟随类型）
[void]$placeLines.Add('circuit-set 56 100 34 7')
[void]$placeLines.Add('setblock 57 100 34 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 58 100 34 circuit-redstone_logic:variable')
[void]$placeLines.Add('setblock 55 100 36 circuit-redstone_logic:string_value')                    # F 文本 -> int 变量：类型冲突
[void]$placeLines.Add('circuit-set 55 100 36 abc')
[void]$placeLines.Add('setblock 56 100 36 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 57 100 36 circuit-redstone_logic:variable')
[void]$placeLines.Add('circuit-set 57 100 36 int=1')
[void]$placeLines.Add('setblock 60 100 30 circuit-redstone_logic:variable')                        # G 挖掉保留
[void]$placeLines.Add('circuit-set 60 100 30 int=99')
[void]$placeLines.Add('setblock 60 100 32 circuit-redstone_logic:variable')                        # H 文本 "12" -> 常量
[void]$placeLines.Add('circuit-set 60 100 32 str="12"')
[void]$placeLines.Add('setblock 61 100 32 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 62 100 32 circuit-redstone_logic:constant')
[void]$placeLines.Add('setblock 60 100 34 circuit-redstone_logic:variable')                        # I 未设置：不应把目标清零
[void]$placeLines.Add('setblock 61 100 34 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 62 100 34 circuit-redstone_logic:constant')
[void]$placeLines.Add('circuit-set 62 100 34 5')
[void]$placeLines.Add('setblock 60 100 36 circuit-redstone_logic:string_value')                    # J 文本 -> 未设置的变量
[void]$placeLines.Add('circuit-set 60 100 36 hello')
[void]$placeLines.Add('setblock 61 100 36 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 62 100 36 circuit-redstone_logic:variable')

# null：不写值也能设置；数据线把它搬到常量得 0、搬到字符串得空串。
[void]$placeLines.Add('setblock 65 100 30 circuit-redstone_logic:variable')
[void]$placeLines.Add('circuit-set 65 100 30 null')
[void]$placeLines.Add('setblock 66 100 30 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 67 100 30 circuit-redstone_logic:constant')
[void]$placeLines.Add('circuit-set 67 100 30 5')
[void]$placeLines.Add('setblock 65 100 32 circuit-redstone_logic:variable')
[void]$placeLines.Add('circuit-set 65 100 32 null')
[void]$placeLines.Add('setblock 66 100 32 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 67 100 32 circuit-redstone_logic:string_value')
[void]$placeLines.Add('circuit-set 67 100 32 xyz')
[void]$placeLines.Add('setblock 65 100 34 circuit-redstone_logic:variable')
[void]$placeLines.Add('circuit-set 65 100 34 null')
[void]$placeLines.Add('setblock 66 100 34 circuit-redstone_logic:data_wire[facing=east]')
[void]$placeLines.Add('setblock 67 100 34 circuit-redstone_logic:variable')

# 悬空方块：完整方块（红石粉能放在上面），但玩家可以穿过（碰撞箱空，无法用命令断言）。
# 66..70 与前面的变量测试区（55..67）错开，避免占用同一格。
[void]$placeLines.Add('setblock 70 100 30 circuit-redstone_logic:hover_block')
[void]$placeLines.Add('setblock 70 101 30 minecraft:redstone_wire')
# 触发一次邻居更新：红石粉会在更新里重新做 canPlaceAt 检查（下面有悬空方块 → 留下）
[void]$placeLines.Add('setblock 71 100 30 minecraft:stone')
[void]$placeLines.Add('setblock 71 100 30 minecraft:air')
# 对照组：空气中放红石粉，同样的更新之后应该被移除
[void]$placeLines.Add('setblock 70 101 32 minecraft:redstone_wire')

# 隐形告示牌：文字用 /circuit-set 写，文本存在方块实体里（".." 表示清空）。
[void]$placeLines.Add('setblock 72 100 30 circuit-redstone_logic:hidden_sign')
[void]$placeLines.Add('setblock 72 100 32 circuit-redstone_logic:hidden_sign')
[void]$placeLines.Add('setblock 71 100 32 minecraft:stone')
[void]$placeLines.Add('setblock 71 100 32 minecraft:air')

[void]$placeLines.Add('say PHASE1_PLACE_END')

$place = Write-CommandFile -Name 'phase1-place.txt' -Lines $placeLines.ToArray()

$assertOn = Write-CommandFile -Name 'phase2-assert-on.txt' -Lines @(
	'say PHASE2_ASSERT_ON_BEGIN'
	'execute if block 2 100 0 minecraft:air run say DIAG_DUST_MISSING'
	'execute if block 1 100 0 circuit-redstone_logic:wire[powered=true] run say OK_WIRE_POWERED'
	'execute if block 2 100 0 minecraft:redstone_wire[power=15] run say OK_WIRE_TO_DUST'
	'execute if block 1 100 12 circuit-redstone_logic:wire[powered=true] run say OK_DUST_TO_WIRE'
	'execute if block 17 100 20 circuit-redstone_logic:wire[powered=false] run say OK_DUST_DECAY_ZERO'
	'execute if block 4 100 24 circuit-redstone_logic:wire[powered=true] run say OK_DUST_DECAY_POSITIVE'
	'execute if block 10 100 1 circuit-redstone_logic:wire[powered=false] run say OK_RING_NOT_LATCHED'
	'execute if block 0 100 5 circuit-redstone_logic:and_gate[power=15] run say OK_AND_11'
	'execute if block 3 100 5 circuit-redstone_logic:not_gate[power=0] run say OK_NOT_IN_HIGH'
	'execute if block 8 100 5 circuit-redstone_logic:xor_gate[power=15] run say OK_XOR_10'
	'execute if block 0 100 8 circuit-redstone_logic:limiter[power=15] run say OK_LIMITER_AB'
	'execute if block 5 100 8 circuit-redstone_logic:scanner[power=3] run say OK_SCANNER_LEFT3'
	'execute if block 16 100 16 circuit-redstone_logic:scanner[power=0] run say OK_SCANNER_EMPTY_ZERO'
	'execute if block 0 100 28 circuit-redstone_logic:amplifier[power=15] run say OK_AMPLIFIER_ON'
	'execute if block 9 100 32 circuit-redstone_logic:amplifier[power=15] run say OK_AMPLIFIER_AMPLIFY8TO15'
	'execute if block 0 100 44 circuit-redstone_logic:brightness_sensor[power=0] run say OK_LIGHT_SENSOR_DARK'
	'execute if block 50 100 2 circuit-redstone_logic:comparator[power=15] run say OK_COMPARATOR_LESS'
	'execute if block 60 100 8 circuit-redstone_logic:comparator[power=15] run say OK_COMPARATOR_EQUAL'
	'execute if block 70 100 14 circuit-redstone_logic:comparator[power=0] run say OK_COMPARATOR_GREATER_FALSE'
	'execute if block 80 100 2 circuit-redstone_logic:comparator[power=0] run say OK_COMPARATOR_NO_CONSTANT'
	'execute if block 90 100 2 circuit-redstone_logic:clock[power=15] run say OK_CLOCK_ALWAYS_HIGH'
	'execute if data block 90 100 4 {period:5} run say OK_CLOCK_PERIOD'
	'execute if block 90 100 6 circuit-redstone_logic:clock[power=0] run say OK_CLOCK_UNSET'
	'execute if data block 42 100 20 {value:7} run say OK_DATA_REDSTONE_LEVEL'
	'execute if block 30 100 2 circuit-redstone_logic:scanner[power=7] run say OK_SCANNER_CONSTANT'
	'execute if block 32 100 2 circuit-redstone_logic:scanner[power=1] run say OK_SCANNER_STRING_COUNTS'
	'execute if block 31 100 1 circuit-redstone_logic:wire[powered=false] run say OK_CONSTANT_NO_EMIT'
	'circuit-set 30 100 1 abc'
	'circuit-set 30 100 5 3'
	'circuit-set 30 100 1 20'
	'circuit-set 90 100 6 0'
	'circuit-read 30 100 1'
	'circuit-read 32 100 1'
	'circuit-read 30 100 5'
	'circuit-read 42 100 2'
	'execute if data block 42 100 2 {value:5} run say OK_DATA_CONST'
	'execute if data block 42 100 4 {text:"-12"} run say OK_DATA_TEXT'
	'execute if data block 42 100 6 {value:42} run say OK_DATA_TEXT_TO_CONST'
	'execute if block 41 100 8 circuit-redstone_logic:data_wire[error=true] run say OK_DATA_ERROR'
	'execute if data block 43 100 10 {value:77} run say OK_DATA_CHAIN'
	'execute if data block 40 100 12 {color:0} run say OK_COLOR_DEFAULT'
	'circuit-set 40 100 12 #12AB34'
	'circuit-set 40 100 12 #GG0011'
	'circuit-set 40 100 8 7'
	'loot spawn 0 120 0 mine 1 100 0'
	'execute if entity @e[type=item,nbt={Item:{id:"circuit-redstone_logic:wire"}}] run say OK_LOOT_WIRE'
	'loot spawn 0 120 0 mine 0 100 5'
	'execute if entity @e[type=item,nbt={Item:{id:"circuit-redstone_logic:and_gate"}}] run say OK_LOOT_GATE'
	'circuit-terminal list'
	'circuit-debug info full-circuit'
	# 变量方块：类型与值、数据线双向、错误分支、挖掉保留
	'execute if block 55 100 30 circuit-redstone_logic:variable[kind=unset] run say OK_VAR_UNSET'
	'circuit-read 55 100 30'
	'circuit-set 55 100 30 null'
	'execute if block 55 100 30 circuit-redstone_logic:variable[kind=null] run say OK_VAR_NULL_STATE'
	'circuit-read 55 100 30'
	'execute if data block 67 100 30 {value:0} run say OK_VAR_NULL_TO_CONST'
	'execute if data block 67 100 32 {text:""} run say OK_VAR_NULL_TO_STRING'
	'execute if data block 67 100 34 {kind:"null"} run say OK_VAR_NULL_TO_VAR'
	'execute if block 67 100 34 circuit-redstone_logic:variable[kind=null] run say OK_VAR_NULL_TO_VAR_STATE'
	'circuit-read 55 100 32'
	'execute if data block 55 100 32 {kind:"str",text:"hello world"} run say OK_VAR_SET_STR'
	'execute if block 55 100 32 circuit-redstone_logic:variable[kind=str] run say OK_VAR_STR_STATE'
	'execute if data block 58 100 30 {value:42} run say OK_VAR_TO_CONST'
	'execute if data block 58 100 32 {text:"hi"} run say OK_VAR_TO_STRING'
	'execute if data block 58 100 34 {kind:"int",number:7} run say OK_CONST_TO_VAR'
	'execute if block 58 100 34 circuit-redstone_logic:variable[kind=int] run say OK_CONST_TO_VAR_STATE'
	'data get block 58 100 34'
	'execute if block 56 100 36 circuit-redstone_logic:data_wire[error=true] run say OK_VAR_TYPE_ERROR'
	'execute if data block 62 100 32 {value:12} run say OK_VAR_STR_TO_CONST'
	'execute if data block 62 100 34 {value:5} run say OK_VAR_UNSET_NO_WRITE'
	'execute if data block 62 100 36 {kind:"str",text:"hello"} run say OK_VAR_FOLLOW_TEXT'
	'setblock 60 100 30 minecraft:air destroy'
	'execute if entity @e[type=item,nbt={Item:{id:"circuit-redstone_logic:variable",tag:{BlockEntityTag:{kind:"int",number:99}}}}] run say OK_VAR_DROP_KEPT'
	# 悬空方块：能托住红石粉（对照：空气中的红石粉会被移除）
	'execute if block 70 101 30 minecraft:redstone_wire run say OK_HOVER_SUPPORTS_DUST'
	'execute if block 70 101 32 minecraft:air run say OK_DUST_NEEDS_SUPPORT'
	# 隐形告示牌：放得下、文本可写、可清空、会掉自己
	'execute if block 72 100 30 circuit-redstone_logic:hidden_sign run say OK_SIGN_PLACED'
	'circuit-set 72 100 30 hello sign'
	'execute if data block 72 100 30 {text:"hello sign"} run say OK_SIGN_TEXT'
	'circuit-set 72 100 32 hello'
	'circuit-set 72 100 32 ""'
	'execute if data block 72 100 32 {text:""} run say OK_SIGN_CLEARED'
	'loot spawn 0 120 0 mine 72 100 30'
	'execute if entity @e[type=item,nbt={Item:{id:"circuit-redstone_logic:hidden_sign"}}] run say OK_LOOT_SIGN'
	'execute if block 70 100 30 circuit-redstone_logic:hover_block run say OK_HOVER_PLACED'
	'loot spawn 0 120 0 mine 70 100 30'
	'execute if entity @e[type=item,nbt={Item:{id:"circuit-redstone_logic:hover_block"}}] run say OK_LOOT_HOVER'
	'circuit-set 55 100 30 42'
	'circuit-set 55 100 32 str=hi'
	'circuit-set 55 100 30 bool=1'
	'circuit-set 55 100 30 int=abc'
	'circuit-set 55 100 32 int=5'
	'circuit-read 55 100 32'
	'circuit-debug test start 0 100 5'
	'circuit-debug test start 2 100 2'
	'circuit-debug test stop'
	'circuit-debug run-chart 89 99 7 91 101 9 40'
	'say PHASE2_ASSERT_ON_END'
)

$removeSources = Write-CommandFile -Name 'phase2-remove.txt' -Lines @(
	'say PHASE2_REMOVE_BEGIN'
	'setblock 0 100 0 minecraft:air'
	'setblock -1 100 12 minecraft:air'
	'setblock 0 100 4 minecraft:air'
	'setblock 2 100 5 minecraft:air'
	'setblock 7 100 5 minecraft:air'
	'setblock 0 100 7 minecraft:air'
	'setblock 5 100 5 minecraft:air'
	'setblock 0 100 24 minecraft:air'
	'setblock -1 100 28 minecraft:air'
	'setblock 0 100 32 minecraft:air'
	'setblock 1 100 44 minecraft:glowstone'
	'say PHASE2_REMOVE_END'
)

$assertOff = Write-CommandFile -Name 'phase3-assert-off.txt' -Lines @(
	'say PHASE3_ASSERT_OFF_BEGIN'
	'execute if block 1 100 0 circuit-redstone_logic:wire[powered=false] run say OK_WIRE_OFF'
	'execute if block 2 100 0 minecraft:redstone_wire[power=0] run say OK_DUST_OFF'
	'execute if block 1 100 12 circuit-redstone_logic:wire[powered=false] run say OK_DUST_TO_WIRE_OFF'
	'execute if block 17 100 20 circuit-redstone_logic:wire[powered=false] run say OK_DUST_DECAY_ZERO_STILL'
	'execute if block 4 100 24 circuit-redstone_logic:wire[powered=false] run say OK_DUST_DECAY_POSITIVE_OFF'
	'execute if block 0 100 28 circuit-redstone_logic:amplifier[power=0] run say OK_AMPLIFIER_OFF'
	'execute if block 9 100 32 circuit-redstone_logic:amplifier[power=0] run say OK_AMPLIFIER_AMPLIFY_OFF'
	'execute if block 0 100 44 circuit-redstone_logic:brightness_sensor[power=15] run say OK_LIGHT_SENSOR_GLOW15'
	'setblock 1 100 44 minecraft:stone'
	'execute if block 0 100 5 circuit-redstone_logic:and_gate[power=0] run say OK_AND_10'
	'execute if block 3 100 5 circuit-redstone_logic:not_gate[power=15] run say OK_NOT_IN_LOW'
	'execute if block 8 100 5 circuit-redstone_logic:xor_gate[power=0] run say OK_XOR_00'
	'execute if block 0 100 8 circuit-redstone_logic:limiter[power=0] run say OK_LIMITER_A_ONLY'
	'execute if block 5 100 8 circuit-redstone_logic:scanner[power=2] run say OK_SCANNER_PERIODIC_RESCAN'
	'execute if block 30 100 2 circuit-redstone_logic:scanner[power=15] run say OK_SCANNER_CONSTANT_CLAMP'
	'execute if data block 40 100 12 {color:1223476} run say OK_COLOR_SET'
	'execute if block 41 100 8 circuit-redstone_logic:data_wire[error=false] run say OK_DATA_ERROR_CLEARED'
	'execute if data block 42 100 8 {value:7} run say OK_DATA_ERROR_FIXED'
	# 拆掉带方块实体的方块：必须连方块实体一起移除（否则日志会刷 invalid for ticking）
	'setblock 3 100 5 minecraft:air'
	'setblock 16 100 16 minecraft:air'
	'setblock 30 100 2 minecraft:air'
	'setblock 32 100 1 minecraft:air'
	# run-video 放在第三轮：第二轮里 run-chart 还在录，同一执行者会报 "recording in progress"。
	'circuit-debug run-video 89 99 7 91 101 9 20'
	'say PHASE3_ASSERT_OFF_END'
)

$stopFile = Write-CommandFile -Name 'stop.txt' -Lines @('stop')

# 测试区必须强制加载：新世界的 spawn 不一定在原点附近，否则 setblock 会报 "position is not loaded"。
$prelude = Write-CommandFile -Name 'prelude.txt' -Lines @(
	'forceload add -32 -32 64 64'
	'say PRELUDE_FORCELOAD_DONE'
)

# ---------------------------------------------------------------- 三轮服务器

$wait = 'ping -n 6 127.0.0.1 >nul'
$boot = 'ping -n 45 127.0.0.1 >nul'

if (Test-Path $worldDir) {
	Remove-Item $worldDir -Recurse -Force
}
# 清掉旧的运行图，确保后面断言的是本次 run-chart 新写出来的文件（png 与同名 txt 都要清）。
$chartDir = Join-Path $root 'run\circuit-charts'
if (Test-Path $chartDir) {
	Remove-Item (Join-Path $chartDir '*.png') -Force -ErrorAction SilentlyContinue
	Remove-Item (Join-Path $chartDir '*.txt') -Force -ErrorAction SilentlyContinue
	Remove-Item (Join-Path $chartDir '*.gif') -Force -ErrorAction SilentlyContinue
	Remove-Item (Join-Path $chartDir '*.mp4') -Force -ErrorAction SilentlyContinue
}

function Invoke-ServerRound {
	param([string]$Name, [string[]]$CommandFiles)

	# 先 forceload 测试区并给它时间生成区块，再执行本轮的指令。
	$parts = @($boot, "type `"$prelude`"", 'ping -n 12 127.0.0.1 >nul')
	foreach ($file in $CommandFiles) {
		$parts += "type `"$file`""
		$parts += $wait
	}
	$parts += "type `"$stopFile`""
	$pipeline = ($parts -join ' & ')

	$logPath = Join-Path $smokeDir "$Name.log"
	Write-Output "=== $Name ==="
	$output = cmd /c "($pipeline) | gradlew.bat runServer --console=plain --no-configuration-cache" 2>&1
	$output | Set-Content -Path $logPath -Encoding utf8
	$output | Select-String -Pattern 'OK_|PHASE|SMOKE|ERROR|Exception|WARN.*circuit|Done \(' | ForEach-Object { $_.Line }
}

Invoke-ServerRound -Name 'round1-place' -CommandFiles @($place)
Invoke-ServerRound -Name 'round2-assert-on-remove' -CommandFiles @($assertOn, $removeSources)
Invoke-ServerRound -Name 'round3-assert-off' -CommandFiles @($assertOff)

Write-Output ''
Write-Output '=== 断言汇总 ==='
$expected = @(
	'OK_WIRE_POWERED'
	'OK_WIRE_TO_DUST'
	'OK_DUST_TO_WIRE'
	'OK_DUST_DECAY_ZERO'
	'OK_DUST_DECAY_POSITIVE'
	'OK_RING_NOT_LATCHED'
	'OK_AND_11'
	'OK_NOT_IN_HIGH'
	'OK_XOR_10'
	'OK_LIMITER_AB'
	'OK_SCANNER_LEFT3'
	'OK_SCANNER_EMPTY_ZERO'
	'OK_AMPLIFIER_ON'
	'OK_AMPLIFIER_AMPLIFY8TO15'
	'OK_WIRE_OFF'
	'OK_DUST_OFF'
	'OK_DUST_TO_WIRE_OFF'
	'OK_DUST_DECAY_ZERO_STILL'
	'OK_DUST_DECAY_POSITIVE_OFF'
	'OK_AMPLIFIER_OFF'
	'OK_AMPLIFIER_AMPLIFY_OFF'
	'OK_LIGHT_SENSOR_GLOW15'
	'OK_COMPARATOR_LESS'
	'OK_COMPARATOR_EQUAL'
	'OK_COMPARATOR_GREATER_FALSE'
	'OK_COMPARATOR_NO_CONSTANT'
	'OK_CLOCK_ALWAYS_HIGH'
	'OK_CLOCK_PERIOD'
	'OK_CLOCK_UNSET'
	'OK_DATA_REDSTONE_LEVEL'
	'OK_AND_10'
	'OK_NOT_IN_LOW'
	'OK_XOR_00'
	'OK_LIMITER_A_ONLY'
	'OK_SCANNER_PERIODIC_RESCAN'
	'OK_SCANNER_CONSTANT'
	'OK_SCANNER_STRING_COUNTS'
	'OK_CONSTANT_NO_EMIT'
	'OK_SCANNER_CONSTANT_CLAMP'
	'OK_DATA_CONST'
	'OK_DATA_TEXT'
	'OK_DATA_TEXT_TO_CONST'
	'OK_DATA_ERROR'
	'OK_DATA_CHAIN'
	'OK_COLOR_DEFAULT'
	'OK_COLOR_SET'
	'OK_DATA_ERROR_CLEARED'
	'OK_DATA_ERROR_FIXED'
	'OK_LOOT_WIRE'
	'OK_LOOT_GATE'
	# 变量方块：set / read / null / 数据线双向 / 挖掉保留
	'OK_VAR_UNSET'
	'OK_VAR_NULL_STATE'
	'OK_VAR_NULL_TO_CONST'
	'OK_VAR_NULL_TO_STRING'
	'OK_VAR_NULL_TO_VAR'
	'OK_VAR_NULL_TO_VAR_STATE'
	'OK_VAR_SET_STR'
	'OK_VAR_STR_STATE'
	'OK_VAR_TO_CONST'
	'OK_VAR_TO_STRING'
	'OK_CONST_TO_VAR'
	'OK_CONST_TO_VAR_STATE'
	'OK_VAR_TYPE_ERROR'
	'OK_VAR_STR_TO_CONST'
	'OK_VAR_UNSET_NO_WRITE'
	'OK_VAR_FOLLOW_TEXT'
	'OK_VAR_DROP_KEPT'
	'OK_HOVER_SUPPORTS_DUST'
	'OK_DUST_NEEDS_SUPPORT'
	'OK_HOVER_PLACED'
	'OK_LOOT_HOVER'
	'OK_SIGN_PLACED'
	'OK_SIGN_TEXT'
	'OK_SIGN_CLEARED'
	'OK_LOOT_SIGN'
)
$allLogs = (Get-ChildItem $smokeDir -Filter '*.log' | Get-Content) -join "`n"
$missing = @()
foreach ($marker in $expected) {
	if ($allLogs -match [regex]::Escape($marker)) {
		Write-Output "PASS $marker"
	} else {
		Write-Output "FAIL $marker"
		$missing += $marker
	}
}
if ($allLogs -match 'DIAG_DUST_MISSING') {
	Write-Output 'DIAG: 原版红石线在断言时不存在（多半是缺少下方支撑方块）'
}
# 指令注册与"仅玩家可用"分支：控制台执行会打印本地化后的提示（服务端语言=en_us 时是英文）
if ($allLogs -match [regex]::Escape('command.circuit-redstone_logic.terminal.players_only') -or
	$allLogs -match [regex]::Escape('Only a player can use this command.')) {
	Write-Output 'PASS COMMAND_CIRCUIT_TERMINAL_REGISTERED'
} else {
	Write-Output 'FAIL COMMAND_CIRCUIT_TERMINAL_REGISTERED'
	$missing += 'COMMAND_CIRCUIT_TERMINAL_REGISTERED'
}
# /circuit-debug 注册与"仅玩家"分支（服务端渲染后的文案里带 /circuit-debug）
if ($allLogs -match [regex]::Escape('/circuit-debug')) {
	Write-Output 'PASS COMMAND_CIRCUIT_DEBUG_REGISTERED'
} else {
	Write-Output 'FAIL COMMAND_CIRCUIT_DEBUG_REGISTERED'
	$missing += 'COMMAND_CIRCUIT_DEBUG_REGISTERED'
}

# ---------------------------------------------------------------- info full-circuit 输出契约
# 需求：一行一个方块，格式固定 <方块名> (<x,y,z>) [<SNBT>]，不加表头/统计/截断行，
#       且必须是"全部电路方块"而不是准星指向的那一张导线网络。

$snbtLine = [regex]'(?m)^.*?\(-?\d+, -?\d+, -?\d+\) \[\{.*\}\]\s*$'
$infoLines = [regex]::Matches($allLogs, $snbtLine)
if ($infoLines.Count -ge 10) {
	Write-Output "PASS DEBUG_INFO_FORMAT ($($infoLines.Count) lines)"
} else {
	Write-Output "FAIL DEBUG_INFO_FORMAT (only $($infoLines.Count) lines)"
	$missing += 'DEBUG_INFO_FORMAT'
}

# 全部方块类型都要出现在同一次快照里（证明不是"只导出导线"）。
$displayNames = @(
	'Redstone Wire Bus',
	'AND Gate',
	'NOT Gate',
	'XOR Gate',
	'Signal Limiter',
	'Signal Scanner',
	'Signal Amplifier',
	'Brightness Sensor',
	'Constant',
	'String',
	'Variable',
	'Data Wire',
	'Solid Color Block'
)
$absentTypes = @()
foreach ($displayName in $displayNames) {
	$pattern = [regex]::Escape($displayName) + ' \(-?\d+, -?\d+, -?\d+\) \[\{'
	if ($allLogs -notmatch $pattern) {
		$absentTypes += $displayName
	}
}
if ($absentTypes.Count -eq 0) {
	Write-Output 'PASS DEBUG_INFO_ALL_BLOCKS'
} else {
	Write-Output "FAIL DEBUG_INFO_ALL_BLOCKS (missing: $($absentTypes -join ', '))"
	$missing += 'DEBUG_INFO_ALL_BLOCKS'
}

# 快照必须带真实状态：(1,100,0) 的导线由红石块驱动，应为 powered=true。
if ($allLogs -match ('Redstone Wire Bus \(1, 100, 0\) \[\{Name:"circuit-redstone_logic:wire",Properties:\{[^}]*powered:"true"')) {
	Write-Output 'PASS DEBUG_INFO_STATE'
} else {
	Write-Output 'FAIL DEBUG_INFO_STATE'
	$missing += 'DEBUG_INFO_STATE'
}

# 不允许出现旧实现的表头/截断行（需求只允许逐行方块数据）。
if ($allLogs -match [regex]::Escape('Wire network:') -or $allLogs -match [regex]::Escape('more not shown')) {
	Write-Output 'FAIL DEBUG_INFO_NO_EXTRA_LINES'
	$missing += 'DEBUG_INFO_NO_EXTRA_LINES'
} else {
	Write-Output 'PASS DEBUG_INFO_NO_EXTRA_LINES'
}

# 原版聊天不会把 \n 当换行：绝不允许把多行拼进一条消息（日志里会出现字面量 "]\n"）。
if ($allLogs -match [regex]::Escape(']\n')) {
	Write-Output 'FAIL DEBUG_INFO_ONE_MESSAGE_PER_LINE'
	$missing += 'DEBUG_INFO_ONE_MESSAGE_PER_LINE'
} else {
	Write-Output 'PASS DEBUG_INFO_ONE_MESSAGE_PER_LINE'
}

# test start：起点是逻辑门（不是导线）也必须能开推演；起点是非电路方块必须给出准确报错。
if ($allLogs -match [regex]::Escape('Simulation id:')) {
	Write-Output 'PASS DEBUG_TEST_START_FROM_GATE'
} else {
	Write-Output 'FAIL DEBUG_TEST_START_FROM_GATE'
	$missing += 'DEBUG_TEST_START_FROM_GATE'
}
if ($allLogs -match [regex]::Escape('not a circuit block')) {
	Write-Output 'PASS DEBUG_TEST_REJECT_NON_CIRCUIT'
} else {
	Write-Output 'FAIL DEBUG_TEST_REJECT_NON_CIRCUIT'
	$missing += 'DEBUG_TEST_REJECT_NON_CIRCUIT'
}
if ($allLogs -match [regex]::Escape('Stopped 0 of your simulations') -or $allLogs -match [regex]::Escape('Stopped 1 of your simulations') -or $allLogs -match [regex]::Escape('Stopped 2 of your simulations')) {
	Write-Output 'PASS DEBUG_TEST_STOP'
} else {
	Write-Output 'FAIL DEBUG_TEST_STOP'
	$missing += 'DEBUG_TEST_STOP'
}

# ---------------------------------------------------------------- /circuit-set 输出契约
# 常量写整数、字符串写原文；非法输入必须给出可读报错。
$setChecks = [ordered]@{
	'SET_CONSTANT_FEEDBACK' = 'Constant (30, 100, 1) = 7'
	'SET_STRING_FEEDBACK'   = 'String (32, 100, 1) = "hello world"'
	'SET_BAD_NUMBER'        = 'Not an integer: abc'
	'SET_NOT_SETTABLE'      = 'That block is not a Constant, String or Variable block.'
	'SET_BAD_COLOR'         = 'Not a hex colour (#RRGGBB): #GG0011'
	'SET_COLOR_FEEDBACK'    = 'Solid Color Block (40, 100, 12) = #12AB34'
	'SET_CLOCK_FEEDBACK'    = 'Clock (90, 100, 4) = one high pulse every 5 ticks'
	'SET_BAD_PERIOD'        = 'The frequency must be an integer >= 1 (ticks): 0'
	'SET_SIGN_FEEDBACK'    = 'Hidden Sign (72, 100, 30) = "hello sign"'
}
foreach ($name in $setChecks.Keys) {
	if ($allLogs -match [regex]::Escape($setChecks[$name])) {
		Write-Output "PASS $name"
	} else {
		Write-Output "FAIL $name"
		$missing += $name
	}
}

# ---------------------------------------------------------------- 变量方块
# 语法、类型、读写方向、错误分支：全部按聊天栏实际文案断言（服务端语言是 en_us）。
$variableLogChecks = [ordered]@{
	'VAR_SET_OK'         = 'Variable (55, 100, 32) = "hello world"'
	'VAR_READ_UNSET'     = 'Variable (55, 100, 30): (not set)'
	'VAR_READ_NULL'      = 'Variable (55, 100, 30): null'
	'VAR_READ_STR'       = 'Variable (55, 100, 32): "hello world"'
	'VAR_READ_AFTER_INT' = 'Variable (55, 100, 32): 5'
	'VAR_BAD_SYNTAX'     = 'A Variable takes "type=value", not a bare value: 42'
	'VAR_NEED_QUOTES'    = 'Text values need double quotes around them: hi'
	'VAR_BAD_KIND'       = 'Unknown variable type: bool (use null / int / str)'
	'VAR_BAD_NUMBER'     = 'Not an integer: abc'
}
foreach ($name in $variableLogChecks.Keys) {
	if ($allLogs -match [regex]::Escape($variableLogChecks[$name])) {
		Write-Output "PASS $name"
	} else {
		Write-Output "FAIL $name"
		$missing += $name
	}
}

# 拆掉带方块实体的方块后，绝不能留下孤儿方块实体（否则日志会刷 invalid for ticking）。
if ($allLogs -match [regex]::Escape('invalid for ticking')) {
	Write-Output 'FAIL NO_ORPHAN_BLOCK_ENTITY'
	$missing += 'NO_ORPHAN_BLOCK_ENTITY'
} else {
	Write-Output 'PASS NO_ORPHAN_BLOCK_ENTITY'
}

# ---------------------------------------------------------------- run-chart 运行图
# 日志里要有开始记录与保存成功；磁盘上要有本次新写的 PNG（且不是空文件）。
$chartLogChecks = [ordered]@{
	'CHART_STARTED_LOG' = 'ticks=40 blocks=27'
	'CHART_SAVED'       = 'Chart saved:'
}
foreach ($name in $chartLogChecks.Keys) {
	if ($allLogs -match [regex]::Escape($chartLogChecks[$name])) {
		Write-Output "PASS $name"
	} else {
		Write-Output "FAIL $name"
		$missing += $name
	}
}
$chartFiles = @(Get-ChildItem -Path $chartDir -File -Filter '*.png' -ErrorAction SilentlyContinue)
$chartOk = ($chartFiles.Count -ge 1) -and ($chartFiles | Where-Object { $_.Length -gt 2000 })
if ($chartOk) {
	$newest = ($chartFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1)
	Write-Output "PASS CHART_IMAGE_WRITTEN ($($newest.Name), $($newest.Length) bytes)"
} else {
	Write-Output 'FAIL CHART_IMAGE_WRITTEN'
	$missing += 'CHART_IMAGE_WRITTEN'
}
# run-video：必须是真正的 GIF（魔数 GIF89a/GIF87a）且不是空文件。
$videoFiles = @(Get-ChildItem -Path $chartDir -File -Filter '*.gif' -ErrorAction SilentlyContinue)
$videoOk = $false
foreach ($file in $videoFiles) {
	$header = [System.IO.File]::ReadAllBytes($file.FullName)
	if ($header.Length -gt 2000 -and $header.Length -gt 6) {
		$magic = [System.Text.Encoding]::ASCII.GetString($header, 0, 4)
		if ($magic -eq 'GIF8') {
			$videoOk = $true
			Write-Output "PASS CHART_VIDEO_WRITTEN ($($file.Name), $($file.Length) bytes)"
		}
	}
}
if (-not $videoOk) {
	Write-Output 'FAIL CHART_VIDEO_WRITTEN'
	$missing += 'CHART_VIDEO_WRITTEN'
}
# run-video 还要尽可能再给一个 MP4（魔数：偏移 4 处是 'ftyp'）。
# 没有 ffmpeg 的机器上只出 GIF 是预期行为，所以那里记为 SKIP 而不是 FAIL。
# 再确认它真的能解码：ffprobe 数出来的帧数必须等于 21（round3 的 run-video 是 20 tick → 21 帧）。
$hasFfmpeg = [bool](Get-Command ffmpeg -ErrorAction SilentlyContinue)
$hasFfprobe = [bool](Get-Command ffprobe -ErrorAction SilentlyContinue)
$mp4Files = @(Get-ChildItem -Path $chartDir -File -Filter '*.mp4' -ErrorAction SilentlyContinue)
$mp4Ok = $false
foreach ($file in $mp4Files) {
	$bytes = [System.IO.File]::ReadAllBytes($file.FullName)
	# 48x64 的 21 帧小视频只有 3KB 出头，所以下限按"非空且带盒子头"来。
	if ($bytes.Length -gt 512 -and $bytes.Length -gt 12) {
		$magic = [System.Text.Encoding]::ASCII.GetString($bytes, 4, 4)
		if ($magic -eq 'ftyp') {
			$frames = ''
			if ($hasFfprobe) {
				$frames = (& ffprobe -v error -count_frames -select_streams v:0 `
						-show_entries stream=nb_read_frames -of default=nokey=1:noprint_wrappers=1 $file.FullName 2>$null |
						Select-Object -First 1)
			}
			if (-not $hasFfprobe -or "$frames".Trim() -eq '21') {
				$mp4Ok = $true
				Write-Output "PASS CHART_MP4_WRITTEN ($($file.Name), $($file.Length) bytes, frames=$("$frames".Trim()))"
			} else {
				Write-Output "DIAG CHART_MP4_WRITTEN failed: ffprobe frames='$frames' for $($file.Name)"
			}
		}
	}
}
if (-not $mp4Ok) {
	if ($hasFfmpeg) {
		Write-Output 'FAIL CHART_MP4_WRITTEN'
		$missing += 'CHART_MP4_WRITTEN'
	} else {
		Write-Output 'SKIP CHART_MP4_WRITTEN (ffmpeg not installed)'
	}
}
# 聊天栏/控制台也要报出 MP4 落盘路径（有 ffmpeg 才期望有）。
if ($hasFfmpeg) {
	if ($allLogs -match 'MP4 saved as well') {
		Write-Output 'PASS CHART_MP4_REPORTED'
	} else {
		Write-Output 'FAIL CHART_MP4_REPORTED'
		$missing += 'CHART_MP4_REPORTED'
	}
}
# 导出帧用的临时目录（frames-*）必须被清掉，不能留在运行图目录里。
$leftoverFrames = @(Get-ChildItem -Path $chartDir -Directory -Filter 'frames-*' -ErrorAction SilentlyContinue)
if ($leftoverFrames.Count -eq 0) {
	Write-Output 'PASS CHART_FRAMES_CLEANED'
} else {
	Write-Output "FAIL CHART_FRAMES_CLEANED ($($leftoverFrames[0].Name))"
	$missing += 'CHART_FRAMES_CLEANED'
}
# 同名 .txt 逐条清单也要写出来，并且真的包含变化行。
$chartTexts = @(Get-ChildItem -Path $chartDir -File -Filter '*.txt' -ErrorAction SilentlyContinue)
$chartTextOk = $false
foreach ($file in $chartTexts) {
	$content = Get-Content -Path $file.FullName -Raw -ErrorAction SilentlyContinue
	if ($content -match 't=\d+' -and $content -match 'how to read') {
		$chartTextOk = $true
	}
}
if ($chartTextOk) {
	Write-Output 'PASS CHART_TEXT_WRITTEN'
} else {
	Write-Output 'FAIL CHART_TEXT_WRITTEN'
	$missing += 'CHART_TEXT_WRITTEN'
}

# ---------------------------------------------------------------- /circuit-read 输出契约
# 格式固定为 <方块名> (<x, y, z>): <值>（字符串用引号包住）。
$readChecks = [ordered]@{
	'READ_CONSTANT'       = 'Constant (30, 100, 1): 20'
	'READ_STRING'         = 'String (32, 100, 1): "hello world"'
	'READ_NOT_DATA'       = 'Nothing to read there: not a Constant, String or Variable block.'
	'READ_AFTER_TRANSFER' = 'Constant (42, 100, 2): 5'
}
foreach ($name in $readChecks.Keys) {
	if ($allLogs -match [regex]::Escape($readChecks[$name])) {
		Write-Output "PASS $name"
	} else {
		Write-Output "FAIL $name"
		$missing += $name
	}
}

# 配方加载自检：只有 3 条配方（导线 / 红石粉互转 / 电路终端），其余方块走终端指令换取。
if ($allLogs -match [regex]::Escape('recipes loaded: 3 from this mod')) {
	Write-Output 'PASS RECIPES_LOADED'
} else {
	Write-Output 'FAIL RECIPES_LOADED'
	$missing += 'RECIPES_LOADED'
}

if ($missing.Count -gt 0) {
	Write-Output ''
	Write-Output "SMOKE TEST FAILED: $($missing.Count) missing marker(s)"
	exit 1
}
Write-Output ''
Write-Output 'SMOKE TEST PASSED'

