package l.circuit_redstone.logic.cas.debug;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.CircuitRedstone;

/**
 * {@code /circuit-debug run-chart} 的记录器：观察真实世界，把矩形区域里 C tick 内
 * 每一次方块状态变化记下来，结束后渲染成一张图。
 *
 * <p>为什么直接观察真实世界而不是沙盒推演：图表要反映"实际发生了什么"，
 * 真实世界用的就是本模组自己的求解器；再写一份区域沙盒只会带来"图和现实不一致"的风险。
 */
public final class ChartSession {
	/** 单次记录允许的最大 tick 数（2 分钟）。 */
	public static final int MAX_TICKS = 2400;
	/** run-video 允许的最大 tick 数（一帧一个 tick，太多会生成很大的 GIF）。 */
	public static final int MAX_VIDEO_TICKS = 600;
	/** 矩形区域允许的最大方块数。 */
	public static final int MAX_BLOCKS = 16384;
	/** 每个位置最多记录多少次变化（防止极端情况吃内存）。 */
	public static final int MAX_CHANGES_PER_POSITION = 4096;

	private static final Map<UUID, ChartSession> SESSIONS = new HashMap<>();

	/** 录完之后输出成什么：时间轴图，还是逐 tick 的俯视动画。 */
	public enum Mode {
		/** {@code run-chart}：渲染成一张时间轴热力图。 */
		CHART,
		/** {@code run-video}：渲染成动画 GIF（一个 tick 一帧）。 */
		VIDEO
	}

	/** 一次状态变化：第 {@code tick} 个被记录的 tick 之后，该位置变成了 {@code state}。 */
	public record Change(int tick, BlockState state) {
	}

	private final ServerWorld world;
	private final UUID ownerId;
	private final BlockPos min;
	private final BlockPos max;
	private final int totalTicks;
	private final Mode mode;
	private final List<BlockPos> positions = new ArrayList<>();
	private final Map<BlockPos, BlockState> initialStates = new HashMap<>();
	private final Map<BlockPos, BlockState> lastStates = new IdentityHashMap<>();
	private final Map<BlockPos, List<Change>> changesByPosition = new HashMap<>();

	private int currentTick;
	private boolean truncated;

	private ChartSession(ServerWorld world, UUID ownerId, BlockPos min, BlockPos max, int totalTicks, Mode mode) {
		this.world = world;
		this.ownerId = ownerId;
		this.min = min;
		this.max = max;
		this.totalTicks = totalTicks;
		this.mode = mode;
	}

