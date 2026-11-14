# 开发者文档

> 面向玩家的使用说明在仓库根目录的 [README.md](../README.md)（英文）与 [README_ZH.md](../README_ZH.md)（中文）。本文件保留协议细节、更新模型、构建与自测方法，便于后续维护。[English version](DEVELOPMENT.md)

# Circuit Redstone - Logic（circuit-redstone_logic）

Minecraft **1.20.4** / Fabric 的红石数字逻辑模组：二进制导线 + 逻辑门 + 限制信号器 + 输出任意信号器。

- 模组 id：`circuit-redstone_logic`
- 包名：`l.circuit_redstone.logic.cas`
- 依赖：Fabric Loader `>=0.20.1`、Fabric API、Java 17+

## 1. 信号转换协议

红石等级 `0..15` 与导线电平 `0/1` 之间只有一处转换实现：`l.circuit_redstone.logic.cas.logic.SignalProtocol`。

| 方向 | 规则 | 方法 |
| --- | --- | --- |
| 红石 → 导线（ADC） | `== 0` → `0`；`> 0` → `1` | `SignalProtocol.redstoneToWire(int)` |
| 导线 → 红石（DAC） | `0` → `0`；`1` → `15` | `SignalProtocol.wireToRedstone(int)` |

所有方块对外供电都遵守这套协议；导线自身对外提供的强度恒为 `DAC(1) = 15`，读取外部信号时按 `ADC(>0)` 量化。

## 2. 方块

| 方块 | id | 端口 | 输出 |
| --- | --- | --- | --- |
| 导线 | `wire` | 六向可连（含原版红石线） | 二进制，不衰减 |
| 与门 | `and_gate` | A=后方，B=左/右 | `A AND B`，DAC 到 0/15 |
| 或门 | `or_gate` | A=后方，B=左/右 | `A OR B`，DAC 到 0/15 |
| 非门 | `not_gate` | A=后方 | `NOT A`，DAC 到 0/15 |
| 异或门 | `xor_gate` | A=后方，B=左/右 | `A XOR B`，DAC 到 0/15 |
| 限制信号器 | `limiter` | A=后方，B=左/右 | `min(A, B)`（0..15 电平） |
| 比较器 | `comparator` | A=后方常量，B=右方常量，模式=左方等级 | 关系成立输出 15，否则 0 |
| 时钟 | `clock` | 无输入（自己计时） | 每 N tick 输出 1 tick 的高电平，N 由 `/circuit-set` 设置 |
| 输出任意信号器 | `scanner` | 左、右、上方各 15 格 | 扫描结果（0..15 电平） |
| 信号增强器 | `amplifier` | A=后方 | 输入 `>0` → 输出 **15**，零延迟 |
| 亮度传感器 | `brightness_sensor` | 无端口（读自身光照） | 输出 = 自身光照等级 0..15 |
| 常量 | `constant` | 无端口，不输出红石信号 | 保存一个整数，供扫描器取值 |
| 字符串 | `string_value` | 无端口，不参与红石逻辑 | 保存一段文本（最长 256 字符） |
| 变量 | `variable` | 无端口，不输出红石信号 | 一个方块存一个 int 或 str，数据线可读可写 |
| 数据线 | `data_wire` | 背面读、正面写（FACING 方向） | 把数据方块的搬运结果写进正面的数据方块 |
| 纯色方块 | `color_block` | 无端口，不参与红石/数据逻辑 | 保存 24 位 RGB，客户端按颜色着色 |
| 悬空方块 | `hover_block` | 无端口，不参与红石/数据逻辑 | 完整方块（能托红石粉），碰撞箱为空（玩家穿过），中间透明 + 一圈白框 |
| 隐形告示牌 | `hidden_sign` | 无端口，不参与红石/数据逻辑 | 方块本体全透明，文字由客户端 BER 绘制 |

约定与细节：

- **FACING 指向输出面**（输出沿 FACING 方向）。原版中继器也沿 FACING 输出，但它的
  `FACING = getHorizontalPlayerFacing().getOpposite()`，也就是输出**朝玩家自己**；
  本模组取 `FACING = getHorizontalPlayerFacing()`，所以**输出朝玩家看向的方向**——
  这就是"和中继器手感相反"的来源（原版语义见 `AbstractRedstoneGateBlock#getPlacementState`）。
- **输入 A = 后方**（输出面的对面），**输入 B = 左右两侧**；左右都有信号时**左优先**，与"限制信号器"的需求一致。
- 逻辑门的输入先做 ADC（`>0` 即高电平）参与布尔运算，输出再 DAC 成 0 或 15。
- 限制信号器与扫描器按 0..15 的**电平**工作：在纯导线电路里限制器就等价于与门，接原版红石线时则表现为"取较小者"。
  它们同时实现了原版比较器输出，因此模拟量可以被比较器读走。
