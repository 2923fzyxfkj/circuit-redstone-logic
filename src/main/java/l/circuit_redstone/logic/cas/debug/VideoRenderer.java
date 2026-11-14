package l.circuit_redstone.logic.cas.debug;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.w3c.dom.NodeList;

import l.circuit_redstone.logic.cas.CircuitRedstone;

/**
 * 把 {@link ChartSession} 的记录渲染成一段**动画 GIF** 视频（一个 tick 一帧）。
 *
 * <p>画面是区域的**俯视图**：每个 (x, z) 列取该列最高的非空气方块，颜色沿用运行图的语义色。
 * 为什么用 GIF：Java 标准库自带 GIF 序列写入，不需要任何外部编码器（ffmpeg 之类的依赖），
 * 浏览器 / Windows 照片 / 图片查看器都能直接播放；20 帧/秒对应 1 tick 一帧。
 */
public final class VideoRenderer {
	/** 画面最长边（像素）。 */
	private static final int MAX_SIDE = 512;
	private static final int MIN_CELL = 2;
	private static final int MAX_CELL = 16;
	private static final int LABEL_HEIGHT = 16;
	/** 1 tick 一帧 = 20 帧/秒，GIF 的 delay 单位是 1/100 秒。 */
	private static final int TICK_DELAY_CENTIS = 5;

	private static final Color BACKGROUND = new Color(0x14141A);
	private static final Color GRID = new Color(0x242430);
	private static final Color TEXT = new Color(0xD0D0D8);

	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/** 渲染结果：GIF 字节 + 文件名前缀（不含扩展名）+ 尺寸信息。 */
	public record Video(byte[] gif, String baseName, int width, int height, int frames, int ticks) {
		public String gifFileName() {
			return baseName + ".gif";
		}

		public String mp4FileName() {
			return baseName + ".mp4";
		}
	}

	/** 一帧的消费者（渲染与导出共用同一条时间线）。 */
	private interface FrameSink {
		void accept(int tick, BufferedImage frame) throws IOException;
	}

	private VideoRenderer() {
	}

	public static Video render(ServerWorld world, BlockPos min, BlockPos max, int ticks,
			Map<BlockPos, BlockState> initialStates,
			Map<BlockPos, List<ChartSession.Change>> changesByPosition) {
		FrameInfo info = frameInfo(min, max);
		List<BufferedImage> frames = new ArrayList<>();
		try {
			forEachFrame(min, max, ticks, initialStates, changesByPosition, info,
					(tick, frame) -> frames.add(frame));
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.error("[{}] failed to render video frames", CircuitRedstone.MOD_ID, failure);
			return null;
		}

		byte[] gif;
		try {
			gif = encodeGif(frames, TICK_DELAY_CENTIS);
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.error("[{}] failed to encode video gif", CircuitRedstone.MOD_ID, failure);
			return null;
		}
		String baseName = "circuit-video-" + FILE_STAMP.format(LocalDateTime.now());
		return new Video(gif, baseName, info.width(), info.height(), frames.size(), ticks);
	}

	/**
	 * 把每一帧导出成 PNG（给 ffmpeg 用）。
	 *
	 * @return 导出的帧数
	 */
	public static int exportFrames(ServerWorld world, BlockPos min, BlockPos max, int ticks,
			Map<BlockPos, BlockState> initialStates,
			Map<BlockPos, List<ChartSession.Change>> changesByPosition,
			Path directory) throws IOException {
		Files.createDirectories(directory);
		FrameInfo info = frameInfo(min, max);
		int[] count = { 0 };
		forEachFrame(min, max, ticks, initialStates, changesByPosition, info, (tick, frame) -> {
			Path target = directory.resolve(String.format("frame-%04d.png", tick));
			// 返回 false 说明没有 PNG 编码器：直接报错，免得 ffmpeg 拿到缺帧的序列。
			if (!ImageIO.write(frame, "png", target.toFile())) {
				throw new IOException("no PNG writer available");
			}
			count[0]++;
		});
		return count[0];
	}

	/** 画面尺寸信息（导出与渲染共用）。 */
	private record FrameInfo(int sizeX, int sizeZ, int cell, int width, int height, boolean drawGrid) {
	}

	private static FrameInfo frameInfo(BlockPos min, BlockPos max) {
		int sizeX = max.getX() - min.getX() + 1;
		int sizeZ = max.getZ() - min.getZ() + 1;
		int cell = Math.max(MIN_CELL, Math.min(MAX_CELL, MAX_SIDE / Math.max(sizeX, sizeZ)));
		return new FrameInfo(sizeX, sizeZ, cell, sizeX * cell, sizeZ * cell + LABEL_HEIGHT, cell >= 6);
	}

