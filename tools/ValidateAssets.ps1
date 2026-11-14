# 静态校验资源：JSON 合法性、跨文件引用（model / parent / texture）、方块↔blockstate↔item 模型齐全性。
# 用法：pwsh -File tools/ValidateAssets.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root 'src\main\resources\assets\circuit-redstone_logic'
$modId = 'circuit-redstone_logic'
$problems = New-Object System.Collections.Generic.List[string]

function Add-Problem {
	param([string]$Message)
	$script:problems.Add($Message)
}

# 注册的方块 id（与 ModRegistry 保持一致）
$blocks = @('wire', 'and_gate', 'or_gate', 'not_gate', 'xor_gate', 'limiter', 'comparator', 'clock', 'scanner', 'amplifier', 'brightness_sensor', 'constant', 'string_value', 'variable', 'data_wire', 'color_block', 'hover_block', 'hidden_sign')
# 纯物品（与 ModRegistry 保持一致）
$plainItems = @('terminal')

function Read-Json {
	param([string]$Path)
	try {
		return (Get-Content -Path $Path -Raw -Encoding utf8 | ConvertFrom-Json)
	} catch {
		Add-Problem "JSON 解析失败: $Path -> $($_.Exception.Message)"
		return $null
	}
}

# 1. 全部 JSON 可解析
$jsonFiles = Get-ChildItem -Path (Join-Path $root 'src\main\resources') -Recurse -File -Filter *.json
foreach ($file in $jsonFiles) {
	Read-Json -Path $file.FullName | Out-Null
}

function Test-ModelReference {
	param([string]$Reference, [string]$Context)
	if ($Reference -notmatch ':') {
		Add-Problem "$Context 引用了没有命名空间的模型: $Reference"
		return
	}
	$parts = $Reference.Split(':', 2)
	if ($parts[0] -ne $modId) {
		return # vanilla 引用不在这里校验
	}
	$path = Join-Path (Join-Path $assets 'models') ($parts[1] + '.json')
	if (-not (Test-Path $path)) {
		Add-Problem "$Context 引用了不存在的模型: $Reference"
	}
}

function Test-TextureReference {
	param([string]$Reference, [string]$Context)
	if ($Reference.StartsWith('#')) {
		return # 纹理变量，由父模型提供
	}
	if ($Reference -notmatch ':') {
		Add-Problem "$Context 引用了没有命名空间的贴图: $Reference"
		return
	}
	$parts = $Reference.Split(':', 2)
	if ($parts[0] -ne $modId) {
		return
	}
	$path = Join-Path (Join-Path $assets 'textures') ($parts[1] + '.png')
	if (-not (Test-Path $path)) {
		Add-Problem "$Context 引用了不存在的贴图: $Reference"
	}
}

# 2. blockstate → 模型引用
foreach ($block in $blocks) {
	$blockstatePath = Join-Path (Join-Path $assets 'blockstates') "$block.json"
	if (-not (Test-Path $blockstatePath)) {
		Add-Problem "缺少 blockstate: $block.json"
		continue
	}
	$blockstate = Read-Json -Path $blockstatePath
	if ($null -eq $blockstate) {
		continue
	}
	if ($blockstate.PSObject.Properties.Name -contains 'multipart') {
		$index = 0
		foreach ($part in $blockstate.multipart) {
			if (-not ($part.PSObject.Properties.Name -contains 'apply')) {
				Add-Problem "$block.json 的 multipart[$index] 缺少 apply"
			} else {
				Test-ModelReference -Reference $part.apply.model -Context "$block.json multipart[$index]"
			}
			$index++
		}
	} elseif ($blockstate.PSObject.Properties.Name -contains 'variants') {
		foreach ($name in $blockstate.variants.PSObject.Properties.Name) {
			$variant = $blockstate.variants.$name
			if ($variant -is [array]) {
				foreach ($entry in $variant) {
					Test-ModelReference -Reference $entry.model -Context "$block.json $name"
				}
			} else {
				Test-ModelReference -Reference $variant.model -Context "$block.json $name"
			}
		}
	} else {
		Add-Problem "$block.json 既没有 multipart 也没有 variants"
	}

	# item 模型
	$itemModelPath = Join-Path (Join-Path $assets 'models\item') "$block.json"
	if (-not (Test-Path $itemModelPath)) {
		Add-Problem "缺少物品模型: models/item/$block.json"
	}
}