- 扫描器规则：沿左、右、上三个方向各 15 格，**优先找常量方块**：
  射线上有常量时直接输出它的值（上 > 左 > 右，同方向取最近的），超出 0..15 会被夹取；
  没有任何常量时才统计**非空气方块数量**（上方有方块就取上方的数量，否则左右取较大者）。

  注意"非空气方块"是字面意思：红石线、导线、火把、水、树叶、以及墙**背后**的方块都会被数进去，
  而且射线所在高度就是扫描器自己的高度。若出现"看起来没方块却有输出"，看日志里这行即可定位：

  ```
  [circuit-redstone_logic] scanner at x,y,z: left=<左计数> right=<右计数> up=<上计数> -> power=<输出>
  [circuit-redstone_logic] scanner at x,y,z: constant value <值> -> power=<输出>
  ```

  （只在电平发生变化时打印一次。）

- 数据方块（`constant` / `string_value` / `variable`）**自身不输出任何红石信号**，也不与导线连接，
  它们只是可写的容器：`/circuit-set <值>` 写入，扫描器读取常量值。
  常量值保存在方块实体的 NBT 里（世界存档会保留），字符串同理。
- **变量方块**（`variable`）是"一个方块存一个变量"：
  - 类型（`unset` / `null` / `int` / `str`）放在 BlockState 属性 `KIND` 里（`EnumProperty<VariableKind>`，
    1.20.4 没有字符串属性，所以枚举实现了 `StringIdentifiable`，属性值取 `asString()`）；
    客户端据此切换四套模型（`variable_unset` / `variable_null` / `variable_int` / `variable_str`）。
  - 值（`number` / `text`）存在方块实体里。**类型与值必须一起改**，唯一入口是
    `VariableBlock.apply(world, pos, state, value)`：写 NBT + `setBlockState(pos, state.with(KIND, kind), NOTIFY_LISTENERS)`。
  - 写入语法 `null` / `int=42` / `str="hi"` 由 `logic/VariableAssignment` 解析（纯逻辑、无 MC 依赖，
    所以 `tools/LogicCheck` 能直接测）；`null` 是唯一不需要值的写法，命令层只把 `Failure.error()` 翻译成聊天栏消息。
  - **`null` 是"有值但为空"**：`DataConversion.Kind` 增加了第三种 `NULL`，`Value#asConstant()` 给 0、
    `Value#asText()` 给空串，于是 `DataConversion.convert` 不用特判就能实现
    "null → 常量 = 0、null → 字符串 = 空串"。而 `UNSET`（从未设置）在 `DataWireBlock#readSource` 里是"没有源"，
    不会写任何东西——这两者行为必须保持不同，否则"清空"和"没接"就没法区分。
  - **数据线双向**：`DataWireBlock#readSource` 把变量当源（`unset` 视为"没有源"，`null` 视为空值）；
    `writeVariable` 按变量当前类型转换（`logic/VariableWrite`：`unset` / `null` 跟随源的类型），
    类型冲突（文本 `"abc"` → int 变量）走 `error=true` 红底贴图。
  - **挖起保留**：掉落表 `variable.json` 用 `minecraft:copy_nbt`（`source: block_entity`，
    把 `kind` / `number` / `text` 复制到 `BlockEntityTag.*`）。放置时 `VariableBlockItem#postPlacement`
    在 `BlockItem` 写完 NBT 之后再同步一次 BlockState 的 `KIND`（否则贴图还停在 `unset`）。
    创造模式中键取方块由 `VariableBlock#getPickStack` 带 NBT。
  - 物品 tooltip 多一行"当前值"（`tooltip.circuit-redstone_logic.variable.current`，读 `BlockEntityTag`）。
  - 它**不参与**扫描器 / 比较器：那两个只认常量方块。
- **数据线**（`data_wire`）把数据从一个数据方块搬到另一个：
  - 箭头画在**东西两个侧面**上（从侧面看是正的，不会被顶面透视压斜）；默认（`facing=north`）指向北，
    模型随 `facing` 旋转后箭头即指向数据流动方向；**背面**（`facing` 的反面）读源，**正面**写目标。
  - 转换规则：常量 → 常量/字符串（字符串用十进制文本）；字符串 → 字符串；
    字符串 → 常量要求内容**是整数**（允许首尾空白与正负号）。
  - 字符串内容不是整数却要写进常量时，那根数据线进入 `error=true` 状态、换成红色错误贴图；
    把字符串改成整数后会自动恢复。
  - **支持串联**：连续摆放的数据线会沿链条把源值接力传下去（最多 64 根，遇环放弃），
    只有"正面紧贴数据方块"的那一根负责写入。
  - 驱动是每 tick 一次的**计划刻**（不是方块实体、也不需要红石信号），计划刻随存档保存。
