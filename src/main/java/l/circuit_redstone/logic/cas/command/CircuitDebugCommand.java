package l.circuit_redstone.logic.cas.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.block.CircuitPart;
import l.circuit_redstone.logic.cas.debug.ChartSession;
import l.circuit_redstone.logic.cas.sim.CircuitSimulator;
import l.circuit_redstone.logic.cas.sim.SimulationHandle;
import l.circuit_redstone.logic.cas.sim.SimulationManager;
import l.circuit_redstone.logic.cas.sim.SimulationSummary;
import l.circuit_redstone.logic.cas.sim.SimulatorCallback;
import l.circuit_redstone.logic.cas.sim.SimulatorOptions;

/**
 * 底层调试指令 {@code /circuit-debug}。
 *
 * <p>架构：{@link CircuitSimulator} 是唯一的推演引擎，子指令只是不同的"执行策略"
 * （整电路快照 / 普通播报 / 死循环检测），引擎逻辑不复制。
 *
 * <p>所有子指令都接受控制台/命令方块执行：输出一律走 {@link ServerCommandSource#sendFeedback}，
 * 所以服务端自测可以直接用 {@code say} 之外的方式校验输出。
 */
public final class CircuitDebugCommand {
	private static final String ARG_POS = "pos";
	private static final String ARG_POS_A = "pos1";
	private static final String ARG_POS_B = "pos2";
	private static final String ARG_TICKS = "ticks";
	private static final String ARG_ID = "id";
	/** 控制台/命令方块没有玩家 UUID：用全零 UUID 当作"这台服务器"自己。 */
	private static final UUID CONSOLE_OWNER = new UUID(0L, 0L);