# 纯物品：要有 item 模型
foreach ($item in $plainItems) {
	$itemModelPath = Join-Path (Join-Path $assets 'models\item') "$item.json"
	if (-not (Test-Path $itemModelPath)) {
		Add-Problem "缺少物品模型: models/item/$item.json"
	}
	$langKey = "item.$modId.$item"
	foreach ($lang in @('en_us', 'zh_cn')) {
		$langPath = Join-Path (Join-Path $assets 'lang') "$lang.json"
		if (Test-Path $langPath) {
			$content = Read-Json -Path $langPath
			if ($null -ne $content -and -not ($content.PSObject.Properties.Name -contains $langKey)) {
				Add-Problem "$lang.json 缺少翻译键: $langKey"
			}
		}
	}
}

# 3. 模型内部引用
$modelFiles = Get-ChildItem -Path (Join-Path $assets 'models') -Recurse -File -Filter *.json
foreach ($file in $modelFiles) {
	$model = Read-Json -Path $file.FullName
	if ($null -eq $model) {
		continue
	}
	if ($model.PSObject.Properties.Name -contains 'parent') {
		Test-ModelReference -Reference $model.parent -Context $file.Name
	}
	if ($model.PSObject.Properties.Name -contains 'textures') {
		foreach ($name in $model.textures.PSObject.Properties.Name) {
			Test-TextureReference -Reference $model.textures.$name -Context "$($file.Name) textures.$name"
		}
	}
}

# 4. lang 键齐全
foreach ($lang in @('en_us', 'zh_cn')) {
	$langPath = Join-Path (Join-Path $assets 'lang') "$lang.json"
	if (-not (Test-Path $langPath)) {
		Add-Problem "缺少语言文件: $lang.json"
		continue
	}
	$content = Read-Json -Path $langPath
	foreach ($block in $blocks) {
		$key = "block.$modId.$block"
		if (-not ($content.PSObject.Properties.Name -contains $key)) {
			Add-Problem "$lang.json 缺少翻译键: $key"
		}
	}
	if (-not ($content.PSObject.Properties.Name -contains "itemGroup.$modId.circuit")) {
		Add-Problem "$lang.json 缺少物品组名称"
	}
}

# 5. 配方白名单：只有导线（红石粉互转）和电路终端有工作台配方；
#    其余方块必须通过「电路终端」的 /circuit-terminal get <id> 花导线换取（否则终端就没意义了）。
$allowedRecipes = @('wire', 'redstone_from_wire', 'terminal')
foreach ($file in (Get-ChildItem -Path (Join-Path $root "src\main\resources\data\$modId\recipes") -File -Filter *.json)) {
	if ($allowedRecipes -notcontains $file.BaseName) {
		Add-Problem "多出来的配方（只允许 wire / redstone_from_wire / terminal）: $($file.Name)"
	}
}
foreach ($name in $allowedRecipes) {
	if (-not (Test-Path (Join-Path $root "src\main\resources\data\$modId\recipes\$name.json"))) {
		Add-Problem "缺少配方: $name.json"
	}
}