- **纯色方块**（`color_block`）放下时是黑色 `#000000`，用 `/circuit-set #RRGGBB` 改色；
  颜色存在方块实体里，客户端用 `ColorProviderRegistry` 给白色模型着色（客户端入口
  `CircuitRedstoneClient`）。因为颜色不是 BlockState 的一部分，改色后会先同步方块实体数据、
  再在 1 tick 后换一个 `shade` 变体逼客户端重建网格，颜色才会显示出来。

- **隐形告示牌**（`hidden_sign`）：
  - 方块本体用**空模型**（`hidden_sign.json` 只有 `textures.particle`，没有 `parent` 也没有 `elements`）：
    烘焙后没有任何面，方块彻底不渲染。**不要**用"全透明贴图 + cutout"来伪装隐形——
    `render_type` 是 1.20.5+ 的字段，1.20.4 会忽略它，方块落到 solid 层后透明像素会被画成**黑块**；
    物品图标另用 `textures/item/hidden_sign.png`（否则物品栏里什么都看不到）。
  - 文字存在 `HiddenSignBlockEntity`（NBT `text`），写入走 `HiddenSignBlock#setText`：
    `markDirty` + `HiddenSignBlockEntity#syncToClients`（逐玩家发 `BlockEntityUpdateS2CPacket`）。
  - **方块实体数据必须显式同步**：文字不在 BlockState 里，所以 `setText` 里
    `world.updateListeners(...)` 是没有用的——那只刷新方块状态，客户端方块实体仍然是空的。
    必须同时实现 `toUpdatePacket()`（指令写入后立刻到客户端）与 `toInitialChunkDataNbt()`
    （区块加载/重进世界时带上文字），两条缺任何一条的表现都是"指令说写成功、牌子上还是占位提示"。
    缺 `toUpdatePacket` 时连重进世界也修不好，因为初始区块数据同样是空的。
  - 渲染在客户端 `HiddenSignRenderer`（`BlockEntityRenderer`）：用 `TextRenderer` 在方块正反两面各画一次，
    按显示宽度自动换行（最多 4 行）、白色文字；文本为空时画斜体灰色的
    `block.circuit-redstone_logic.hidden_sign.placeholder`（"请输入文本..."）。
  - 文字沿法线的偏移必须 **≥ 0.5**（`SURFACE_OFFSET = 0.501`）：这个方块体积是完整立方体，
    半宽就是 0.5，用原版告示牌的 0.4375 会把文字埋进方块内部、被方块面挡住（表现为"只有方块没有字"）。
  - 构造器里有一行 `hidden sign renderer created` 日志：排查"没有字"时先看它，确认 BER 真的被创建。
  - `/circuit-set` 分支：`""` 视为清空（否则 greedyString 会把引号当文本的一部分）。
  - 它不参与红石/数据逻辑，也不是 `CircuitPart`（不会出现在 `info full-circuit` 与运行图里）。

- **悬空方块**（`hover_block`）是"看起来镂空、功能上是完整方块"的垫块，实现要点：
  - **支撑判定用 `getSidesShape`**（返回完整立方体）：红石粉的 `canPlaceAt` 走
    `isSideSolidFullSquare(UP)` → `SideShapeType.FULL.matches` → `BlockState#getSidesShape`
    → **`Block#getSidesShape`（可覆盖）**，与碰撞形状无关。第一版只给了完整 outline、
    碰撞箱留空，红石粉会被判成"没支撑"而掉落——`getSidesShape` 才是正确入口。
  - **碰撞形状**为空（`getCollisionShape` → `VoxelShapes.empty()`）：玩家、掉落物直接穿过；
  - **剔除形状**为空（`getCullingShape` → empty）：不剔除相邻方块的面，
    否则半透明处会露出被剔除的背面，看着像破洞；
  - **不能设 `nonOpaque()`**：不透明属性是"能托方块"的前提之一，代价是它会挡光——
    这一条写进了 README 的"已知限制"；
  - **贴图**是"中间透明 + 4px 白框"（`New-FrameTexture`），alpha 阈值化成 1-bit；
    **客户端注册 cutout 渲染层**（`BlockRenderLayerMap.INSTANCE.putBlock(HOVER_BLOCK, RenderLayer.getCutout())`），
    模型里也写了 `"render_type": "minecraft:cutout"` 作为双保险。
  - **模型必须带 `parent`**（这里用 `minecraft:block/cube`）：只写 `textures`、没有 `parent` 也没有 `elements` 的模型
    会被烘焙成**空模型**——方块完全不渲染，玩家只能看到自己的方块选择框。这就是"悬空方块隐形"的真正原因。

- 信号增强器做的是 `DAC(ADC(输入))`：输入端只要有任何强度就输出满强度 15。
  它的意义是替代原版中继器的"提信号"功能而**不付延迟**——例如红石线衰减到 8 时，
  增强器直接给出 15，而中继器要花 1~4 红石刻。
