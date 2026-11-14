# Circuit Redstone - Logic

**English** | [简体中文](README.md)

[![Minecraft 1.20.4](https://img.shields.io/badge/Minecraft-1.20.4-blue)](#install-and-quick-start)
[![Fabric](https://img.shields.io/badge/loader-Fabric-orange)](#install-and-quick-start)
[![License MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

## What is this

Turns redstone from an analog strength into binary logic: wires carry only 0/1 and never decay,
gates settle within the same game tick, and constants, strings, variables and data wires let a
circuit read, move and write values.

- Environment: client and server (Fabric, Minecraft 1.20.4)
- Requires: Fabric Loader ≥ 0.20.1, Fabric API, Java 17+
- Contents: 18 blocks, 3 crafting recipes, 4 command groups, all with English and Chinese tooltips
- Version 1.28.7, MIT licensed
- Developed by DeepSeek-v4.1-Flash and DeepSeekHarness

What it does not do: it does not replace vanilla redstone dust (the two interoperate), it provides no
maths beyond analog levels, and it does not support game versions other than 1.20.4.

## Install and quick start

1. Install Fabric Loader and Fabric API for Minecraft 1.20.4.
2. Drop `circuit-redstone_logic-1.28.7.jar` into `mods/`.
3. Launch the game: a Circuit Redstone - Logic tab appears in the creative inventory. Servers need the
   same jar.

Smallest working circuit, an inverter:

```text
[redstone block] ── wire ──→ [NOT gate] ──→ wire ──→ [lamp]
```

- The colored face of a gate is its output face, and it points where you look when placing.
- The face behind the gate is input A; the two sides are input B.
- While the redstone block powers the wire, the NOT gate outputs 0 and the lamp is off. Swap the block
  for a lever and toggle it.

Wires and vanilla dust convert both ways without loss: dust and wire are 1:1 shapeless recipes.

## Blocks at a glance

Signal blocks.

| Block | id | What it does | Ports |
| --- | --- | --- | --- |
| Wire | `wire` | carries 0/1, no decay, six-way | any face; outputs 15 |
| AND gate | `and_gate` | `A AND B` | A = back, B = left/right |
| OR gate | `or_gate` | `A OR B` | A = back, B = left/right |
| NOT gate | `not_gate` | `NOT A` | A = back |
| XOR gate | `xor_gate` | `A XOR B` | A = back, B = left/right |
| Signal limiter | `limiter` | `min(A, B)` on levels 0..15 | A = back, B = left/right |
| Signal amplifier | `amplifier` | any input above 0 → 15 | A = back |

Analog and sensing blocks.

| Block | id | What it does | Input / output |
| --- | --- | --- | --- |
| Signal scanner | `scanner` | outputs a level 0..15 | scans 15 blocks up, left, right |
| Brightness sensor | `brightness_sensor` | outputs light level 0..15 | no input |
| Comparator | `comparator` | compares two constants, one bit out | back=A, right=B, left level=mode |
| Clock | `clock` | pulses every N ticks | `/circuit-set <ticks>` sets N |

Data blocks.

| Block | id | What it does | How to write |
| --- | --- | --- | --- |
| Constant | `constant` | stores one integer | `/circuit-set 7` |
| String | `string_value` | stores up to 256 characters | `/circuit-set hello` |
| Variable | `variable` | one int / str / null per block | `/circuit-set int=42` |
| Data wire | `data_wire` | copies the value from its back to the block in front | arrow = flow direction |

Decoration and helpers.

| Block | id | What it does | How to get |
| --- | --- | --- | --- |
| Hover block | `hover_block` | full block that holds dust, you walk through | terminal: 4 wires → 16 |
| Solid color block | `color_block` | stores one RGB color | terminal: 4 wires → 16 |
| Hidden sign | `hidden_sign` | the block is invisible, only text is drawn | terminal: 4 wires → 1 |

## Wiring rules

Gate ports, the part everyone gets backwards:

```text
                left (B)
                   │
   back (A) ─── [ gate ] ───→ output face
                   │
                right (B)
```

- The colored face is the output face and points where you look when placing.
- The face behind it is input A; the sides are input B, and the left side wins when both are powered.
- A NOT gate has only A. Inputs can be driven by redstone blocks, levers, dust, wires or another
  gate's output face.
- Left and right are relative to the gate: stand on the input side and look at the colored face, and
  your left hand is B's left.

Gates read every input as "redstone level above 0 is HIGH", then run the boolean operation. Output is
always 0 or 15, so dust at strength 3 feeding an AND gate counts as HIGH, not as 3.

Conversion rules between dust and wires:

| Direction | Rule |
| --- | --- |
| redstone → wire | level 0 is LOW, above 0 is HIGH |
| wire → redstone | HIGH emits 15 |
| wire to wire | six-way, no decay |
| wire to vanilla dust | adjacent is connected, both ways |

Two consequences: a wire reads only the single dust block next to it, so dust decayed to 0 makes the
wire LOW; and a wire emits weak power only, so it never leaks through solid blocks.

Limiter, scanner, amplifier, brightness sensor and comparator all work on 0..15 levels and all expose
their current level, so a vanilla comparator reads them too. Constants, strings and variables emit no
redstone and are read as data only.

On speed: wire networks, gates, limiters and amplifiers settle within the same tick, so a long chain
does not add one redstone tick per block. A per-tick evaluation budget exists, so extremely long or
heavily oscillating circuits may continue on the next tick. Interop with vanilla blocks runs at
vanilla 20 Hz.

## Block reference

### Wire `wire`

Six-way connection, no decay, weak power only. When it meets vanilla dust the wire draws a connecting
arm toward the dust, but vanilla dust never draws an arm toward the wire — it looks disconnected and
works fine. A single network is capped at 4096 positions; beyond that it is truncated with a log
warning.

### Gates

| Block | Truth table |
| --- | --- |
| AND gate | outputs 15 only when both inputs are HIGH |
| OR gate | outputs 15 when either input is HIGH |
| NOT gate | outputs 0 when A is HIGH, 15 when A is LOW |
| XOR gate | outputs 15 when the inputs differ |

XOR for the sum bit and AND for the carry gives a half adder.

### Signal limiter `limiter`

Outputs `min(A, B)`, with A on the back and B on the sides (left wins). It works on levels, not bits:
A = 12 and B = 5 outputs 5. Typical use: clamp a 0..15 value to an upper bound.

### Signal amplifier `amplifier`

Any input above 0 on the back outputs 15, otherwise 0. It re-shapes and isolates: any strength becomes
full strength and the output side cannot feed back into the input. A zero-delay repeater replacement.

### Signal scanner `scanner`

Scans 15 blocks up, left and right; left and right are relative to the block's facing.

1. A constant block on a ray wins: the scanner outputs that constant, with up above left and left
   above right, nearest first on a given ray. Values outside 0..15 are clamped.
2. With no constant anywhere, it counts non-air blocks: the up count is used when the up ray sees
   anything, otherwise the larger of the left and right counts.

Water, leaves, dust, wires, torches and blocks behind walls all count, so check the rays when a number
looks wrong. It rescans every 10 ticks and reacts immediately to an adjacent block change.

```text
[constant = 7]              ← a constant on the ray
     ↑
[scanner] ──→ wire          (outputs exactly 7)
```

### Brightness sensor `brightness_sensor`

Outputs the brightest light level around it (itself plus six neighbours) from 0 to 15. The sensor is
opaque, so its own level is always 0 and the reading comes from the space next to it: open sky 15, next
to a torch 14, sealed inside opaque blocks 0. Light changes cause no block updates, so it polls every
10 ticks.

### Comparator `comparator`

A constant on the back is operand A, a constant on the right is operand B, and the left redstone level
1..5 selects the mode.

| Left level | Mode | True when |
| --- | --- | --- |
| 1 | less than | `A < B` |
| 2 | less or equal | `A <= B` |
| 3 | equal | `A == B` |
| 4 | greater or equal | `A >= B` |
| 5 | greater than | `A > B` |

True outputs 15, false outputs 0. A left level of 0 or above 5 is an invalid mode and outputs 0; a
missing constant on the back or right also outputs 0. Comparison uses the raw integers in the
constants, so negative values and values above 15 work and are not clamped.

### Clock `clock`

Aim at it and run `/circuit-set <ticks>` to set the period N, at least 1. It then emits one tick of
HIGH on its output face every N ticks and stays 0 otherwise. `N = 1` is constant HIGH; with no period
set it stays 0. Period and phase live in the block and survive a world reload.

### Constant `constant`

Stores one integer, negative or above 15. It emits no redstone and is a data source only: the scanner
reads it, a data wire copies it, the comparator uses it as an operand.

### String `string_value`

Stores up to 256 characters. It emits no redstone and no block reads it today, but a data wire can
copy it into another string block.

### Variable `variable`

One variable per block. Its type is set by the last write and can change at any time.

```text
/circuit-set int=42          → stores the integer 42
/circuit-set str="hello"     → stores the text hello (quotes are syntax)
/circuit-set null            → sets the empty value null
```

The texture follows the type: orange for int, blue for string, purple for null, grey for never set.
Text must be quoted, which keeps `str=42` and `int=42` apart.

`null` is a value that is empty: a data wire copies it, and it becomes 0 in a constant or the empty
string in a string block. Never-set is different — it is no source at all, so a data wire writes
nothing. The data wire section below relies on that difference.

A data wire can read a variable and also write into it. Breaking and re-placing keeps the value: the
dropped item carries the data, its tooltip shows the stored value, and pick-blocking gives the same
item with data.

### Data wire `data_wire`

Copies the value on its back into the data block in front. Side arrows point in the flow direction,
the back reads and the front writes, it transfers once per tick without any redstone signal, chains up
to 64 wires, and abandons a loop.

The back can be one of four sources:

- a constant block, copying its integer;
- a string block, copying its text;
- a variable block, copying its current int, str or null (nothing while unset);
- a redstone signal (dust, lever, wire, gate output), using its level 0..15 as an integer.

Air on the back means nothing is connected and the target keeps its value. The front can be a
constant, a string or a variable.

| Source → target | Result |
| --- | --- |
| constant → constant | copied as is |
| constant → string | decimal text |
| redstone level → constant | that level |
| redstone level → string | that level as text |
| string → string | copied as is |
| string → constant | the text must be an integer, otherwise it fails |
| null → constant | writes 0 |
| null → string | writes the empty string |

Writing into a variable converts to its current type: an int variable takes an integer, a string
variable takes text, and an unset or null variable follows the source type. A failed conversion turns
the wire into the red error texture; fix the value and it recovers by itself.

### Hover block `hover_block`

Looks like a transparent block with a thin white frame, but it is a full block.

| Property | Detail |
| --- | --- |
| Holds things | dust, torches and pressure plates can be placed on top |
| Walk through | no collision box, and you cannot stand on it |
| See through | only a white frame, so it does not block the view |
| No logic | not conductive, stores nothing, does not connect to wires |
| Cost | terminal: 4 wires → 16 blocks |

It is see-through but still blocks light like a solid block. That is the price for holding redstone
dust on top.

### Solid color block `color_block`

Placed as black. Aim at it and run `/circuit-set #12AB34`; the color appears on the next game tick.
It accepts `#RRGGBB`, `RRGGBB` and `0xRRGGBB`, and rejects invalid colors with a message. Terminal: 4
wires → 16 blocks.

### Hidden sign `hidden_sign`

It stands and stores text like a vanilla sign, but the block itself is completely invisible — only
the text is drawn.

- Aim at it and run `/circuit-set <text>` to set the text.
- While the text is empty it shows an italic grey `Type something...`.
- `/circuit-set ""` clears the text.
- Text is white, up to 4 lines, wrapped automatically, visible from both sides.
- It has no collision box and takes no part in redstone or data logic.

## Commands

None of these commands needs operator permission; the requirement is written in each section.

### Crafting recipes

| Result | Ingredients |
| --- | --- |
| 1 wire | 1 redstone dust, shapeless |
| 1 redstone dust | 1 wire, shapeless |
| 1 circuit terminal | shaped: `redstone wire redstone` / `wire crafting table wire` / `redstone wire redstone` |

The other 17 blocks have no crafting recipe and are bought with the circuit terminal.

### Circuit terminal

```text
/circuit-terminal list                # every item that can be bought
/circuit-terminal get and_gate        # 4 wires for one AND gate
/circuit-terminal get and_gate 3      # 3 batches = 3 gates, costs 12 wires
/circuit-terminal get hover_block 32  # 16 per batch: 2 batches, costs 8 wires
```

| Rule | Detail |
| --- | --- |
| Requirement | a circuit terminal in your inventory |
| Price | 4 wires per batch |
| Batch size | 1 for most blocks, 16 for hover and color blocks |
| Custom amount | must be whole batches; price = batches × 4 |
| Namespace | `and_gate` and `circuit-redstone_logic:and_gate` both work |
| Not allowed | you cannot buy a terminal with a terminal |
| Drops | every block drops itself and can be moved |

The success message states the amount, for example `Gave 16x Hover Block for 4 wires.`

### Writing values `/circuit-set`

| Block | Value | Example |
| --- | --- | --- |
| Constant | integer | `/circuit-set 7` |
| String | text, up to 256 characters | `/circuit-set hello world` |
| Variable | `int=…`, `str="…"` or `null` | `/circuit-set int=42` |
| Hidden sign | text, `""` clears it | `/circuit-set hello` |
| Solid color block | hex color | `/circuit-set #12AB34` |
| Clock | period, at least 1 tick | `/circuit-set 20` |

Aiming is optional: `/circuit-set <x> <y> <z> <value>` works from the console and command blocks.
Wrong values produce explicit errors such as "not an integer", "not a hex colour" and "the frequency
must be an integer >= 1"; a bare `42` on a variable asks you to write `int=42`.

### Reading values `/circuit-read`

```text
/circuit-read                     # the constant, string or variable you are aiming at
/circuit-read <x> <y> <z>         # read at a position
```

The output format is `<block name> (<x>, <y>, <z>): <value>`, for example:

```text
Constant (30, 100, 1): 20
String (32, 100, 1): "hello world"
Variable (33, 100, 1): 42
Variable (35, 100, 1): (not set)
```

Chinese locales use the full-width colon.

### Debug `/circuit-debug`

For building and troubleshooting circuits. No operator permission needed; the console and command blocks can run it too.

```text
/circuit-debug info full-circuit             # dump every circuit block in the world
/circuit-debug test start <x> <y> <z>        # simulate the wire network at that position
/circuit-debug test dead-loop <x> <y> <z>    # simulate and detect dead loops
/circuit-debug test stop [id]                # stop a simulation
/circuit-debug run-chart <A> <B> <tick>      # record a chart
/circuit-debug run-video <A> <B> <tick>      # record an animation
```

| Subcommand | What it does |
| --- | --- |
| `info full-circuit` | Lists every circuit block in the loaded chunks, one chat message per block as `<name> (<x>, <y>, <z>) [<SNBT>]`. No aiming, no truncation. |
| `test start <x> <y> <z>` | Simulates the wire network at that position in a sandbox, reports every state change, then prints the finish reason with the tick count and the change count. |
| `test dead-loop <x> <y> <z>` | Same run with live dead-loop detection; a detected oscillation shows a red warning with its period in ticks. |
| `test stop [id]` | Without an id it stops all of your simulations, with an id it stops that one. |
| `run-chart <A> <B> <tick>` | Records every block change in the region A~B (two opposite corners) for N ticks, then renders a chart image and a text change list. |
| `run-video <A> <B> <tick>` | Renders the same recording as an animated GIF, plus an MP4 when ffmpeg is installed. |

Details:

- The `test start` message prints the simulation id, so you can copy it into `test stop <id>`. At most 4 simulations run at the same time.
- The simulation needs a wire at that position or on one of its six neighbours, otherwise it reports "nothing to simulate here".
- There are six finish reasons: stable, dead loop, stopped by hand, tick limit reached, time budget exceeded and error.
- `run-chart` takes 1..2400 ticks over at most 16384 blocks; one column is one tick, one row is one block, colour and number show the state.
- `run-video` takes 1..600 ticks, one frame per tick, top view.
- One recorder per player at a time; start the next one after the previous finished.
- Both recorders always write to `<game dir>/circuit-charts/`. In single player an in-game save screen lets you change the folder and the file name; a server or the console only uses the default folder.
- A block with too many changes keeps only the first 4096 changes in the chart, with a warning.


## Example circuits

### Inverter

```text
[redstone block / lever] ── wire ──→ [NOT gate] ──→ wire ──→ [lamp]
```

Lever off makes the wire LOW, the NOT gate outputs 15 and the lamp lights; lever on turns it off.

### Two-input AND gate

```text
              [lever #2]
                  │  (into the left side)
[redstone block] ── wire ──→ [AND gate] ──→ wire ──→ [lamp]
```

The lamp lights only when the block is active and the lever is on. Feeding both sides makes the left
side win.

### Clock blinker

```text
[clock] ──→ wire ──→ [lamp]
```

`/circuit-set 10` blinks every 10 ticks; `/circuit-set 1` is constant HIGH.

### Scanner counting blocks

```text
[stone] [stone] [stone] [scanner] ──→ wire
```

The three stones are within 15 blocks to the left, so the scanner outputs 3, capped at 15. Put a
constant on a ray for a fixed value, and keep the output wire off the rays.

### Comparator checking A > B

```text
                      [dust, strength 5]        ← left side: mode 5 (greater than)
                             │
[constant A=5] (back) ── [comparator] ──→ wire ──→ [lamp]
                             │
                      [constant B=3]            ← right side: B
```

True outputs 15 and lights the lamp; swap the constants and it goes dark.

### Moving a value with a data wire

```text
[constant = 7] ──→ [data wire →] ──→ [constant]
```

The arrow points right, so 7 enters from the back and is written into the constant in front; read it
back with `/circuit-read`. A string source `"42"` makes the target constant 42, and `"abc"` turns the
wire into the red error texture while the target stays unchanged.

### Reading and writing a variable

```text
[constant = 7] ──→ [data wire →] ──→ [variable]   ← unset variable becomes int 7
[variable]     ──→ [data wire →] ──→ [constant]   ← copies the current value
```

You can also fix the type first with `/circuit-set int=0` and let the data wire follow it.

### Running dust through the air

```text
[hover blocks] × N    ← a layer, dust needs support or it pops off
   ↑ dust on top
[dust] ── into a gate or wire
```

You walk straight through the layer, so it does not block the view.

### Labeling a circuit with a hidden sign

```text
[hidden sign]   ← place it anywhere, the block is invisible
    ↑ /circuit-set clock 10t
```

Handy for naming sections, marking inputs and outputs, or writing down timing parameters without
blocking wiring.

## FAQ and limitations

### FAQ

A gate has a signal but does not output: check the direction first (the output face should not point
at the source; look for the colored face), then the input faces (A on the back, B on the sides), then
whether the input is really 0 (gates treat only above 0 as HIGH).

The comparator always outputs 0: the back and right sides must be constant blocks and the left side
must be a level 1..5; strings and variables are not accepted.

The scanner shows a strange number: check for a constant on the rays first (a constant wins), then
whether the up ray sees anything (the up count wins), then whether both sides counted something (the
larger count is used). Water, leaves, dust, wires and torches all count.

A data wire does not transfer or turns red: the front must touch a constant, string or variable; the
back must not be air, and any other block is read as a redstone level 0..15; a red texture means a
conversion failure, usually text that is not an integer.

Constant, string or variable: a constant holds an integer, a string holds text, and a variable holds
either type in one block and can be written by a data wire.

A variable set with `/circuit-set` changes back: a data wire is still writing it every tick, so
disconnect the source first.

How to use a hidden sign: place it and run `/circuit-set hello` to show white text; `/circuit-set ""`
clears it and restores the grey hint.

How the amount is priced: by batches, 4 wires each. Most blocks are 1 per batch and hover and color
blocks are 16 per batch, and the amount must be whole batches.

### Known limitations

- Vanilla dust never draws a connecting arm toward this mod's wires, although they are connected.
- Interop with vanilla blocks runs at vanilla 20 Hz.
- A single wire network is capped at 4096 positions and a data wire chain at 64 hops.
- A per-tick evaluation budget exists, so extreme circuits continue on the next tick.
- The hover block is see-through but still blocks light like a solid block.
- Changing the mod id wipes old-id blocks in existing worlds; back up first.
- The art is placeholder: flat colors and direction arrows, not final art.

## License

MIT licensed; the full text is in [LICENSE](LICENSE).

Code, assets and documentation were generated by DeepSeek-v4.1-Flash and DeepSeekHarness.