# 5b. 配方结果都在本模组内且 id 合法
$recipesDir = Join-Path $root 'src\main\resources\data\circuit-redstone_logic\recipes'
foreach ($file in (Get-ChildItem -Path $recipesDir -File -Filter *.json)) {
	$recipe = Read-Json -Path $file.FullName
	if ($null -eq $recipe) {
		continue
	}
	$resultId = $recipe.result.item
	if (-not $resultId.StartsWith("$modId`:")) {
		continue # 产物是原版物品（例如 导线 -> 红石粉），这里只校验本模组产物
	}
	$resultBlock = $resultId.Split(':', 2)[1]
	if (($blocks -notcontains $resultBlock) -and ($plainItems -notcontains $resultBlock)) {
		Add-Problem "$($file.Name) 的产物 id 未注册: $resultId"
	}
}

# 6. 掉落表齐全：没有 loot table 的方块被挖掉后什么都不掉（生存模式会白丢方块）
foreach ($block in $blocks) {
	$lootPath = Join-Path $root "src\main\resources\data\$modId\loot_tables\blocks\$block.json"
	if (-not (Test-Path $lootPath)) {
		Add-Problem "缺少掉落表: data\$modId\loot_tables\blocks\$block.json"
		continue
	}
	$loot = Read-Json -Path $lootPath
	if ($null -eq $loot) {
		continue
	}
	$entry = $loot.pools[0].entries[0]
	if ($null -eq $entry -or $entry.name -ne "${modId}:$block") {
		Add-Problem "掉落表 $block.json 的第 1 个产物应为 ${modId}:$block"
	}
}