	/** 按 tick 顺序渲染每一帧并交给 {@code sink}。 */
	private static void forEachFrame(BlockPos min, BlockPos max, int ticks,
			Map<BlockPos, BlockState> initialStates,
			Map<BlockPos, List<ChartSession.Change>> changesByPosition,
			FrameInfo info, FrameSink sink) throws IOException {
		// 每 tick 的变化按 tick 分桶，渲染时顺序应用（不用每帧重算整段历史）。
		Map<Integer, List<Map.Entry<BlockPos, ChartSession.Change>>> changesByTick = new HashMap<>();
		for (Map.Entry<BlockPos, List<ChartSession.Change>> entry : changesByPosition.entrySet()) {
			for (ChartSession.Change change : entry.getValue()) {
				changesByTick.computeIfAbsent(change.tick(), ignored -> new ArrayList<>())
						.add(Map.entry(entry.getKey(), change));
			}
		}

		Map<BlockPos, BlockState> current = new HashMap<>(initialStates);
		Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
		for (int tick = 0; tick <= ticks; tick++) {
			if (tick > 0) {
				for (Map.Entry<BlockPos, ChartSession.Change> entry : changesByTick.getOrDefault(tick, List.of())) {
					current.put(entry.getKey(), entry.getValue().state());
				}
			}
			sink.accept(tick, renderFrame(min, max, info, current, tick, ticks, labelFont));
		}
	}

	private static BufferedImage renderFrame(BlockPos min, BlockPos max, FrameInfo info,
			Map<BlockPos, BlockState> current, int tick, int ticks, Font labelFont) {
		int sizeX = info.sizeX();
		int sizeZ = info.sizeZ();
		int cell = info.cell();
		int width = info.width();
		int height = info.height();
		BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = frame.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setColor(BACKGROUND);
		graphics.fillRect(0, 0, width, height);

		for (int x = 0; x < sizeX; x++) {
			for (int z = 0; z < sizeZ; z++) {
				BlockState top = topState(min, max, current, min.getX() + x, min.getZ() + z);
				int px = x * cell;
				int py = z * cell;
				graphics.setColor(ChartRenderer.colorOf(top));
				graphics.fillRect(px, py, cell, cell);
				if (info.drawGrid()) {
					graphics.setColor(GRID);
					graphics.drawRect(px, py, cell - 1, cell - 1);
				}
			}
		}

		graphics.setColor(TEXT);
		graphics.setFont(labelFont);
		// 画面窄的时候（方块少、像素被放大）只写短标签，避免文字被裁掉。
		String label = "t=" + tick + "/" + ticks + (width >= 240
				? "   top view: X -> right, Z -> down"
				: "   X> Zv");
		graphics.drawString(label, 4, height - 4);
		graphics.dispose();
		return frame;
	}

	/** 某一列最高的非空气方块（找不到就是空气）。 */
	private static BlockState topState(BlockPos min, BlockPos max, Map<BlockPos, BlockState> current,
			int x, int z) {
		for (int y = max.getY(); y >= min.getY(); y--) {
			BlockState state = current.get(new BlockPos(x, y, z));
			if (state != null && !state.isAir()) {
				return state;
			}
		}
		return Blocks.AIR.getDefaultState();
	}

	/** 用 JDK 自带的 GIF 序列写入器编码（无限循环，1 tick 一帧）。 */
	private static byte[] encodeGif(List<BufferedImage> frames, int delayCentis) throws IOException {
		// 没有 GIF 写入器时按 IOException 报（调用方只会处理这一种）。
		Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("gif");
		if (!writers.hasNext()) {
			throw new IOException("no GIF image writer available");
		}
		ImageWriter writer = writers.next();
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
			writer.setOutput(output);
			writer.prepareWriteSequence(null);
			for (BufferedImage frame : frames) {
				ImageWriteParam parameters = writer.getDefaultWriteParam();
				IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(frame), parameters);
				configureGifMetadata(metadata, delayCentis);
				writer.writeToSequence(new IIOImage(frame, null, metadata), parameters);
			}
			writer.endWriteSequence();
		} finally {
			writer.dispose();
		}
		return buffer.toByteArray();
	}

	/** 写入 GIF 的 delay + 无限循环标记。 */
	private static void configureGifMetadata(IIOMetadata metadata, int delayCentis) throws IOException {
		String format = metadata.getNativeMetadataFormatName();
		IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

		IIOMetadataNode graphicControl = findOrCreate(root, "GraphicControlExtension");
		graphicControl.setAttribute("disposalMethod", "none");
		graphicControl.setAttribute("userInputFlag", "FALSE");
		graphicControl.setAttribute("transparentColorFlag", "FALSE");
		graphicControl.setAttribute("delayTime", String.valueOf(delayCentis));
		graphicControl.setAttribute("transparentColorIndex", "0");

		IIOMetadataNode applicationExtensions = findOrCreate(root, "ApplicationExtensions");
		IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
		loop.setAttribute("applicationID", "NETSCAPE");
		loop.setAttribute("authenticationCode", "2.0");
		loop.setUserObject(new byte[] { 0x1, 0x0, 0x0 });
		applicationExtensions.appendChild(loop);

		metadata.setFromTree(format, root);
	}

	private static IIOMetadataNode findOrCreate(IIOMetadataNode root, String name) {
		NodeList nodes = root.getElementsByTagName(name);
		if (nodes.getLength() > 0) {
			return (IIOMetadataNode) nodes.item(0);
		}
		IIOMetadataNode node = new IIOMetadataNode(name);
		root.appendChild(node);
		return node;
	}
}