- 亮度传感器读取**自身所在位置**的光照等级（天空光与方块光取较大者，并按当前时间/天气折算），
  输出同强度信号。光照变化不会触发相邻更新，所以它每 10 tick 轮询一次；
  相邻方块变化时仍会立刻重算。

### 电路终端与 `/circuit-terminal`

- **电路终端**（`circuit-redstone_logic:terminal`）是纯物品，配方为九宫格：
  ```
  红石粉  导线  红石粉
   导线  工作台  导线
  红石粉  导线  红石粉
  ```
- 指令（**不需要管理员权限**，门槛是"背包里带着电路终端"）：
  - `/circuit-terminal list` —— 列出本模组全部方块/物品，格式 `名称: id`（名称用可翻译文本，客户端本地化）。
  - `/circuit-terminal get <id>` —— 扣 **4 个导线**，给 `ExchangeRule.perBatch(path)` 个
    `circuit-redstone_logic:<id>`；`id` 不用写命名空间前缀（写 `circuit-redstone_logic:xxx` 也接受，但只接受本模组 id）。
- 价格与数量规则集中在 `logic/ExchangeRule`（纯逻辑，可单测）：
  - 一批固定 `WIRE_COST = 4` 个导线；
  - `perBatch(path)`：普通方块 `DEFAULT_AMOUNT = 1`，`BULK_PATHS = {hover_block, color_block}`
    一批 `BULK_AMOUNT = 16`；
  - `isValidAmount(path, n)`：`n >= perBatch` 且 `n % perBatch == 0`（只能整批买）；
  - `costFor(path, n)`：`WIRE_COST * (n / perBatch)`，非法数量返回 `-1`。
  - `/circuit-terminal get <id> [n]`：不写 n = 买一批（与旧行为一致）；写 n 必须整批，
    价格按批数算（`get and_gate 3` = 12 导线，`get hover_block 32` = 8 导线）。
  新增"成批兑换"的方块只需把 id 加进 `BULK_PATHS`，不用改命令层。
- 背包容量检查（`fitsAfterExchange` → `simulateInsert`）按**实际数量**模拟"先填同类堆、再占空格"，
  所以 16 个一批不会出现"只检查 1 个 → 放不下却已扣导线"的漏洞；扣导线发生在检查之后。

### 数据方块与 `/circuit-set`

- **常量**（`constant`）保存一个整数，**字符串**（`string_value`）保存一段文本，
  **变量**（`variable`）一个方块存一个 int 或 str（类型可换、可被数据线写）。
  三者都不发光、不供电、不与导线电气连接，只是可写的容器（数据存在方块实体的 NBT 里）。
- 写法（不需要管理员权限）：
  - 准星对准方块：`/circuit-set 7`（目标是常量时按整数解释）、`/circuit-set hello world`（目标是字符串时按原文写入，上限 256 字符）、`/circuit-set #12AB34`（目标是纯色方块时按十六进制颜色，支持 `#RRGGBB` / `RRGGBB` / `0xRRGGBB`）。
  - **变量必须用类型前缀**：`/circuit-set null`（不需要值）、`/circuit-set int=42` 或
    `/circuit-set str="hi"`（`str` 的值必须被一对双引号包住；`int` 不接受引号）。
    缺等号 / 类型名不认识 / 值不是整数 / 文本没加引号 / `null` 后面带了值，各有独立错误消息
    （`set.variable_need_equals` / `bad_kind` / `bad_number` / `need_quotes` / `null_takes_no_value`）。
  - 坐标形式：`/circuit-set <x> <y> <z> <值>`（控制台/命令方块也能用，服务端自测走这条）。
  - 目标是常量却给了非整数 → 明确报"不是整数"；目标是纯色方块却给了非法颜色 → 报"不是十六进制颜色"；目标不是数据方块 → 明确报"不是常量、字符串或变量"。
- 读值：`/circuit-read`（准星对准常量/字符串/变量）或 `/circuit-read <x> <y> <z>`，
  输出固定格式 `<方块名> (<x, y, z>): <值>`（字符串与变量的文本都用引号包住，例如
  `String (32, 100, 1): "hello world"`；变量未设置时输出 `(not set)`）。
- 常量的唯一消费者是**扫描器**：扫描器左/右/上射线上有常量时，输出常量的值而不是方块数量
  （上 > 左 > 右，同方向取最近的；超出 0..15 夹取）。字符串目前不参与任何红石逻辑。
- 值改变后会显式通知四周，扫描器立即重算（不必等 10 tick 的周期重扫）。

### 配方一览

**只有下面 3 条工作台配方**；其余方块一律走 `/circuit-terminal get <id>`（每次 4 导线），
生成器里有白名单清理，多出来的配方会被删掉、校验器也会报错。