# 7. 指令提示文案：占位符数量必须与代码传参一致（防止"文案与传参顺序/个数"错位）
#    代码传参：unknown(id) / need_wires(需要, 现有) / granted(物品名, 消耗)
$commandTemplates = [ordered]@{
	'command.circuit-redstone_logic.terminal.unknown'            = 1
	'command.circuit-redstone_logic.terminal.need_wires'         = 2
	'command.circuit-redstone_logic.terminal.granted'            = 3
	'command.circuit-redstone_logic.terminal.players_only'       = 0
	'command.circuit-redstone_logic.terminal.need_terminal'      = 0
	'command.circuit-redstone_logic.terminal.exchange_terminal'  = 0
	'command.circuit-redstone_logic.terminal.inventory_full'     = 0
	'command.circuit-redstone_logic.terminal.amount_too_small'    = 2
	'command.circuit-redstone_logic.terminal.amount_not_multiple'  = 1
	'command.circuit-redstone_logic.set.players_only'            = 0
	'command.circuit-redstone_logic.set.no_target'               = 0
	'command.circuit-redstone_logic.set.not_settable'            = 0
	'command.circuit-redstone_logic.set.bad_number'              = 1
	'command.circuit-redstone_logic.set.bad_color'               = 1
	'command.circuit-redstone_logic.set.bad_period'              = 1
	'command.circuit-redstone_logic.set.clock'                   = 3
	'command.circuit-redstone_logic.set.too_long'                = 1
	'command.circuit-redstone_logic.set.ok'                      = 3
	'command.circuit-redstone_logic.set.variable_need_equals'    = 1
	'command.circuit-redstone_logic.set.variable_bad_kind'       = 2
	'command.circuit-redstone_logic.set.variable_need_quotes'    = 1
	'command.circuit-redstone_logic.set.variable_null_takes_no_value' = 1
	'tooltip.circuit-redstone_logic.variable.current'            = 1
	'command.circuit-redstone_logic.read.players_only'           = 0
	'command.circuit-redstone_logic.read.no_target'              = 0
	'command.circuit-redstone_logic.read.not_data'               = 0
	'command.circuit-redstone_logic.read.result'                 = 3
	'command.circuit-redstone_logic.read.variable_unset'         = 0
	'command.circuit-redstone_logic.debug.not_circuit'           = 0
	'command.circuit-redstone_logic.debug.no_network'            = 0
	'command.circuit-redstone_logic.debug.info_line'             = 3
	'command.circuit-redstone_logic.debug.info_empty'            = 0
	'command.circuit-redstone_logic.debug.chart_started'          = 2
	'command.circuit-redstone_logic.debug.chart_busy'             = 0
	'command.circuit-redstone_logic.debug.chart_too_big'          = 1
	'command.circuit-redstone_logic.debug.chart_failed'           = 0
	'command.circuit-redstone_logic.debug.chart_done'             = 3
	'command.circuit-redstone_logic.debug.chart_empty'            = 0
	'command.circuit-redstone_logic.debug.chart_truncated'        = 1
	'command.circuit-redstone_logic.debug.chart_saved_as'         = 1
	'command.circuit-redstone_logic.debug.chart_dialog'           = 0
	'command.circuit-redstone_logic.debug.chart_text'             = 1
	'command.circuit-redstone_logic.debug.chart_help'             = 0
	'command.circuit-redstone_logic.debug.chart_dialog_game'      = 0
	'command.circuit-redstone_logic.debug.video_started'          = 2
	'command.circuit-redstone_logic.debug.video_saved'            = 3
	'command.circuit-redstone_logic.debug.video_failed'           = 0
	'command.circuit-redstone_logic.debug.video_help'             = 0
	'command.circuit-redstone_logic.debug.video_mp4_saved'        = 2
	'command.circuit-redstone_logic.debug.video_mp4_failed'       = 0
	'command.circuit-redstone_logic.debug.video_mp4_unavailable'  = 0
	'command.circuit-redstone_logic.debug.video_too_long'         = 1
	'screen.circuit-redstone_logic.chart_save.title'              = 0
	'screen.circuit-redstone_logic.chart_save.title_video'        = 0
	'screen.circuit-redstone_logic.chart_save.folder_label'       = 0
	'screen.circuit-redstone_logic.chart_save.file_label'         = 0
	'screen.circuit-redstone_logic.chart_save.hint'               = 0
	'screen.circuit-redstone_logic.chart_save.save'               = 0
	'screen.circuit-redstone_logic.chart_save.cancel'             = 0
	'screen.circuit-redstone_logic.chart_save.invalid'            = 0
	'screen.circuit-redstone_logic.chart_save.invalid_folder'     = 1
	'screen.circuit-redstone_logic.chart_save.failed'             = 1
	'screen.circuit-redstone_logic.chart_save.system'             = 0
	'screen.circuit-redstone_logic.chart_save.no_system_dialog'   = 0
	'command.circuit-redstone_logic.debug.started'               = 2
	'command.circuit-redstone_logic.debug.change'                = 5
	'command.circuit-redstone_logic.debug.finished'              = 3
	'command.circuit-redstone_logic.debug.dead_loop'             = 1
	'command.circuit-redstone_logic.debug.error'                 = 1
	'command.circuit-redstone_logic.debug.stopped'               = 1
	'command.circuit-redstone_logic.debug.stopped_all'           = 1
	'command.circuit-redstone_logic.debug.no_such_id'            = 1
	'command.circuit-redstone_logic.debug.too_many'              = 1
	'command.circuit-redstone_logic.debug.reason.stable'         = 0
	'command.circuit-redstone_logic.debug.reason.dead_loop'      = 0
	'command.circuit-redstone_logic.debug.reason.cancelled'      = 0
	'command.circuit-redstone_logic.debug.reason.tick_limit'     = 0
	'command.circuit-redstone_logic.debug.reason.budget_limit'   = 0
	'command.circuit-redstone_logic.debug.reason.error'          = 0
}
foreach ($lang in @('en_us', 'zh_cn')) {
	$langPath = Join-Path (Join-Path $assets 'lang') "$lang.json"
	if (-not (Test-Path $langPath)) {
		continue
	}
	$content = Read-Json -Path $langPath
	if ($null -eq $content) {
		continue
	}
	foreach ($key in $commandTemplates.Keys) {
		if (-not ($content.PSObject.Properties.Name -contains $key)) {
			Add-Problem "$lang.json 缺少指令提示: $key"
			continue
		}
		$text = $content.$key
		$placeholders = ([regex]::Matches($text, '%s')).Count
		if ($placeholders -ne $commandTemplates[$key]) {
			Add-Problem "$lang.json 的 $key 占位符数量应为 $($commandTemplates[$key])，实际 $placeholders"
		}
	}
}

