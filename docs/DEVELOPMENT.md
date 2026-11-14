# Developer documentation

> Player-facing documentation lives in [README.md](../README.md) (English) and [README_ZH.md](../README_ZH.md) (Simplified Chinese), and this file has a Chinese translation at [DEVELOPMENT_ZH.md](DEVELOPMENT_ZH.md). This file keeps the protocol details, the update model and the build and self-test procedures needed to maintain the project.

# Circuit Redstone - Logic (`circuit-redstone_logic`)

A redstone digital-logic mod for Minecraft **1.20.4** / Fabric: binary wires + logic gates + an analog limiter + analog outputs.

- Mod id: `circuit-redstone_logic`
- Package: `l.circuit_redstone.logic.cas`
- Dependencies: Fabric Loader `>=0.20.1`, Fabric API, Java 17+

## 1. Signal conversion protocol

There is exactly one implementation of the conversion between the redstone level `0..15` and the wire level `0/1`: `l.circuit_redstone.logic.cas.logic.SignalProtocol`.

| Direction | Rule | Method |
| --- | --- | --- |
| Redstone -> wire (ADC) | `== 0` -> `0`; `> 0` -> `1` | `SignalProtocol.redstoneToWire(int)` |
| Wire -> redstone (DAC) | `0` -> `0`; `1` -> `15` | `SignalProtocol.wireToRedstone(int)` |

Every block follows this protocol when it powers its neighbours; a wire always offers `DAC(1) = 15` outward and quantises incoming signals with `ADC(>0)`.

## 2. Blocks

| Block | id | Ports | Output |
| --- | --- | --- | --- |
| Wire | `wire` | six-way connection (including vanilla dust) | binary, no decay |
| AND gate | `and_gate` | A = back, B = left/right | `A AND B`, DAC to 0/15 |
| OR gate | `or_gate` | A = back, B = left/right | `A OR B`, DAC to 0/15 |
| NOT gate | `not_gate` | A = back | `NOT A`, DAC to 0/15 |
| XOR gate | `xor_gate` | A = back, B = left/right | `A XOR B`, DAC to 0/15 |
| Limiter | `limiter` | A = back, B = left/right | `min(A, B)` (0..15 level) |
| Comparator | `comparator` | A = Constant behind, B = Constant on the right, mode = level on the left | 15 when the relation holds, otherwise 0 |
| Clock | `clock` | no input (it keeps time itself) | one tick of high every N ticks, N set with `/circuit-set` |
| Scanner | `scanner` | 15 blocks left, right and up | scan result (0..15 level) |
| Amplifier | `amplifier` | A = back | input `>0` -> output **15**, zero delay |
| Brightness sensor | `brightness_sensor` | no ports (reads its own light) | output = its own light level 0..15 |
| Constant | `constant` | no ports, emits no redstone | stores one integer, read by the scanner |
| String value | `string_value` | no ports, no redstone logic | stores one piece of text (up to 256 characters) |
| Variable | `variable` | no ports, emits no redstone | one int or str per block, readable and writable by data wires |
| Data wire | `data_wire` | reads at the back, writes at the front (FACING) | writes the relayed value into the data block in front |
| Color block | `color_block` | no ports, no redstone or data logic | stores 24-bit RGB, tinted on the client |
| Hover block | `hover_block` | no ports, no redstone or data logic | a full block (holds redstone dust) with an empty collision shape (players walk through) and a transparent middle plus a white frame |
| Hidden sign | `hidden_sign` | no ports, no redstone or data logic | the block itself is fully transparent, the text is drawn by a client-side BER |

Conventions and details:

- **FACING points at the output face** (the output leaves along FACING). A vanilla repeater also outputs along FACING, but it uses
  `FACING = getHorizontalPlayerFacing().getOpposite()`, which means the output faces **the player**;
  this mod uses `FACING = getHorizontalPlayerFacing()`, so the **output faces the direction the player is looking** -
  that is the origin of "the opposite feel compared with a repeater" (vanilla semantics: `AbstractRedstoneGateBlock#getPlacementState`).
- **Input A = the back** (the opposite side of the output face), **input B = the two sides**; when both sides carry a signal, **left wins**, matching the limiter's requirement.
- A gate first applies ADC to its inputs (`>0` means high) and then runs the boolean operation; the output is passed through DAC to 0 or 15.
- The limiter and the scanner work on **levels** in 0..15: inside a pure wire circuit the limiter behaves like an AND gate, and next to vanilla dust it behaves as "take the smaller one".
  Both also implement the vanilla comparator output, so analog values can be read by a comparator.