| 配方 | 类型 | 结果 |
| --- | --- | --- |
| 红石粉 | 无序 | 1 导线 |
| 导线 | 无序 | 1 红石粉 |
| 九宫格（见上） | 有序 | 1 电路终端 |

### 延迟与吞吐（关于"红石刻限制"）

- **延迟**：导线 / 逻辑门 / 限制器 / 扫描器 / 增强器全部是"同游戏刻内收敛"，
  也就是**没有中继器式的 1~4 红石刻延迟**；长链也是在一个 tick 内传播完，与链长无关
  （调度器是工作队列 + 代数失效，链多长都只受单 tick 预算限制）。
- **吞吐**：求解器每个世界每 tick 最多处理 `CircuitUpdates.STEP_BUDGET = 16384` 个位置，
  按 20 tick/s 计算，理论上限约 **32 万次求值/秒**，远高于原版红石每秒几十次的节奏。
- **反馈环**：同一位置在一个 tick 内最多推进
  `CircuitUpdates.MAX_EVALUATIONS_PER_POSITION = 32` 轮（可调）。这就是"解除红石刻节流"的旋钮：
  振荡器/反馈电路不再被"一 tick 一轮"钉死；超过上限的位置会被挂起到下一个 tick，避免活锁把服务器卡住。
- **无法突破的硬限制**：任何**原版方块**（红石线、观察者、活塞、红石灯、比较器……）只按 20 Hz 采样，
  这是 Minecraft 主循环决定的，模组无法在不改 tick 循环的前提下突破。所以"比红石刻更快"只发生在
  本模组自己的网络内部；一旦信号交给原版方块，就回到 20 Hz 的世界。

### 导线与原版红石线的关系

导线只读取**贴着它的那一格**红石线，按协议 `ADC(强度 > 0)` 决定高低电平——所以原版红石线的**逐格衰减是被尊重的**：
一条红石线走到强度 0 的位置，接上去的导线就是低电平。

难点在于这条红石线可能正被我们这根导线自己点亮（导线向相邻红石线供 15）。为了既不丢失衰减、又不自锁，
判断信号源时会用 `DustPower` 按原版规则把这格红石线的强度重算一遍，并把**本次求值的整张导线网络当作不存在**：

```
power(红石线) = max( 非红石线邻居的弱电, 相邻红石线的 power - 1 )
```

于是得到的是"如果没有我这根导线，这格红石线会是多少强度"：

- 远端衰减到 0 的红石线 → 独立强度 0 → 导线低电平（**修复：不再误判为高电平**）；
- 红石线背后有真实来源（拉杆/火把/红石块/中继器…）→ 独立强度 > 0 → 导线高电平；
- 导线点亮红石线不会反过来点亮自己 → 不会自锁。
- 导线网络只认**弱电**（不采用实心方块的强电放大），否则会出现
  "导线点亮红石线 → 红石线把下方石块强电点亮 → 石块又给导线供电"的环路。
- 贴着红石线的网络会给自己安排一个周期性的**方块计划刻**
  （`WireNetwork.DUST_RESCAN_INTERVAL_TICKS`，默认 10 tick）做低频重算：
  红石线只在自身强度变化时通知邻居，而"红石线周围的来源变化"（以及被我们供电掩盖的变化）不会通知导线。
  计划刻会随区块存档保留，世界重载后依然有效；纯导线网络不安排计划刻，零额外开销。

## 3. 更新模型（防递归）

- 所有 `neighborUpdate` / `onBlockAdded` / `onStateReplaced` 只做一件事：**把位置放进待处理队列**，不在回调里计算或写方块。
- 队列在 `ServerTickEvents.END_WORLD_TICK` 里由 `CircuitUpdates.flush` **迭代**消费（零递归），计算中新入队的位置会追加到同队列尾部，所以长链在一个 tick 内就能传播完。
- 写状态统一用 `Block.NOTIFY_LISTENERS`（不触发原版邻居级联），随后对变化位置显式 `updateNeighborsAlways` + `updateComparators` 做定点通知。
- 三重保险：单 tick 步数上限、同一 tick 同一位置求值次数上限（把反馈环降级成 1 tick 振荡器）、flush 重入锁。
- 导线按**整张网络**求值：网络电平 = 网络上是否存在外部信号源，避免"无源导线环互相供能而自锁"。
- 扫描器由 `BlockEntityTicker` 每 10 tick 触发一次扫描，绝不每 tick 做 15 格扫描；相邻方块变化时通过脏标记立即重算。
- 与原版红石线耦合的导线网络另外安排周期性的方块计划刻（见上一节），用来兜住"导线收不到通知"的边角情况。
- 拆方块必须走 `super.onStateReplaced`：原版在 `AbstractBlock#onStateReplaced` 里移除方块实体，
  漏掉这一步会留下孤儿方块实体（日志刷 `invalid for ticking`、存档越存越大）。旧存档里的孤儿实体由
  `OrphanBlockEntityCleanup` 在区块加载时清理（只动本模组自己的方块实体类型）。