	/** 注册 tick 监听（只注册一次，所有会话共用）。 */
	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(ChartSession::onWorldTick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> SESSIONS.clear());
	}

	/**
	 * 开始一次记录。
	 *
	 * @return 区域过大时返回 null
	 */
	public static ChartSession start(ServerWorld world, UUID ownerId, BlockPos first, BlockPos second, int ticks,
			Mode mode) {
		BlockPos min = new BlockPos(
				Math.min(first.getX(), second.getX()),
				Math.min(first.getY(), second.getY()),
				Math.min(first.getZ(), second.getZ()));
		BlockPos max = new BlockPos(
				Math.max(first.getX(), second.getX()),
				Math.max(first.getY(), second.getY()),
				Math.max(first.getZ(), second.getZ()));
		long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
				* (max.getZ() - min.getZ() + 1);
		if (volume > MAX_BLOCKS) {
			return null;
		}

		ChartSession session = new ChartSession(world, ownerId, min, max, ticks, mode);
		for (BlockPos pos : BlockPos.iterate(min, max)) {
			BlockPos immutable = pos.toImmutable();
			session.positions.add(immutable);
			BlockState state = world.getBlockState(immutable);
			session.initialStates.put(immutable, state);
			session.lastStates.put(immutable, state);
		}
		SESSIONS.put(ownerId, session);
		return session;
	}

	public int getTotalTicks() {
		return totalTicks;
	}

	public int getBlockCount() {
		return positions.size();
	}

	public static boolean hasSession(UUID ownerId) {
		return SESSIONS.containsKey(ownerId);
	}

	private static void onWorldTick(ServerWorld world) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		List<ChartSession> finished = null;
		for (ChartSession session : new ArrayList<>(SESSIONS.values())) {
			if (session.world != world) {
				continue;
			}
			if (session.advance()) {
				if (finished == null) {
					finished = new ArrayList<>();
				}
				finished.add(session);
			}
		}
		if (finished == null) {
			return;
		}
		for (ChartSession session : finished) {
			SESSIONS.remove(session.ownerId);
			session.finish();
		}
	}

	/** 推进一个 tick，返回是否已经录满。 */
	private boolean advance() {
		currentTick++;
		for (BlockPos pos : positions) {
			BlockState state = world.getBlockState(pos);
			// BlockState 是注册表里 intern 过的实例，身份比较就等价于"状态是否变化"。
			if (state == lastStates.get(pos)) {
				continue;
			}
			lastStates.put(pos, state);
			List<Change> list = changesByPosition.computeIfAbsent(pos, ignored -> new ArrayList<>());
			if (list.size() < MAX_CHANGES_PER_POSITION) {
				list.add(new Change(currentTick, state));
			} else {
				truncated = true;
			}
		}
		return currentTick >= totalTicks;
	}

	private void finish() {
		int changes = changesByPosition.values().stream().mapToInt(List::size).sum();
		byte[] payload;
		String fileName;
		String doneKey;
		String failedKey;
		VideoRenderer.Video video = null;

		if (mode == Mode.VIDEO) {
			video = VideoRenderer.render(world, min, max, totalTicks, initialStates,
					changesByPosition);
			if (video == null) {
				report(Text.translatable("command.circuit-redstone_logic.debug.video_failed"));
				return;
			}
			payload = video.gif();
			fileName = video.gifFileName();
			doneKey = "command.circuit-redstone_logic.debug.video_saved";
			failedKey = "command.circuit-redstone_logic.debug.video_failed";
		} else {
			ChartRenderer.Chart chart = ChartRenderer.render(world, min, max, totalTicks, initialStates,
					changesByPosition, truncated);
			if (chart == null) {
				report(Text.translatable("command.circuit-redstone_logic.debug.chart_failed"));
				return;
			}
			payload = chart.png();
			fileName = chart.fileName();
			doneKey = "command.circuit-redstone_logic.debug.chart_done";
			failedKey = "command.circuit-redstone_logic.debug.chart_failed";
		}

		ChartSaver.Saved saved = ChartSaver.saveDefault(payload, fileName);
		if (saved == null) {
			report(Text.translatable(failedKey));
			return;
		}
		report(Text.translatable(doneKey, saved.path().toString(), String.valueOf(totalTicks),
				String.valueOf(changes)));
		// 视频模式再额外编一个 MP4（GIF 已经有了，MP4 只是多一种更方便分享的格式）。
		List<Path> extras = List.of();
		if (video != null) {
			Path mp4 = encodeMp4(video, saved.path().toAbsolutePath().getParent());
			if (mp4 != null) {
				extras = List.of(mp4);
			}
		}
		String textName = fileName.substring(0, fileName.lastIndexOf('.')) + ".txt";
		Path textPath = ChartSaver.saveText(buildTextReport(), textName);
		if (textPath != null) {
			report(Text.translatable("command.circuit-redstone_logic.debug.chart_text", textPath.toString()));
		}
		report(Text.translatable(mode == Mode.VIDEO
				? "command.circuit-redstone_logic.debug.video_help"
				: "command.circuit-redstone_logic.debug.chart_help"));
		if (changes == 0) {
			report(Text.translatable("command.circuit-redstone_logic.debug.chart_empty"));
		}
		if (truncated) {
			report(Text.translatable("command.circuit-redstone_logic.debug.chart_truncated",
					String.valueOf(MAX_CHANGES_PER_POSITION)));
		}
		offerSaveAs(payload, fileName, extras);
	}

	/**
	 * 生成同名 MP4：先把每帧导成 PNG，再交给 ffmpeg 编码，最后删掉临时帧目录。
	 *
	 * <p>为什么要落盘 PNG 再转：H.264 编码必须靠外部程序，而 ffmpeg 的标准输入是
	 * 文件序列最省事、也最不容易出错。
	 *
	 * @return 成功时的 mp4 路径；没有 ffmpeg、或编码失败时返回 null（GIF 不受影响）
	 */
	private Path encodeMp4(VideoRenderer.Video video, Path directory) {
		if (!VideoEncoder.available()) {
			report(Text.translatable("command.circuit-redstone_logic.debug.video_mp4_unavailable"));
			return null;
		}
		Path frames = directory.resolve("frames-" + video.baseName());
		try {
			int exported = VideoRenderer.exportFrames(world, min, max, totalTicks, initialStates,
					changesByPosition, frames);
			Path target = directory.resolve(video.mp4FileName());
			Path encoded = VideoEncoder.encode(frames, target);
			if (encoded == null) {
				report(Text.translatable("command.circuit-redstone_logic.debug.video_mp4_failed"));
				return null;
			}
			report(Text.translatable("command.circuit-redstone_logic.debug.video_mp4_saved",
					encoded.toString(), String.valueOf(exported)));
			return encoded;
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.error("[{}] failed to export video frames", CircuitRedstone.MOD_ID, failure);
			report(Text.translatable("command.circuit-redstone_logic.debug.video_mp4_failed"));
			return null;
		} finally {
			deleteTree(frames);
		}
	}

	/** 递归删除临时帧目录（删不掉就留着，不影响结果）。 */
	private static void deleteTree(Path directory) {
		if (!Files.exists(directory)) {
			return;
		}
		try (java.util.stream.Stream<Path> stream = Files.walk(directory)) {
			stream.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// 临时文件删不掉无所谓
				}
			});
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.warn("[{}] could not clean up {}", CircuitRedstone.MOD_ID, directory);
		}
	}

	/**
	 * 客户端环境里让玩家自己选一次保存位置。
	 *
	 * <p>以<b>游戏内界面</b>为主（AWT 原生对话框在开发客户端里可能被 headless 跳过、
	 * 或者弹到全屏窗口后面去，玩家根本看不见）；游戏内界面里再提供一个"系统另存为…"按钮，
	 * 想浏览文件夹时可以用它。
	 *
	 * @param extras 跟主文件一起另存的附加文件（例如 mp4），会按玩家选的文件名改成同名
	 */
	private void offerSaveAs(byte[] payload, String fileName, List<Path> extras) {
		Consumer<Path> onSaved = target -> {
			report(Text.translatable(
					"command.circuit-redstone_logic.debug.chart_saved_as", target.toString()));
			copyExtras(extras, target);
		};
		if (ChartSaver.offerInGameDialog(payload, fileName, onSaved)) {
			report(Text.translatable("command.circuit-redstone_logic.debug.chart_dialog_game"));
			return;
		}
		// 拿不到游戏内界面（理论上只在非客户端环境）才退回 AWT。
		ChartSaver.offerSaveDialog(payload, fileName, onSaved, () -> {
		});
	}

	/** 把附加文件复制到玩家选的文件夹里，文件名跟随主文件（只换扩展名）。 */
	private static void copyExtras(List<Path> extras, Path target) {
		if (extras.isEmpty()) {
			return;
		}
		Path folder = target.toAbsolutePath().getParent();
		String name = target.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = dot <= 0 ? name : name.substring(0, dot);
		for (Path extra : extras) {
			String extraName = extra.getFileName().toString();
			int extraDot = extraName.lastIndexOf('.');
			String extension = extraDot < 0 ? "" : extraName.substring(extraDot);
			try {
				Files.copy(extra, folder.resolve(base + extension), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException failure) {
				CircuitRedstone.LOGGER.warn("[{}] could not copy {}: {}", CircuitRedstone.MOD_ID, extra,
						failure.toString());
			}
		}
	}

	/** 把消息发给发起者（玩家可能已经离线，那就写日志）。 */
	private void report(Text message) {
		MinecraftServer server = world.getServer();
		server.execute(() -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerId);
			if (player != null) {
				player.sendMessage(message, false);
			} else {
				CircuitRedstone.LOGGER.info("[{}] {}", CircuitRedstone.MOD_ID, message.getString());
			}
		});
	}

	/** 一行摘要，给日志/测试用。 */
	public String describe() {
		return "run-chart " + min.toShortString() + " .. " + max.toShortString()
				+ " ticks=" + totalTicks + " blocks=" + positions.size();
	}

	/**
	 * 生成同名 {@code .txt}：逐条列出每个方块每次变化后的状态，纯文本、不需要看图。
	 */
	private String buildTextReport() {
		StringBuilder builder = new StringBuilder();
		builder.append("run-chart ").append(min.toShortString()).append(" .. ").append(max.toShortString())
				.append('\n');
		builder.append("ticks=").append(totalTicks)
				.append(" blocks=").append(positions.size())
				.append(" changed-blocks=").append(changesByPosition.size())
				.append('\n');
		builder.append("how to read: each line is one change; 'initial' is the state before tick 1.\n\n");

		List<BlockPos> order = new ArrayList<>(changesByPosition.keySet());
		order.sort(positionOrder());
		int lines = 0;
		for (BlockPos pos : order) {
			builder.append(describeState(pos, initialStates.getOrDefault(pos, net.minecraft.block.Blocks.AIR.getDefaultState())))
					.append("  (initial)\n");
			for (Change change : changesByPosition.get(pos)) {
				builder.append("t=").append(change.tick()).append("  ")
						.append(describeState(pos, change.state())).append('\n');
				lines++;
				if (lines > 20000) {
					builder.append("... (list truncated)\n");
					return builder.toString();
				}
			}
			builder.append('\n');
		}
		return builder.toString();
	}

	/** 一行状态描述：{@code clock (90, 100, 8) level=15}。 */
	private static String describeState(BlockPos pos, BlockState state) {
		String name = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
		String level = ChartRenderer.levelText(state);
		String suffix = level.isEmpty() ? "" : " level=" + level;
		return String.format("%s (%d, %d, %d)%s", name, pos.getX(), pos.getY(), pos.getZ(), suffix);
	}

	/** 位置排序：先 Y 再 X 再 Z，和调试导出保持一致。 */
	static Comparator<BlockPos> positionOrder() {
		return (a, b) -> {
			int cmp = Integer.compare(a.getY(), b.getY());
			if (cmp != 0) {
				return cmp;
			}
			cmp = Integer.compare(a.getX(), b.getX());
			return cmp != 0 ? cmp : Integer.compare(a.getZ(), b.getZ());
		};
	}
}