function Get-DisplayWidth {
	param([string]$Text)
	# MC 的 tooltip 不会自动换行：按"显示宽度"衡量（中日韩全角字符按 2 格算）。
	$width = 0
	foreach ($ch in $Text.ToCharArray()) {
		$code = [int]$ch
		$wide = (($code -ge 0x1100 -and $code -le 0x115F) -or
			($code -ge 0x2E80 -and $code -le 0xA4CF) -or
			($code -ge 0xAC00 -and $code -le 0xD7A3) -or
			($code -ge 0xF900 -and $code -le 0xFAFF) -or
			($code -ge 0xFE30 -and $code -le 0xFE6F) -or
			($code -ge 0xFF00 -and $code -le 0xFF60) -or
			($code -ge 0xFFE0 -and $code -le 0xFFE6))
		$width += if ($wide) { 2 } else { 1 }
	}
	return $width
}

# 8. 每个方块/物品都必须有双语、按行拆分的 tooltip：
#    键名是 tooltip.circuit-redstone_logic.<id>.0 / .1 / …（一行一个 Text 组件）
$tooltipCounts = @{}
foreach ($lang in @('en_us', 'zh_cn')) {
	$langPath = Join-Path (Join-Path $assets 'lang') "$lang.json"
	if (-not (Test-Path $langPath)) {
		continue
	}
	$content = Read-Json -Path $langPath
	if ($null -eq $content) {
		continue
	}
	$maxWidth = 44
	foreach ($id in ($blocks + $plainItems)) {
		$base = "tooltip.$modId.$id"
		$count = 0
		while ($content.PSObject.Properties.Name -contains "$base.$count") {
			$lineName = "$base.$count"
			$line = [string]$content.PSObject.Properties[$lineName].Value
			if ([string]::IsNullOrWhiteSpace($line)) {
				Add-Problem "$lang.json 的 tooltip 行为空: $base.$count"
			}
			if ($line -match '\\n') {
				Add-Problem "$lang.json 的 tooltip 行里出现了字面量 \n: $base.$count"
			}
			$lineWidth = Get-DisplayWidth -Text $line
			if ($lineWidth -gt $maxWidth) {
				Add-Problem "$lang.json 的 $base.$count 太宽（显示宽度 $lineWidth > $maxWidth）：$line"
			}
			$count++
		}
		if ($count -lt 2) {
			Add-Problem "$lang.json 的 $base 只有 $count 行（至少要拆成 2 行）"
		}
		if ($content.PSObject.Properties.Name -contains $base) {
			Add-Problem "$lang.json 里还存在旧的单条 tooltip 键（应改成按行编号）：$base"
		}
		$tooltipCounts["$lang/$id"] = $count
	}
}

# 两种语言的行数必须一致，否则切换语言时行数会跳变
foreach ($id in ($blocks + $plainItems)) {
	$enCount = $tooltipCounts["en_us/$id"]
	$zhCount = $tooltipCounts["zh_cn/$id"]
	if ($null -ne $enCount -and $null -ne $zhCount -and $enCount -ne $zhCount) {
		Add-Problem "tooltip 行数不一致: $id -> en_us=$enCount, zh_cn=$zhCount"
	}
}

# 9. 汇总
if ($problems.Count -gt 0) {
	Write-Output "资源校验失败，共 $($problems.Count) 个问题："
	foreach ($problem in $problems) {
		Write-Output "  - $problem"
	}
	exit 1
}

Write-Output "资源校验通过：$($jsonFiles.Count) 个 JSON，$($blocks.Count) 个方块"