## 3.5 底层调试：`/circuit-debug`

架构是"**一个沙盒引擎 + 多种执行策略**"：

- `sim/CircuitSimulator`：唯一的推演引擎。采集快照 → 在后台线程按 tick 推演沙盒 → 每次状态变化回调给策略。
- `sim/SimulationSandbox`：沙盒快照，同时是一份 `CircuitContext`（世界视图）。
- `logic/CircuitContext` + `logic/WireRule`：**真实世界与沙盒共用同一份求值逻辑**
  （导线的供电判定、门的端口读取、扫描器计数、传感器光照都不是第二份实现）。
- `sim/SimulationManager`：线程池（2 线程、守护线程）、模拟 ID、取消、服务器关闭时清理。

子指令：

| 指令 | 作用 |
| --- | --- |
| `/circuit-debug info full-circuit` | 读取**真实世界**（非沙盒）里**所有已加载区块**的**全部电路方块**（导线 + 各逻辑门/限制器/增强器/扫描器/传感器 + 常量/字符串），按 `<方块名> (<x,y,z>) [<SNBT>]` 一行一个方块输出；不需要准星、也不要求是导线 |
| `/circuit-debug test start <x> <y> <z>` | 采集该坐标周边导线网络并注入一个 tick 的高电平脉冲，逐条播报每个方块的变化（旧/新 SNBT），并给出模拟 ID；起点可以是导线，也可以是任意电路方块（取其六邻的导线作为网络起点） |
| `/circuit-debug test dead-loop <x> <y> <z>` | 同上，但用**全局配置哈希**做重复检测（只存 64 位哈希，不堆 SNBT 字符串），命中即抛 `InfiniteLoopException` 并在聊天栏红字报出周期 |
| `/circuit-debug test stop` | 取消该执行者（玩家或控制台）的全部模拟 |
| `/circuit-debug test stop <模拟ID>` | 取消指定模拟 |
| `/circuit-debug run-chart <A> <B> <ticks>` | 观察真实世界里矩形 A~B 区域，记录 C tick 内每一次方块状态变化，渲染成 PNG 运行图 |
| `/circuit-debug run-video <A> <B> <ticks>` | 同样的录制，渲染成动画 GIF + （有 ffmpeg 时）同名 MP4（一帧一个 tick、俯视图，上限 600 tick） |

### 运行图 / 视频（`run-chart` / `run-video`）实现

- `debug/ChartSession`：在 `END_WORLD_TICK` 里逐 tick 扫描矩形（上限 16384 方块 / 2400 tick；
  视频上限 600 tick），用 `BlockState` 身份比较检测变化（状态实例是注册表内 intern 的），
  每个位置保留初始状态 + 变化序列。两个子指令共用同一份录制与落盘逻辑，只有渲染器不同（`Mode`）。
- `debug/ChartRenderer`：画"时间轴热力图"——每行一个变化过的位置（行首写方块名+坐标），
  每列一个 tick；格子里写电平数值（格子够宽时），底部画图例 + 0..15 渐变条；
  tick 多时每 tick 压到 1px。**纯 AWT `BufferedImage` + `ImageIO`**，无窗口也能渲染，所以专用服务器可用。
- `debug/VideoRenderer`：把同一份记录渲染成**动画 GIF**——每帧是区域俯视图（每列取最高非空气方块），
  一帧一个 tick（20 帧/秒，GIF delay=5）；用 JDK 自带的 `GIF ImageWriter#writeToSequence` 编码，
  并写入 `GraphicControlExtension(delayTime)` + NETSCAPE 循环标记。**不依赖 ffmpeg**。
  渲染与导出共用 `forEachFrame` 一条时间线（tick 0..N，各一帧），保证 GIF 与 MP4 帧序一致。
- `debug/VideoEncoder`：GIF 之外再编一个 MP4。探测 `ffmpeg`（PATH → `%LOCALAPPDATA%\Microsoft\WinGet\Links`
  → `%ProgramData%\chocolatey\bin`；只缓存"找到了"这个结论，找不到就每次重探，装完不用重启游戏）。编码流程：
  `VideoRenderer.exportFrames` 把每帧写成 `frame-%04d.png`（临时目录，结束后递归删除）→
  `ffmpeg -framerate 20 -start_number 0 -i frame-%04d.png -vf pad=ceil(iw/2)*2:ceil(ih/2)*2
  -c:v libx264 -pix_fmt yuv420p`（yuv420p 要求偶数宽高，先 pad）；失败再退回 `-c:v mpeg4`；
  超时 120 s。**GIF 永远先写盘**，MP4 失败只影响那一个文件，不会让整次录制失败。