- Scanner rule: 15 blocks each to the left, the right and up, **looking for Constant blocks first**:
  if a ray hits a Constant, its value is output directly (up > left > right, nearest first within one direction), clamped into 0..15;
  only when there is no Constant does it count **non-air blocks** (the up count wins if the up ray saw anything, otherwise the larger of left and right).

  Note that "non-air" is literal: redstone dust, wires, torches, water, leaves and blocks **behind** a wall are all counted,
  and the ray sits at the scanner's own height. When "there is clearly no block but there is an output", check this log line:

  ```
  [circuit-redstone_logic] scanner at x,y,z: left=<left count> right=<right count> up=<up count> -> power=<output>
  [circuit-redstone_logic] scanner at x,y,z: constant value <value> -> power=<output>
  ```

  (Printed once per level change.)

- Data blocks (`constant` / `string_value` / `variable`) **emit no redstone themselves** and do not connect to wires;
  they are writable containers only: `/circuit-set <value>` writes them and the scanner reads Constant values.
  Constant values live in the block entity NBT (the world save keeps them), the same goes for strings.
- **The variable block** (`variable`) is "one variable per block":
  - The type (`unset` / `null` / `int` / `str`) lives in the BlockState property `KIND` (`EnumProperty<VariableKind>`;
    1.20.4 has no string properties, so the enum implements `StringIdentifiable` and the property value is `asString()`);
    the client switches between four models accordingly (`variable_unset` / `variable_null` / `variable_int` / `variable_str`).
  - The value (`number` / `text`) lives in the block entity. **The type and the value must be changed together**, and the only
    entry point is `VariableBlock.apply(world, pos, state, value)`: write NBT + `setBlockState(pos, state.with(KIND, kind), NOTIFY_LISTENERS)`.
  - The write syntax `null` / `int=42` / `str="hi"` is parsed by `logic/VariableAssignment` (pure logic, no MC dependency, so
    `tools/LogicCheck` can test it directly); `null` is the only form that takes no value, and the command layer only translates
    `Failure.error()` into a chat message.
  - **`null` means "has a value, and it is empty"**: `DataConversion.Kind` gained a third member `NULL`, and `Value#asConstant()`
    returns 0 while `Value#asText()` returns an empty string, so `DataConversion.convert` implements
    "null -> constant = 0, null -> string = empty" without special cases. `UNSET` (never set) is "no source" inside
    `DataWireBlock#readSource` and writes nothing - the two behaviours must stay different, otherwise "cleared" and "not connected"
    cannot be told apart.
  - **The data wire is bidirectional**: `DataWireBlock#readSource` treats a variable as a source (`unset` means "no source",
    `null` means an empty value), and `writeVariable` converts according to the variable's current type
    (`logic/VariableWrite`: `unset` / `null` follow the source type); a type conflict (text `"abc"` into an int variable) sets
    `error=true` and switches to the red error texture.
  - **The value survives being broken**: the loot table `variable.json` uses `minecraft:copy_nbt` (`source: block_entity`,
    copying `kind` / `number` / `text` into `BlockEntityTag.*`). On placement, `VariableBlockItem#postPlacement`
    synchronises the BlockState `KIND` once more after `BlockItem` has written the NBT (otherwise the texture stays at `unset`).
    In creative mode, middle-click picking is handled by `VariableBlock#getPickStack`, which carries the NBT.
  - The item tooltip has one extra "stored value" line (`tooltip.circuit-redstone_logic.variable.current`, read from `BlockEntityTag`).
  - It takes **no part** in the scanner or comparator: those two only recognise Constant blocks.
- **The data wire** (`data_wire`) moves data from one data block to another:
  - The arrow is drawn on the **east and west faces** (it reads upright from the side and is not squashed by the top face);
    the default (`facing=north`) points north, and once the model is rotated by `facing` the arrow points along the data flow;
    the **back** (the opposite of `facing`) reads the source and the **front** writes the target.
  - Conversion rules: Constant -> Constant/String (strings use decimal text); String -> String;
    String -> Constant requires the content to **be an integer** (surrounding whitespace and a sign are allowed).
  - When the string is not an integer but has to be written into a Constant, that data wire enters `error=true` and switches to the red
    error texture; changing the string back to an integer restores it automatically.
  - **Chaining is supported**: consecutive data wires relay the source value down the chain (up to 64, giving up on a cycle), and only
    the wire whose **front touches a data block** performs the write.
  - The driver is a **scheduled tick** once per tick (not a block entity, and it needs no redstone signal); scheduled ticks are saved with the world.
