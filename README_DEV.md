# Circuit Redstone - Logic · 开发文档入口

**开发者向** | [玩家向中文](README.md) | [玩家向英文](README_EN.md)

本文件只讲"怎么构建、怎么测、怎么调试、怎么发布"，玩家怎么玩不在这里。
深入的技术细节（信号协议、更新调度、方块实体设计）见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## 环境

| 项 | 值 |
| --- | --- |
| 模组 id | `circuit-redstone_logic` |
| 包名 | `l.circuit_redstone.logic.cas` |
| 目标游戏 | Minecraft 1.20.4 |
| 构建 JVM | 需要 JDK 25（Loom 1.19.4 的要求） |
| 字节码目标 | Java 17（`options.release = 17`） |
| 依赖缓存 | 仓库内 `.gradle-home`（离线可用） |

构建 JVM 不是 25 时，Loom 会在配置阶段直接失败。本机固定使用 `zulu-25`。

## 构建

```powershell
Set-Location .\CRLogic
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) '.gradle-home')
.\gradlew.bat clean build
```

产物：

| 路径 | 用途 |
| --- | --- |
| `build/libs/circuit-redstone_logic-1.28.7.jar` | 发布用（已 remap 到 intermediary） |
| `build/devlibs/` | 开发用，不要发布 |

`clean` 偶尔会失败，因为 `build/` 下有日志文件被残留句柄占用；此时直接跑 `build` 即可。

## 自测

四个层次，按顺序跑。

```powershell
pwsh -File tools/GenerateAssets.ps1
pwsh -File tools/ValidateAssets.ps1
javac -encoding UTF-8 -cp build/classes/java/main -d build/tmp/logiccheck tools/LogicCheck.java
java -cp "build/classes/java/main;build/tmp/logiccheck" LogicCheck
pwsh -File tools/SmokeTest.ps1
```

| 层 | 覆盖内容 | 期望结果 |
| --- | --- | --- |
| `GenerateAssets.ps1` | 贴图、模型、方块状态、语言、配方、掉落表全部由脚本生成 | 输出 assets 目录 |
| `ValidateAssets.ps1` | JSON 合法性、模型与贴图引用、语言占位符数量、tooltip 行数、配方白名单、掉落表 | `106 个 JSON，18 个方块` |
| `LogicCheck.java` | 纯逻辑：信号协议、门真值表、扫描器、比较器、时钟、数据转换、变量赋值、兑换计价 | `161 checks, 0 failures` |
| `SmokeTest.ps1` | 三轮专用服务器，真实放置方块并断言状态、掉落、指令输出、运行图与视频 | 全部 `PASS`，无 `FAIL` |

冒烟测试注意：

- 三轮必须分开跑（第 1 轮放置、第 2 轮断言、第 3 轮断电断言），因为求值在 tick 末尾；
- 每个 `say OK_*` 标记都要登记在 `$expected` 里，否则等于跑了没检查；
- 运行图与视频分别在第 2、3 轮，同一执行者不能同时开两个录制。

## 调试指令

```text
/circuit-debug info full-circuit             # 导出全部电路方块，一行一个方块的状态
/circuit-debug test start <x> <y> <z>        # 沙盒推演一张导线网络
/circuit-debug test dead-loop <x> <y> <z>    # 基于状态哈希检测死循环
/circuit-debug test stop [id]                # 停止推演
/circuit-debug run-chart <A> <B> <ticks>     # 记录真实世界并渲染运行图 + txt
/circuit-debug run-video <A> <B> <ticks>     # 同一份记录渲染成 GIF，有 ffmpeg 时另出 MP4
```

`info full-circuit` 的输出契约是 `<方块名> (<x>, <y>, <z>) [<SNBT>]`，一行一个方块，不加表头、不合并多行。

`run-chart` 与 `run-video` 默认写到 `<游戏目录>/circuit-charts/`；单人游戏还会弹游戏内另存为界面，
开发客户端是无头环境，AWT 原生对话框不可用。

## 代码结构

```text
src/main/java/l/circuit_redstone/logic/cas/
  CircuitRedstone.java        入口：注册、初始化、启动日志
  ModRegistry.java            18 个方块、物品、方块实体类型、物品组
  block/                      方块实现（导线、门、扫描器、变量、悬空方块、隐形告示牌……）
  block/entity/               方块实体（电平、文本、变量、时钟相位、颜色）
  logic/                      纯逻辑：信号协议、门真值表、扫描器合并、数据转换、变量赋值、兑换规则
  update/                     求值调度：脏队列、导线网络 BFS、红石粉独立求值、孤儿方块实体清理
  command/                    /circuit-terminal、/circuit-set、/circuit-read、/circuit-debug
  debug/                      运行图、视频、图表渲染、保存
  client/                     客户端：颜色 tint、cutout 渲染层、隐形告示牌文字渲染器
  item/                       tooltip 物品、变量方块物品
tools/                        资源生成、资源校验、逻辑自测、冒烟测试
docs/DEVELOPMENT.md           技术细节（协议、调度、上限）
```

设计约定：

- 方块更新回调只入队，真正的求值集中在 `END_WORLD_TICK`，避免递归更新；
- 方块状态是客户端同步与渲染的依据，方块实体是权威数据；
- 数据方块（常量、字符串、变量）不发射红石，只被扫描器与数据线读写；
- 模型必须带 `parent` 或 `elements`，否则会被烘焙成空模型（方块不可见）。

## 发布

1. 改 `gradle.properties` 的 `version`（`fabric.mod.json` 用 `${version}` 引用）。
2. `gradlew build`，确认 `build/libs/circuit-redstone_logic-<版本>.jar` 生成。
3. 解包检查 `fabric.mod.json`：`version` 与 `license` 必须正确。
4. 打包分发目录：jar + 玩家向 README（中英）+ LICENSE，压成 `release/<版本>.zip`。

发布 jar 必须是 `build/libs` 下的 remapped 产物；`build/devlibs` 里的只能在开发环境使用。
判断方法：类文件里对 Minecraft 的引用应为 `class_xxxx`（intermediary），而不是 yarn 名。

## 已知工程约束

- 构建 JVM 必须 ≥ 25，字节码目标是 17，两者不能混。
- 客户端是无头的（`-Djava.awt.headless=true`），依赖 AWT 的保存对话框在开发环境不可用。
- `run/` 是开发运行目录：客户端、专用服务器、冒烟测试共用，日志文件可能被占用。
- 杀掉 Gradle 客户端进程可能连带杀掉共享 daemon，避免在构建或测试运行期间操作。
- 资源全部由脚本生成，不要手改 `assets/`、`data/` 下的文件；手改会在下一次生成时被覆盖。