- `debug/ChartSaver`：先写 `<游戏目录>/circuit-charts/`（PNG/GIF + 同名 `.txt` 逐条变化清单）；
  客户端环境下弹**游戏内"另存为"界面**（`client/ChartSaveScreen`，目录与文件名都可改）；
  AWT `FileDialog`（系统对话框）只在非 headless 时由界面里的"系统对话框…"按钮触发。
  弹窗期间世界照常 tick。
- 冒烟测试会真的跑一次 `run-chart`，断言日志里出现 `ticks=… blocks=…` / `Chart saved:`，
  并检查磁盘上生成了非空 PNG（`CHART_IMAGE_WRITTEN`）；第三轮跑 `run-video`，检查 GIF 魔数
  （`CHART_VIDEO_WRITTEN`）和 MP4 的 `ftyp` 盒子（`CHART_MP4_WRITTEN`，机器上没有 ffmpeg 时记 SKIP）。

所有子指令都可以由控制台/命令方块执行（输出走 `CommandSource#sendFeedback`），便于服务端自测。

安全与上限（全部硬编码为常量）：

- **线程**：快照在主线程只读采集（`World` 不能跨线程访问），推演全在后台线程；主线程只负责 `server.execute(...)` 发包，绝不阻塞等待。
- **规模**：单次快照 ≤ 4096 节点；推演 ≤ 10000 tick、≤ 5 秒墙钟；同时最多 4 个模拟任务。
- **输出**：`info` 一次性输出全部方块，**一个方块一条聊天消息**（原版聊天不把 `\n` 当换行，把多行塞进一条消息在游戏里只会显示成一整行），且没有表头/统计/截断行；注意聊天栏滚动历史只保留最近 100 条，方块特别多时最早的那些会滚出历史记录（渲染时仍全部刷过一遍）。`test` 每 tick 最多上报 100 条变化。
- **异常**：推演中任何异常都会被捕获 → 安全结束并释放任务，不会拖死服务器。

## 4. 渲染占位方案

`tools/GenerateAssets.ps1` 生成全部占位资源（贴图用 **ImageMagick** 的 `magick` 命令绘制，
需要本机装了 ImageMagick 7；脚本里统一输出 `-depth 8 -colorspace sRGB` 的 RGBA PNG，
因为 Minecraft 只认 8-bit 贴图）：

- 16×16 纯色贴图（1px 描边 + 类型色块）；
- 数据线贴图额外用 `polygon` 画出方向箭头（正常=薄荷色，转换失败=红底浅色）；
- 导线用 `multipart`：中心方块 + 六个方向的细臂，通电时切换高亮贴图；
- 电路方块用 `multipart` 只按 `facing` 旋转，正面（front）为输出面；
- 数据线用 `facing × error` 的 `multipart`，箭头随朝向旋转、失败时整块换错误贴图；
- 纯色方块模型带 `tintindex: 0`，客户端 `ColorProviderRegistry` 按方块实体颜色着色；
- 通电状态用 `_on` 贴图区分（**不发光**，避免污染亮度传感器的读数）；
- 物品模型直接继承方块模型。

## 5. 构建

本仓库自带离线依赖缓存 `.gradle-home/`（未纳入版本控制），构建时把 `GRADLE_USER_HOME` 指过去即可，不需要联网：

```powershell
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) '.gradle-home')
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'   # 运行 Gradle/Loom 的 JDK；产物仍按 Java 17 编译
.\gradlew.bat build
```

产物：`build/libs/circuit-redstone_logic-1.28.7.jar`

注意：本工程锁定 `loom_version=1.19-SNAPSHOT`，它解析到的 Loom 1.19.4 要求构建 JVM ≥ 25，
因此 `JAVA_HOME` 必须是 25；模组本身仍然用 `options.release = 17` 编译，运行时只需要 Java 17+。

工程启用了一个 `access widener`（`src/main/resources/circuit_redstone_logic.accesswidener`、`build.gradle` 里的
`loom { accessWidenerPath }`、`fabric.mod.json` 的 `accessWidener` 字段三处互相配套）：只放宽
`ThreadedAnvilChunkStorage#entryIterator`，让 `/circuit-debug info full-circuit` 能遍历"当前世界全部已加载区块"。

运行开发环境（会自动重编译）：

```powershell
.\gradlew.bat runServer      # 专用服务器
.\gradlew.bat runClient      # 客户端（需要先下载 1.20.4 资源）
```

## 6. 自测

纯逻辑自测（不需要启动游戏）：

```powershell
javac -encoding UTF-8 -cp build/classes/java/main -d build/tmp/logiccheck tools/LogicCheck.java
java -cp "build/classes/java/main;build/tmp/logiccheck" LogicCheck
```

资源静态校验（JSON 合法性 + 模型/贴图/lang/配方引用齐全）：

```powershell
pwsh -File tools/ValidateAssets.ps1
```