- **The color block** (`color_block`) is placed as black `#000000` and recoloured with `/circuit-set #RRGGBB`;
  the colour lives in the block entity and the client tints a white model through `ColorProviderRegistry` (client entry point
  `CircuitRedstoneClient`). Because the colour is not part of the BlockState, a change first syncs the block entity data and then swaps to a
  `shade` variant one tick later to force the client to rebuild the mesh, which is what makes the colour show up.

- **The hidden sign** (`hidden_sign`):
  - The block itself uses an **empty model** (`hidden_sign.json` has only `textures.particle`, neither `parent` nor `elements`):
    it bakes to no faces at all, so the block is never rendered. Do **not** fake invisibility with "a fully transparent texture + cutout":
    `render_type` is a 1.20.5+ field, 1.20.4 ignores it, and once the block falls into the solid layer its transparent pixels are drawn as
    a **black block**; the item icon uses `textures/item/hidden_sign.png` instead (otherwise the inventory shows nothing).
  - The text lives in `HiddenSignBlockEntity` (NBT `text`) and is written through `HiddenSignBlock#setText`:
    `markDirty` + `HiddenSignBlockEntity#syncToClients` (one `BlockEntityUpdateS2CPacket` per watching player).
  - **Block entity data must be synced explicitly**: the text is not part of the BlockState, so `world.updateListeners(...)` inside
    `setText` does nothing - it only refreshes the block state and the client block entity stays empty. Both
    `toUpdatePacket()` (the text reaches the client right after the command) and `toInitialChunkDataNbt()` (chunk load / rejoining the world
    carries the text) are required; missing either one looks like "the command says it wrote, the sign still shows the placeholder".
    Without `toUpdatePacket` even rejoining the world does not help, because the initial chunk data is empty too.
  - Rendering happens in the client-side `HiddenSignRenderer` (a `BlockEntityRenderer`): it uses `TextRenderer` to draw on both faces of the
    block, wrapping by displayed width (at most 4 lines), white text; when the text is empty it draws the italic grey
    `block.circuit-redstone_logic.hidden_sign.placeholder` ("Type something...").
  - The offset along the normal must be **>= 0.5** (`SURFACE_OFFSET = 0.501`): this block occupies a full cube, so its half width is 0.5, and the
    vanilla sign value 0.4375 would bury the text inside the block behind its face (which looks like "a block with no text").
  - The constructor logs `hidden sign renderer created`: when debugging "no text", check that line first to confirm the BER really exists.
  - The `/circuit-set` branch treats `""` as "clear" (otherwise greedyString would keep the quotes as part of the text).
  - It takes no part in redstone or data logic, and it is not a `CircuitPart` (so it never shows up in `info full-circuit` or in a chart).

- **The hover block** (`hover_block`) is a "looks hollow, functions as a full block" spacer; implementation notes:
  - **Support is decided by `getSidesShape`** (returning a full cube): the `canPlaceAt` of redstone dust goes through
    `isSideSolidFullSquare(UP)` -> `SideShapeType.FULL.matches` -> `BlockState#getSidesShape`
    -> **`Block#getSidesShape` (overridable)**, independently of the collision shape. The first version only provided a full outline and
    left the collision box empty, so redstone dust decided it had no support and dropped - `getSidesShape` is the correct hook.
  - **The collision shape** is empty (`getCollisionShape` -> `VoxelShapes.empty()`): players and dropped items pass straight through;
  - **The culling shape** is empty (`getCullingShape` -> empty): it must not cull the faces of neighbouring blocks,
    otherwise the culled back faces show through the translucent parts and look like holes;
  - **It cannot be `nonOpaque()`**: the opaque property is one of the prerequisites for supporting blocks, and the price is that it blocks
    light - that limitation is documented in the README's "known limits";
  - **The texture** is "transparent middle + 4 px white frame" (`New-FrameTexture`), with alpha thresholded to 1 bit;
    **the client registers the cutout render layer** (`BlockRenderLayerMap.INSTANCE.putBlock(HOVER_BLOCK, RenderLayer.getCutout())`), and the
    model also carries `"render_type": "minecraft:cutout"` as a second safety net.
  - **The model must have a `parent`** (`minecraft:block/cube` here): a model with only `textures`, no `parent` and no `elements`
    bakes into an **empty model** - the block is not rendered at all and the player only sees their own selection box. That was the real
    reason for "the hover block is invisible".

- The amplifier does `DAC(ADC(input))`: any input strength at all becomes the full strength 15.
  Its purpose is to replace a vanilla repeater's "boost the signal" job **without paying the delay** - when a dust line has decayed to 8,
  the amplifier outputs 15 immediately while a repeater costs 1..4 redstone ticks.