	private CircuitDebugCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("circuit-debug")
				.then(CommandManager.literal("info")
						.then(CommandManager.literal("full-circuit")
								.executes(CircuitDebugCommand::infoFullCircuit)))
				.then(CommandManager.literal("test")
						.then(CommandManager.literal("start")
								.then(CommandManager.argument(ARG_POS, BlockPosArgumentType.blockPos())
										.executes(context -> startSimulation(context, false))))
						.then(CommandManager.literal("dead-loop")
								.then(CommandManager.argument(ARG_POS, BlockPosArgumentType.blockPos())
										.executes(context -> startSimulation(context, true))))
						.then(CommandManager.literal("stop")
								.executes(CircuitDebugCommand::stopOwned)
								.then(CommandManager.argument(ARG_ID, StringArgumentType.word())
										.executes(CircuitDebugCommand::stopById))))
				.then(CommandManager.literal("run-chart")
						.then(CommandManager.argument(ARG_POS_A, BlockPosArgumentType.blockPos())
								.then(CommandManager.argument(ARG_POS_B, BlockPosArgumentType.blockPos())
										.then(CommandManager.argument(ARG_TICKS,
												IntegerArgumentType.integer(1, ChartSession.MAX_TICKS))
												.executes(context -> runRecording(context,
														ChartSession.Mode.CHART))))))
				.then(CommandManager.literal("run-video")
						.then(CommandManager.argument(ARG_POS_A, BlockPosArgumentType.blockPos())
								.then(CommandManager.argument(ARG_POS_B, BlockPosArgumentType.blockPos())
										.then(CommandManager.argument(ARG_TICKS,
												IntegerArgumentType.integer(1, ChartSession.MAX_VIDEO_TICKS))
												.executes(context -> runRecording(context,
														ChartSession.Mode.VIDEO)))))));
	}

	/* ---------------------------------------------------------------- run-chart / run-video */

	/**
	 * {@code run-chart} 与 {@code run-video} 共用的入口：观察真实世界里 A~B 矩形区域，
	 * 记录 C tick 内的每一次方块状态变化，结束后交给对应的渲染器（图 / 动画 GIF）。
	 */
	private static int runRecording(CommandContext<ServerCommandSource> context, ChartSession.Mode mode) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();
		BlockPos first = BlockPosArgumentType.getBlockPos(context, ARG_POS_A);
		BlockPos second = BlockPosArgumentType.getBlockPos(context, ARG_POS_B);
		int ticks = IntegerArgumentType.getInteger(context, ARG_TICKS);
		UUID owner = ownerId(source);
		boolean video = mode == ChartSession.Mode.VIDEO;

		if (ChartSession.hasSession(owner)) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.chart_busy"));
			return 0;
		}
		if (video && ticks > ChartSession.MAX_VIDEO_TICKS) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.video_too_long",
					String.valueOf(ChartSession.MAX_VIDEO_TICKS)));
			return 0;
		}
		ChartSession session = ChartSession.start(world, owner, first, second, ticks, mode);
		if (session == null) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.chart_too_big",
					String.valueOf(ChartSession.MAX_BLOCKS)));
			return 0;
		}
		CircuitRedstone.LOGGER.info("[{}] {}", CircuitRedstone.MOD_ID, session.describe());
		source.sendFeedback(() -> Text.translatable(video
				? "command.circuit-redstone_logic.debug.video_started"
				: "command.circuit-redstone_logic.debug.chart_started",
				String.valueOf(ticks), String.valueOf(session.getBlockCount())), false);
		return 1;
	}

	/* ---------------------------------------------------------------- info */

	/**
	 * 需求：不依赖准星、不依赖"必须是导线"，直接输出当前世界里<b>全部电路方块</b>的快照。
	 *
	 * <p>每行固定为 {@code <方块名> (<x,y,z>) [<SNBT>]}，<b>一个方块一条聊天消息</b>
	 * （原版聊天不会把 {@code \n} 当换行，把多行拼进一条消息只会在游戏里显示成一整行），
	 * 不添加表头/统计/截断行。
	 */
	private static int infoFullCircuit(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();

		List<BlockPos> positions = collectCircuitPositions(world);
		if (positions.isEmpty()) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.info_empty"));
			return 0;
		}

		for (BlockPos pos : positions) {
			BlockState state = world.getBlockState(pos);
			Text line = Text.translatable("command.circuit-redstone_logic.debug.info_line",
					Text.translatable(state.getBlock().getTranslationKey()),
					Text.literal(pos.toShortString()),
					Text.literal(snbt(state)));
			source.sendFeedback(() -> line, false);
		}
		return positions.size();
	}

	/**
	 * 收集当前世界<b>所有已加载区块</b>里的电路方块（导线 + 各类逻辑门/限制器/增强器/扫描器/传感器）。
	 *
	 * <p>先按区块段的调色板做一次 {@code hasAny} 预筛选，只有真正含电路方块的段才逐格确认，
	 * 因此对整张世界地图做一次快照也不会产生"每格一次世界查询"的开销。
	 */
	private static List<BlockPos> collectCircuitPositions(ServerWorld world) {
		List<BlockPos> positions = new ArrayList<>();
		int sectionSize = ChunkSectionPos.getBlockCoord(1);

		for (ChunkHolder holder : world.getChunkManager().threadedAnvilChunkStorage.entryIterator()) {
			WorldChunk chunk = holder.getWorldChunk();
			if (chunk == null) {
				continue;
			}
			ChunkSection[] sections = chunk.getSectionArray();
			int startX = chunk.getPos().getStartX();
			int startZ = chunk.getPos().getStartZ();
			for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
				ChunkSection section = sections[sectionIndex];
				if (section.isEmpty() || !section.getBlockStateContainer().hasAny(CircuitDebugCommand::isCircuitState)) {
					continue;
				}
				int startY = chunk.getBottomY() + ChunkSectionPos.getBlockCoord(sectionIndex);
				for (int localY = 0; localY < sectionSize; localY++) {
					for (int localZ = 0; localZ < sectionSize; localZ++) {
						for (int localX = 0; localX < sectionSize; localX++) {
							if (isCircuitState(section.getBlockState(localX, localY, localZ))) {
								positions.add(new BlockPos(startX + localX, startY + localY, startZ + localZ));
							}
						}
					}
				}
			}
		}
		positions.sort(Comparator.naturalOrder());
		return positions;
	}

	private static boolean isCircuitState(BlockState state) {
		return state.getBlock() instanceof CircuitPart;
	}

	/* ---------------------------------------------------------------- test */

	private static int startSimulation(CommandContext<ServerCommandSource> context, boolean deadLoop) {
		ServerCommandSource source = context.getSource();
		BlockPos start = BlockPosArgumentType.getBlockPos(context, ARG_POS);
		ServerWorld world = source.getWorld();
		if (!isCircuitState(world.getBlockState(start))) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.not_circuit"));
			return 0;
		}
		if (!CircuitSimulator.hasNetwork(world, start)) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.no_network"));
			return 0;
		}

		SimulatorOptions options = deadLoop ? SimulatorOptions.forDeadLoopProbe() : SimulatorOptions.forPropagation();
		List<Text> lines = Collections.synchronizedList(new ArrayList<>());
		SimulationHandle handle = CircuitSimulator.simulate(world, ownerId(source), start,
				createCallback(source, lines), options);
		if (handle == null) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.too_many",
					SimulationManager.maxJobs()));
			return 0;
		}

		UUID id = handle.getId();
		source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.debug.started",
				id.toString(), id.toString()), false);
		return 1;
	}

	/**
	 * 构造回调：所有聊天输出都切回主线程执行（引擎在后台线程跑）。
	 */
	private static SimulatorCallback createCallback(ServerCommandSource source, List<Text> lines) {
		return new SimulatorCallback() {
			@Override
			public void onStateChange(int tick, BlockPos pos, BlockState oldState, BlockState newState) {
				lines.add(Text.translatable("command.circuit-redstone_logic.debug.change",
						Text.translatable(newState.getBlock().getTranslationKey()),
						Text.literal(blockId(newState)),
						Text.literal(snbt(oldState)),
						Text.literal(snbt(newState)),
						Text.literal(pos.toShortString())));
			}

			@Override
			public void onDeadLoopDetected(int cycleTicks) {
				sendOnMainThread(source, () -> Text.translatable("command.circuit-redstone_logic.debug.dead_loop", cycleTicks)
						.formatted(Formatting.RED));
			}

			@Override
			public void onError(Throwable error) {
				sendOnMainThread(source, () -> Text.translatable("command.circuit-redstone_logic.debug.error",
						String.valueOf(error)));
			}

			@Override
			public void onFinished(SimulationSummary summary) {
				sendOnMainThread(source, () -> {
					MutableText result = Text.literal("");
					synchronized (lines) {
						for (Text line : lines) {
							source.sendFeedback(() -> line, false);
						}
					}
					result.append(Text.translatable("command.circuit-redstone_logic.debug.finished",
							Text.translatable(summary.reason().translationKey()),
							String.valueOf(summary.ticks()),
							String.valueOf(summary.changes())));
					return result;
				});
			}
		};
	}

	/* ---------------------------------------------------------------- stop */

	private static int stopById(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		String raw = StringArgumentType.getString(context, ARG_ID);
		UUID id;
		try {
			id = UUID.fromString(raw);
		} catch (IllegalArgumentException invalid) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.no_such_id", raw));
			return 0;
		}
		if (!SimulationManager.stop(id)) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.debug.no_such_id", raw));
			return 0;
		}
		source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.debug.stopped", id.toString()), false);
		return 1;
	}

	private static int stopOwned(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		int stopped = SimulationManager.stopAll(ownerId(source));
		source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.debug.stopped_all",
				String.valueOf(stopped)), false);
		return stopped;
	}

	/* ---------------------------------------------------------------- 工具 */

	/** 执行者 UUID：玩家用玩家自己的，控制台/命令方块用全零 UUID。 */
	private static UUID ownerId(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		return player == null ? CONSOLE_OWNER : player.getUuid();
	}

	private static String blockId(BlockState state) {
		Block block = state.getBlock();
		return String.valueOf(Registries.BLOCK.getId(block));
	}

	/** SNBT：使用原版 NbtHelper 序列化方块状态。 */
	private static String snbt(BlockState state) {
		return NbtHelper.fromBlockState(state).toString();
	}

	/** 在主线程执行（后台线程不能直接发包/写指令结果）。 */
	private static void sendOnMainThread(ServerCommandSource source, Supplier<Text> message) {
		source.getServer().execute(() -> source.sendFeedback(message, false));
	}
}