运行时冒烟自测（起 3 次专用服务器，在真实世界里放置方块并用 `/execute if block` 断言状态）：

```powershell
pwsh -File tools/SmokeTest.ps1
```

覆盖：导线导通/熄灭、无源导线环不自锁、导线 ↔ 原版红石线双向转换、
AND/NOT/XOR 真值、限制信号器、扫描器的即时响应与周期重扫、
常量/字符串写入（`/circuit-set` 的坐标形式）、扫描器读常量（含 20→15 夹取）与"字符串不当作常量"、
数据线搬运（常量→常量、常量→字符串、字符串→常量、串联、转换失败的错误贴图与恢复）、
`/circuit-read` 的输出格式与"非数据方块"报错、
纯色方块颜色写入（`/circuit-set #RRGGBB`）、
变量方块（`null` / `int=` / `str="…"` 赋值、类型贴图、语法错误的四种消息、数据线双向读写、
`unset` 不覆盖目标、`null` 搬成 0 / `""`、类型冲突红底、挖掉后掉落物带 `BlockEntityTag`）、
悬空方块（能托住红石粉、对照"空气中的红石粉会被移除"、挖掉掉自己；16 个/批的数量由 `LogicCheck` 覆盖）、
`/circuit-debug info full-circuit` 的输出契约。

## 7. 手测清单

1. 放置导线，确认导线之间、导线与原版红石线自动相连并画出连接臂。
2. 原版红石线供 1 级以上信号 → 导线亮起；导线通电 → 相邻原版红石线变成 15。
3. 逻辑门：后方接 A，左侧接 B（再试右侧），按真值表逐项验证 AND/OR/NOT/XOR 输出。
4. 限制信号器：A=15、B=8（用红石线分级）时输出 8；左右都有信号时确认左优先。
5. 扫描器：在左/右/上放不同数量的方块，确认取"上方优先、否则左右较大者"；确认更新间隔约 10 tick。
6. 常量/字符串：放下常量 → 准星对着它 `/circuit-set 9` → 扫描器射线里对着它时应输出 9；
   换成字符串方块执行 `/circuit-set hello world`，确认扫描器仍然按数量统计（字符串不是常量）。
7. 数据线：常量 5 → 数据线（箭头指向字符串）→ 字符串，确认字符串变成 "5"；
   把字符串改成正整数再接到常量，确认常量随之改变；把字符串改成 `abc`，确认数据线显示红色错误贴图；
   两根数据线首尾相接，确认仍然能传。
8. 纯色方块：放下后是黑色，准星对着它 `/circuit-set #12AB34`，确认 1 tick 内方块变成该颜色；
   再用 `/circuit-set #GG0011` 确认报"不是十六进制颜色"。
9. 变量方块（贴图与放置恢复只能在客户端手动确认）：
   - 放下后是**灰色**（unset）；`/circuit-set int=42` → 变**橙色**；`/circuit-set str="hi"` → 变**蓝色**；
   - **挖掉**后把物品捡起来，鼠标停在物品上应看到 `当前值：42` 一行；
   - **重新放下**后贴图立刻是橙色且 `/circuit-read` 读回 42（这一步验证 `postPlacement` 的状态同步）；
   - 中键取方块（创造模式）后放下，值同样保留。
10. 悬空方块：
    - 放下后应看到**一圈 4px 白框**（中间能看到后面的东西，不是黑块 → 验证 cutout 渲染层；只剩选择框 = 模型空）；
    - 走进它应该**直接穿过去**（无碰撞）；
    - 在它**上面放红石粉**应该能放下并正常工作（对照：空气中放红石粉会立刻掉落）；
    - 挖掉应掉自己；电路终端 `get hover_block` 应给 **16 个**。
11. 压力测试：搭一段长导线环（无信号源）确认会正常熄灭；搭 NOT 门自环确认只是振荡而服务器不卡死。

## 8. 已知限制

- 原版红石线不会朝本模组的导线绘制连接臂（原版渲染逻辑限制），但电气上完全互通；连接臂由本模组的导线一侧绘制。
- 1.20.4 没有 `Tickable` 接口，等价物是 `BlockEntityTicker`（由 `BlockEntityProvider#getTicker` 提供），扫描器采用后者。
- 导线网络单张上限 4096 个位置，超出会截断并在日志里告警。
- **改模组 ID 会清空旧存档里旧 ID 的方块**（原版把未知方块当空气、并 `Skipping BlockEntity with id <旧ID>:*`）。
  改 ID 前必须先备份 `run/saves/<世界>`；要保留旧电路，就得在**打开世界之前**离线改写 region 文件里
  调色板与方块实体里的命名空间（本仓库暂未内置这个迁移工具）。
  本模组已从 `circuit-redstone` 改名到 `circuit-redstone_logic`，旧存档里用旧 ID 搭的电路不会自动迁移。