- The brightness sensor reads the light level of **its own position** (the larger of sky light and block light, adjusted for the current
  time and weather) and outputs a signal of the same strength. A light change does not trigger neighbour updates, so it polls every 10 ticks;
  it still recomputes immediately when a neighbouring block changes.

### Circuit terminal and `/circuit-terminal`

- The **circuit terminal** (`circuit-redstone_logic:terminal`) is a plain item, crafted in a 3x3 grid:
  ```
  redstone  wire  redstone
    wire  crafting table  wire
  redstone  wire  redstone
  ```
- Commands (**no operator permission needed**; the requirement is "a circuit terminal in your inventory"):
  - `/circuit-terminal list` - lists every block and item of the mod as `name: id` (the name is translatable text, localised on the client).
  - `/circuit-terminal get <id>` - takes **4 wires** and gives `ExchangeRule.perBatch(path)` copies of
    `circuit-redstone_logic:<id>`; `id` needs no namespace prefix (writing `circuit-redstone_logic:xxx` is also accepted, but only this
    mod's ids are valid).
- The price and amount rules live in `logic/ExchangeRule` (pure logic, unit-testable):
  - one batch costs `WIRE_COST = 4` wires;
  - `perBatch(path)`: ordinary blocks use `DEFAULT_AMOUNT = 1`, and `BULK_PATHS = {hover_block, color_block}`
    use `BULK_AMOUNT = 16` per batch;
  - `isValidAmount(path, n)`: `n >= perBatch` and `n % perBatch == 0` (whole batches only);
  - `costFor(path, n)`: `WIRE_COST * (n / perBatch)`, returning `-1` for an invalid amount.
  - `/circuit-terminal get <id> [n]`: without `n` you buy one batch (the old behaviour); with `n` it must be whole batches and the
    price is the batch count (`get and_gate 3` = 12 wires, `get hover_block 32` = 8 wires).
  To make another block buyable in bulk, add its id to `BULK_PATHS`; the command layer does not change.
- The inventory check (`fitsAfterExchange` -> `simulateInsert`) simulates the **real amount**, filling matching stacks first and then empty
  slots, so a batch of 16 cannot hit "only 1 was checked -> it does not fit but the wires are already gone"; the wires are taken after the check.

### Data blocks and `/circuit-set`

- A **Constant** (`constant`) stores one integer, a **String value** (`string_value`) stores one piece of text, and a
  **Variable** (`variable`) stores one int or str per block (the type can change and data wires can write it).
  None of them glows, powers anything or connects to wires; they are writable containers only (the data lives in block entity NBT).
- Writing (no operator permission needed):
  - Aim at the block: `/circuit-set 7` (parsed as an integer when the target is a Constant), `/circuit-set hello world` (written verbatim when the target is a String, up to 256 characters), `/circuit-set #12AB34` (a hex colour when the target is a color block; `#RRGGBB` / `RRGGBB` / `0xRRGGBB` all work).
  - **Variables need the type prefix**: `/circuit-set null` (takes no value), `/circuit-set int=42` or
    `/circuit-set str="hi"` (a `str` value must be wrapped in a pair of double quotes; `int` does not accept quotes).
    A missing equals sign / an unknown type name / a value that is not an integer / text without quotes / a value after `null` each have
    their own error message (`set.variable_need_equals` / `bad_kind` / `bad_number` / `need_quotes` / `null_takes_no_value`).
  - Coordinate form: `/circuit-set <x> <y> <z> <value>` (also usable from the console and command blocks; the server-side self-test uses it).
  - A Constant given a non-integer reports "not an integer"; a color block given an invalid colour reports "not a hex colour"; a target that is
    not a data block reports "not a Constant, String or Variable".
- Reading: `/circuit-read` (aim at a Constant / String / Variable) or `/circuit-read <x> <y> <z>`,
  with the fixed output format `<block name> (<x, y, z>): <value>` (strings and variable text are wrapped in quotes, for example
  `String (32, 100, 1): "hello world"`; an unset variable prints `(not set)`).
- The only consumer of a Constant is the **scanner**: when the left/right/up rays hit a Constant, the scanner outputs the Constant's value
  instead of a block count (up > left > right, nearest first within a direction; values outside 0..15 are clamped). Strings take part in no
  redstone logic at all.
- A value change notifies the neighbours explicitly, so the scanner recomputes immediately instead of waiting for its 10-tick rescan.

### Recipes at a glance

**Only the 3 crafting recipes below exist**; every other block goes through `/circuit-terminal get <id>` (4 wires each).
The generator keeps a whitelist, so extra recipes are deleted and the validator reports them.

| Recipe | Type | Result |
| --- | --- | --- |
| redstone | shapeless | 1 wire |
| wire | shapeless | 1 redstone |
| 3x3 grid (above) | shaped | 1 circuit terminal |

### Delay and throughput (the "redstone tick limit")

- **Delay**: wires / gates / limiters / scanners / amplifiers all "settle inside the same game tick", which means there is
  **no repeater-style 1..4 redstone tick delay**; a long chain propagates within one tick regardless of its length
  (the scheduler is a work queue plus generation-based invalidation, so chain length only matters for the per-tick budget).
- **Throughput**: the solver processes at most `CircuitUpdates.STEP_BUDGET = 16384` positions per world per tick;
  at 20 ticks/s that is a theoretical ceiling of roughly **320k evaluations per second**, far above vanilla's tens per second.
- **Feedback loops**: one position can advance at most
  `CircuitUpdates.MAX_EVALUATIONS_PER_POSITION = 32` rounds inside a tick (adjustable). That is the knob that "lifts the redstone tick
  throttle": oscillators and feedback circuits are no longer pinned to "one round per tick"; positions that exceed the limit are deferred to
  the next tick, so a livelock cannot stall the server.
- **The hard limit nothing can break**: any **vanilla block** (dust, observer, piston, lamp, comparator, ...) is sampled at 20 Hz only, which
  the Minecraft main loop dictates and a mod cannot break without rewriting the tick loop. So "faster than a redstone tick" only happens
  inside this mod's own network; as soon as the signal is handed to a vanilla block, the 20 Hz world is back.

### Wires and vanilla redstone dust

A wire reads only **the single dust cell touching it**, and decides high/low with the protocol `ADC(level > 0)` - so the **per-block decay of
vanilla dust is respected**: where a dust line has decayed to 0, a wire attached there is low.

The hard part is that this dust line may be lit by our own wire (a wire feeds 15 into adjacent dust). To lose neither the decay nor get a
self-latch, the signal source check recomputes that dust cell with the vanilla rule in `DustPower`, while treating **the whole wire network
being evaluated as non-existent**:

```
power(dust) = max( weak power from non-dust neighbours, power of adjacent dust - 1 )
```

That yields "the strength this dust would have if my wire were not there":

- dust decayed to 0 at the far end -> independent strength 0 -> the wire is low (**fix: no longer misjudged as high**);
- dust with a real source behind it (lever / torch / redstone block / repeater ...) -> independent strength > 0 -> the wire is high;
- a wire lighting dust never lights itself back up -> no self-latch.
- A wire network only accepts **weak power** (it does not take the strong-power amplification of a solid block), otherwise there would be a loop:
  "the wire lights dust -> the dust strongly powers the block below -> the block powers the wire again".
- A network touching dust schedules a periodic **block scheduled tick** for itself
  (`WireNetwork.DUST_RESCAN_INTERVAL_TICKS`, 10 ticks by default) to recompute at low frequency:
  dust only notifies neighbours when its own strength changes, while "source changes around the dust" (and changes masked by our own power)
  never notify the wire. Scheduled ticks are saved with the chunk and survive a world reload; a pure wire network schedules nothing, so it
  costs nothing extra.

## 3. Update model (anti-recursion)

- Every `neighborUpdate` / `onBlockAdded` / `onStateReplaced` does one thing only: **put the position into the dirty queue**; no callback computes or writes a block.
- The queue is consumed **iteratively** by `CircuitUpdates.flush` in `ServerTickEvents.END_WORLD_TICK` (zero recursion); positions enqueued during the
  computation are appended to the same queue, so a long chain propagates within one tick.
- State writes always use `Block.NOTIFY_LISTENERS` (no vanilla neighbour cascade), followed by explicit `updateNeighborsAlways` + `updateComparators`
  on the changed position as a targeted notification.
- Three safeguards: a per-tick step cap, a per-tick per-position evaluation cap (which downgrades a feedback loop into a 1-tick oscillator), and a
  re-entrancy lock on flush.
- Wires are evaluated **as a whole network**: the network level is "does any external source exist on this network", which avoids "sourceless wire
  loops powering each other and latching".
- The scanner is driven by a `BlockEntityTicker` every 10 ticks and never scans 15 blocks per tick; an adjacent block change recomputes it at once through a dirty flag.
- A wire network coupled to vanilla dust additionally schedules the periodic block tick described in the previous section, to cover the corner cases
  where "a wire gets no notification".
- Breaking a block must go through `super.onStateReplaced`: vanilla removes the block entity in `AbstractBlock#onStateReplaced`, and skipping that
  leaves orphan block entities (the log spams `invalid for ticking` and the save keeps growing). Orphans in old saves are cleaned up by
  `OrphanBlockEntityCleanup` on chunk load (it only touches this mod's own block entity types).

## 3.5 Low-level debugging: `/circuit-debug`

The architecture is "**one sandbox engine + several execution strategies**":

- `sim/CircuitSimulator`: the only simulation engine. It takes a snapshot, runs the sandbox tick by tick on a background thread, and reports every
  state change back to the strategy.
- `sim/SimulationSandbox`: the snapshot, which is also a `CircuitContext` (a world view).
- `logic/CircuitContext` + `logic/WireRule`: **the real world and the sandbox share the same evaluation logic**
  (wire powering rules, gate port reads, scanner counts and sensor light are not implemented twice).
- `sim/SimulationManager`: the thread pool (2 daemon threads), simulation ids, cancellation and cleanup on server shutdown.

Subcommands:

| Command | Effect |
| --- | --- |
| `/circuit-debug info full-circuit` | Reads **every circuit block** (wire + gates/limiter/amplifier/scanner/sensors + Constant/String) of **all loaded chunks** in the **real world** (not the sandbox) and prints one line per block as `<block name> (<x,y,z>) [<SNBT>]`; no aiming needed and it does not have to be a wire |
| `/circuit-debug test start <x> <y> <z>` | Snapshots the wire network around that position, injects one tick of high level, reports every block change (old/new SNBT) and returns a simulation id; the start point may be a wire or any circuit block (its six neighbours are used as the network seed) |
| `/circuit-debug test dead-loop <x> <y> <z>` | Same, but uses a **global configuration hash** for repeat detection (it keeps only a 64-bit hash instead of piling up SNBT strings); a hit throws `InfiniteLoopException` and reports the period in red in chat |
| `/circuit-debug test stop` | Cancels all simulations of that source (player or console) |
| `/circuit-debug test stop <simulation id>` | Cancels one simulation |
| `/circuit-debug run-chart <A> <B> <ticks>` | Observes the A..B rectangle in the real world, records every block state change over C ticks and renders a PNG chart |
| `/circuit-debug run-video <A> <B> <ticks>` | The same recording rendered as an animated GIF plus (when ffmpeg exists) an MP4 of the same name (one frame per tick, top view, at most 600 ticks) |

### Charts and video (`run-chart` / `run-video`) implementation

- `debug/ChartSession`: scans the rectangle tick by tick inside `END_WORLD_TICK` (limits: 16384 blocks / 2400 ticks; video is limited to 600
  ticks), detects changes by `BlockState` identity (state instances are interned in the registry) and keeps the initial state plus the change
  sequence per position. Both subcommands share the recording and persistence logic; only the renderer differs (`Mode`).
- `debug/ChartRenderer`: draws a "timeline heatmap" - one row per position that changed (the row header carries the block name and coordinates),
  one column per tick; the cell holds the level value (when it is wide enough) and the bottom carries the legend plus a 0..15 gradient bar;
  with many ticks each tick is compressed to 1 px. **Pure AWT `BufferedImage` + `ImageIO`**, so it renders without a window and works on a
  dedicated server.
- `debug/VideoRenderer`: renders the same recording as an **animated GIF** - each frame is a top view of the region (highest non-air block per
  column), one frame per tick (20 frames/s, GIF delay=5); it encodes with the JDK's own `GIF ImageWriter#writeToSequence` and writes a
  `GraphicControlExtension(delayTime)` plus the NETSCAPE loop marker. **No ffmpeg dependency.**
  Rendering and export share one timeline through `forEachFrame` (ticks 0..N, one frame each), so GIF and MP4 frame order match.
- `debug/VideoEncoder`: encodes an MP4 on top of the GIF. It probes for `ffmpeg` (PATH -> `%LOCALAPPDATA%\Microsoft\WinGet\Links`
  -> `%ProgramData%\chocolatey\bin`; only the "found" result is cached, a miss is re-probed every time, so installing it needs no game restart).
  Encoding: `VideoRenderer.exportFrames` writes each frame as `frame-%04d.png` (temp directory, deleted recursively afterwards) ->
  `ffmpeg -framerate 20 -start_number 0 -i frame-%04d.png -vf pad=ceil(iw/2)*2:ceil(ih/2)*2
  -c:v libx264 -pix_fmt yuv420p` (yuv420p needs even dimensions, hence the pad); on failure it falls back to `-c:v mpeg4`;
  timeout 120 s. **The GIF is always written first**, an MP4 failure only loses that one file and never fails the whole recording.
- `debug/ChartSaver`: always writes `<game dir>/circuit-charts/` first (PNG/GIF plus a same-named `.txt` list of changes);
  in a client it opens the **in-game save-as screen** (`client/ChartSaveScreen`, both folder and file name editable);
  the AWT `FileDialog` (system dialog) is only triggered by the "system dialog..." button and only when not headless.
  The world keeps ticking while the screen is open.
- The smoke test really runs `run-chart` once and asserts that `ticks=... blocks=...` / `Chart saved:` appear in the log, and that a non-empty
  PNG was written to disk (`CHART_IMAGE_WRITTEN`); pass 3 runs `run-video` and checks the GIF magic (`CHART_VIDEO_WRITTEN`) and the MP4 `ftyp`
  box (`CHART_MP4_WRITTEN`, recorded as SKIP when the machine has no ffmpeg).

Every subcommand can be executed by the console or a command block (output goes through `CommandSource#sendFeedback`), which keeps server-side
self-tests possible.

Safety and limits (all hard-coded constants):

- **Threads**: the snapshot is collected read-only on the main thread (`World` cannot be accessed across threads) and the simulation runs entirely
  on background threads; the main thread only sends packets through `server.execute(...)` and never blocks waiting.
- **Scale**: one snapshot is <= 4096 nodes; a simulation is <= 10000 ticks and <= 5 s wall clock; at most 4 simulation jobs at once.
- **Output**: `info` dumps everything at once, **one chat message per block** (vanilla chat does not treat `\n` as a newline, so stuffing multiple
  lines into one message just displays as a single line on screen), with no header, statistics or truncation line; note that chat scrollback keeps
  only the last 100 messages, so the earliest blocks scroll out of history when there are many (they are all still sent). `test` reports at most
  100 changes per tick.
- **Exceptions**: any exception during a simulation is caught -> the job ends safely and is released, so it cannot hang the server.

## 4. Placeholder rendering

`tools/GenerateAssets.ps1` generates all placeholder assets (textures are drawn with the **ImageMagick** `magick` command, which requires
ImageMagick 7 on the machine; the script always writes RGBA PNG with `-depth 8 -colorspace sRGB`, because Minecraft only accepts 8-bit textures):

- 16x16 solid textures (1 px outline plus a type-coloured block);
- the data wire texture additionally draws the direction arrow with `polygon` (normal = mint, conversion failure = light on red);
- the wire uses `multipart`: a centre block plus six thin arms, switching to a lit texture when powered;
- circuit blocks use `multipart`, rotating by `facing` only, with the `front` face as the output face;
- the data wire uses a `facing x error` `multipart`, so the arrow rotates with the direction and the whole block switches to the error texture on failure;
- the color block model carries `tintindex: 0` and the client tints it through `ColorProviderRegistry` using the block entity colour;
- the powered state is a separate `_on` texture (**no light emission**, so brightness sensor readings are not polluted);
- item models inherit the block model directly.

## 5. Build

The repo ships an offline dependency cache in `.gradle-home/` (not version controlled); point `GRADLE_USER_HOME` at it and no network is needed:

```powershell
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) '.gradle-home')
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'   # JDK that runs Gradle/Loom; the artifact still compiles for Java 17
.\gradlew.bat build
```

Artifact: `build/libs/circuit-redstone_logic-1.28.7.jar`

Note: the project pins `loom_version=1.19-SNAPSHOT`, which resolves to Loom 1.19.4 and requires a build JVM >= 25, so `JAVA_HOME` must be 25;
the mod itself is still compiled with `options.release = 17` and only needs Java 17+ at runtime.

The project enables an `access widener` (`src/main/resources/circuit_redstone_logic.accesswidener`, `loom { accessWidenerPath }` in
`build.gradle`, and the `accessWidener` field in `fabric.mod.json` - three places that must agree): it only widens
`ThreadedAnvilChunkStorage#entryIterator`, so `/circuit-debug info full-circuit` can walk "all loaded chunks of the current world".

Run a dev environment (it recompiles automatically):

```powershell
.\gradlew.bat runServer      # dedicated server
.\gradlew.bat runClient      # client (downloads the 1.20.4 assets first)
```

## 6. Self-test

Pure logic self-test (no game needed):

```powershell
javac -encoding UTF-8 -cp build/classes/java/main -d build/tmp/logiccheck tools/LogicCheck.java
java -cp "build/classes/java/main;build/tmp/logiccheck" LogicCheck
```

Static asset validation (JSON validity plus complete model/texture/lang/recipe references):

```powershell
pwsh -File tools/ValidateAssets.ps1
```

Runtime smoke test (starts a dedicated server 3 times, places blocks in a real world and asserts states with `/execute if block`):

```powershell
pwsh -File tools/SmokeTest.ps1
```

Coverage: wire on/off, sourceless wire loops not latching, wire <-> vanilla dust conversions in both directions,
AND/NOT/XOR truth tables, the limiter, the scanner's immediate response and periodic rescan,
Constant/String writes (the coordinate form of `/circuit-set`), the scanner reading a Constant (including the 20 -> 15 clamp) and "a String is not a Constant",
data wire transfers (Constant -> Constant, Constant -> String, String -> Constant, chaining, the error texture on a failed conversion and its recovery),
the output format of `/circuit-read` and its "not a data block" error,
color block recolouring (`/circuit-set #RRGGBB`),
the variable block (`null` / `int=` / `str="..."` assignment, type textures, the four syntax error messages, bidirectional data wire reads and writes,
`unset` not overwriting the target, `null` moving as 0 / `""`, the red type-conflict texture, the drop carrying `BlockEntityTag`),
the hover block (holding redstone dust, contrasted with "dust in the air is removed", dropping itself; the 16-per-batch amount is covered by `LogicCheck`),
and the output contract of `/circuit-debug info full-circuit`.

## 7. Manual checklist

1. Place wires: wires must connect to each other and to vanilla dust automatically, drawing the connection arms.
2. Vanilla dust at level 1 or above -> the wire lights up; a powered wire -> the adjacent vanilla dust becomes 15.
3. Gates: A on the back, B on the left (then try the right), verify AND/OR/NOT/XOR against the truth table.
4. Limiter: A=15, B=8 (levels set with dust) outputs 8; when both sides carry a signal, confirm left wins.
5. Scanner: place different numbers of blocks left/right/up and confirm "up wins, otherwise the larger of left and right"; confirm the update interval is about 10 ticks.
6. Constant/String: place a Constant -> aim at it and `/circuit-set 9` -> a scanner ray hitting it should output 9;
   swap in a String block, run `/circuit-set hello world`, and confirm the scanner still counts blocks (a String is not a Constant).
7. Data wire: Constant 5 -> data wire (arrow pointing at the String) -> String, confirm the String becomes "5";
   change the String to a positive integer feeding a Constant and confirm the Constant follows; change the String to `abc` and confirm the data wire shows the red error texture;
   put two data wires end to end and confirm the value still gets through.
8. Color block: placed black, aim at it and `/circuit-set #12AB34`, confirm the block turns that colour within 1 tick;
   then `/circuit-set #GG0011` and confirm it reports "not a hex colour".
9. Variable block (textures and placement restore can only be confirmed by hand on the client):
   - placed as **grey** (unset); `/circuit-set int=42` -> **orange**; `/circuit-set str="hi"` -> **blue**;
   - **break** it, pick the item up, hover the item and the line `Stored: 42` should appear;
   - **place it again** and the texture is orange immediately while `/circuit-read` returns 42 (this verifies the state sync in `postPlacement`);
   - middle-click pick (creative) the block, place it, and the value is still kept.
10. Hover block:
    - after placing it you should see **a white frame** (you can see through the middle, it is not a black block -> verifies the cutout render layer; only a selection box = empty model);
    - walking into it should **pass straight through** (no collision);
    - **placing redstone dust on top** should work normally (contrast: dust placed in the air drops immediately);
    - breaking it drops itself; `/circuit-terminal get hover_block` gives **16**.
11. Stress test: build a long wire loop with no source and confirm it goes dark normally; build a NOT gate feeding itself and confirm it only
    oscillates while the server stays responsive.

## 8. Known limits

- Vanilla dust never draws a connection arm toward this mod's wires (a vanilla rendering limitation), but they are fully connected electrically; the arm on the wire side is drawn by this mod.
- 1.20.4 has no `Tickable` interface; the equivalent is `BlockEntityTicker` (provided by `BlockEntityProvider#getTicker`), which the scanner uses.
- One wire network is capped at 4096 positions; beyond that it is truncated and a warning is logged.
- **Changing the mod id wipes the blocks of the old id in old saves** (vanilla treats unknown blocks as air and logs `Skipping BlockEntity with id <old id>:*`).
  Back up `run/saves/<world>` before changing the id; to keep old circuits you have to rewrite the namespace in the region palette and in the
  block entities **before opening the world** (this repo ships no migration tool for that yet).
  This mod was renamed from `circuit-redstone` to `circuit-redstone_logic`, and circuits built with the old id in old saves are not migrated automatically.
