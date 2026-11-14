package l.circuit_redstone.logic.cas.debug;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.block.CircuitBlock;
import l.circuit_redstone.logic.cas.block.ColorBlock;
import l.circuit_redstone.logic.cas.block.ConstantBlock;
import l.circuit_redstone.logic.cas.block.DataWireBlock;
import l.circuit_redstone.logic.cas.block.StringValueBlock;
import l.circuit_redstone.logic.cas.block.VariableBlock;
import l.circuit_redstone.logic.cas.block.WireBlock;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 把 {@link ChartSession} 记录到的东西画成一张"时间轴热力图"。
 *
 * <p>为了"看得懂"，图上带了三样东西：
 * <ol>
 *   <li>每行左边写<b>方块名 + 坐标</b>（例如 {@code clock (90, 100, 8)}）；</li>
 *   <li>格子够宽时把<b>数值</b>直接写在格子里（门的电平、红石线强度、导线 1/0）；</li>
 *   <li>底部<b>图例</b>：每种颜色代表什么、0..15 的渐变条。</li>
 * </ol>
 * 另外 {@link ChartSession} 还会写一份同名的 {@code .txt}，逐条列出每次变化，纯文本更好读。
 */
public final class ChartRenderer {
	/** 成图最大宽度（像素）。tick 很多时会把每 tick 压到 1px。 */
	private static final int MAX_WIDTH = 2400;
	private static final int LEFT_MARGIN = 168;
	private static final int RIGHT_MARGIN = 16;
	private static final int TOP_MARGIN = 52;
	private static final int LEGEND_HEIGHT = 64;
	private static final int BOTTOM_MARGIN = 16;
	private static final int ROW_HEIGHT = 12;
	private static final int MAX_ROWS = 160;
	/** 单格宽度上限：够宽才能把数值写进格子。 */
	private static final int MAX_CELL_WIDTH = 28;
	/** 格子里写数值所需的最小宽度。 */
	private static final int MIN_CELL_WIDTH_FOR_NUMBER = 14;

	private static final Color BACKGROUND = new Color(0x14141A);
	private static final Color GRID = new Color(0x2A2A34);
	private static final Color TEXT = new Color(0xE8E8F0);
	private static final Color TEXT_DIM = new Color(0x98A0B0);

	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/** 渲染结果：PNG 字节 + 建议文件名 + 尺寸信息。 */
	public record Chart(byte[] png, String fileName, int width, int height, int rows, int ticks) {
	}

	private ChartRenderer() {
	}

	public static Chart render(ServerWorld world, BlockPos min, BlockPos max, int ticks,
			Map<BlockPos, BlockState> initialStates,
			Map<BlockPos, List<ChartSession.Change>> changesByPosition,
			boolean truncated) {
		List<BlockPos> rows = new ArrayList<>(changesByPosition.keySet());
		rows.sort(ChartSession.positionOrder());
		boolean rowsTruncated = rows.size() > MAX_ROWS;
		if (rowsTruncated) {
			rows = rows.subList(0, MAX_ROWS);
		}

		int cellWidth = Math.max(1, Math.min(MAX_CELL_WIDTH, (MAX_WIDTH - LEFT_MARGIN - RIGHT_MARGIN)
				/ Math.max(1, ticks)));
		int width = LEFT_MARGIN + ticks * cellWidth + RIGHT_MARGIN;
		int legendTop = TOP_MARGIN + Math.max(1, rows.size()) * ROW_HEIGHT + BOTTOM_MARGIN;
		int height = legendTop + LEGEND_HEIGHT;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		graphics.setColor(BACKGROUND);
		graphics.fillRect(0, 0, width, height);

		Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
		Font subtitleFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
		Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
		Font valueFont = new Font(Font.SANS_SERIF, Font.BOLD, 9);

		graphics.setColor(TEXT);
		graphics.setFont(titleFont);
		graphics.drawString("Circuit Redstone - Logic / run-chart", 8, 18);

		graphics.setFont(subtitleFont);
		graphics.setColor(TEXT_DIM);
		String subtitle = String.format("%s -> %s | ticks=%d | rows=%d%s%s",
				min.toShortString(), max.toShortString(), ticks, rows.size(),
				rowsTruncated ? " (row limit " + MAX_ROWS + ")" : "",
				truncated ? " (change list truncated)" : "");
		graphics.drawString(subtitle, 8, 34);
		graphics.setFont(labelFont);
		graphics.drawString("each row = one block, each column = one tick, colour/number = state",
				8, 46);

		int chartTop = TOP_MARGIN;
		int chartLeft = LEFT_MARGIN;
		int chartRight = LEFT_MARGIN + ticks * cellWidth;
		int chartBottom = chartTop + Math.max(1, rows.size()) * ROW_HEIGHT;

		int step = Math.max(1, ticks / 10);
		graphics.setStroke(new BasicStroke(1f));
		for (int tick = 1; tick <= ticks; tick += step) {
			int x = chartLeft + (tick - 1) * cellWidth;
			graphics.setColor(GRID);
			graphics.drawLine(x, chartTop - 4, x, chartBottom + 4);
			graphics.setColor(TEXT_DIM);
			graphics.setFont(labelFont);
			graphics.drawString(String.valueOf(tick), x, chartTop - 8);
		}
		graphics.setColor(GRID);
		graphics.drawLine(chartLeft, chartTop - 4, chartRight, chartTop - 4);

		int y = chartTop;
		for (BlockPos pos : rows) {
			BlockState state = initialStates.getOrDefault(pos, Blocks.AIR.getDefaultState());
			int segmentStart = 1;
			graphics.setFont(labelFont);
			graphics.setColor(TEXT);
			graphics.drawString(rowLabel(pos, state), 8, y + ROW_HEIGHT - 3);

			for (ChartSession.Change change : changesByPosition.getOrDefault(pos, List.of())) {
				if (change.tick() > segmentStart) {
					drawSegment(graphics, chartLeft, y, segmentStart, change.tick() - 1, cellWidth, state,
							valueFont);
				}
				state = change.state();
				segmentStart = Math.max(segmentStart, change.tick());
			}
			if (segmentStart <= ticks) {
				drawSegment(graphics, chartLeft, y, segmentStart, ticks, cellWidth, state, valueFont);
			}

			graphics.setColor(GRID);
			graphics.drawLine(0, y + ROW_HEIGHT - 1, width, y + ROW_HEIGHT - 1);
			y += ROW_HEIGHT;
		}

		if (rows.isEmpty()) {
			graphics.setColor(TEXT_DIM);
			graphics.setFont(subtitleFont);
			graphics.drawString("no block changed in this region during " + ticks + " ticks",
					8, chartTop + 16);
		}

		drawLegend(graphics, legendTop, width, labelFont);

		graphics.dispose();

		byte[] png;
		try {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			ImageIO.write(image, "png", buffer);
			png = buffer.toByteArray();
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.error("[{}] failed to encode chart png", CircuitRedstone.MOD_ID, failure);
			return null;
		}
		String fileName = "circuit-chart-" + FILE_STAMP.format(LocalDateTime.now()) + ".png";
		return new Chart(png, fileName, width, height, rows.size(), ticks);
	}

	private static void drawSegment(Graphics2D graphics, int chartLeft, int y, int fromTick, int toTick,
			int cellWidth, BlockState state, Font valueFont) {
		int x = chartLeft + (fromTick - 1) * cellWidth;
		int segmentWidth = Math.max(1, (toTick - fromTick + 1) * cellWidth);
		graphics.setColor(colorOf(state));
		graphics.fillRect(x, y, segmentWidth, ROW_HEIGHT - 2);

		String value = levelText(state);
		if (value.isEmpty() || cellWidth < MIN_CELL_WIDTH_FOR_NUMBER) {
			return;
		}
		graphics.setFont(valueFont);
		graphics.setColor(contrastText(colorOf(state)));
		for (int tick = fromTick; tick <= toTick; tick++) {
			int cellX = chartLeft + (tick - 1) * cellWidth;
			graphics.drawString(value, cellX + Math.max(1, (cellWidth - textWidth(graphics, value)) / 2),
					y + ROW_HEIGHT - 4);
		}
	}

	private static int textWidth(Graphics2D graphics, String text) {
		return graphics.getFontMetrics().stringWidth(text);
	}

	/** 行标签：方块名（去命名空间）+ 坐标。 */
	private static String rowLabel(BlockPos pos, BlockState state) {
		String name = Registries.BLOCK.getId(state.getBlock()).getPath();
		return name + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
	}

	/** 格子里写的数值（没有数值的方块返回空串）。 */
	static String levelText(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof WireBlock) {
			return state.get(WireBlock.POWERED) ? "1" : "0";
		}
		if (block instanceof CircuitBlock) {
			return String.valueOf(state.get(CircuitBlock.POWER));
		}
		if (state.isOf(Blocks.REDSTONE_WIRE)) {
			return String.valueOf(state.get(RedstoneWireBlock.POWER));
		}
		return "";
	}

	private static Color contrastText(Color background) {
		double luminance = 0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue();
		return luminance > 130 ? new Color(0x101014) : new Color(0xF0F0F4);
	}

	private static void drawLegend(Graphics2D graphics, int top, int width, Font font) {
		graphics.setFont(font);
		graphics.setColor(TEXT_DIM);
		graphics.drawString("legend", 8, top);

		int x = 8;
		int y = top + 6;
		x = legendEntry(graphics, x, y, new Color(0x3A3A44), "wire off (0)");
		x = legendEntry(graphics, x, y, new Color(0x8CF0C8), "wire on (1)");
		x = legendEntry(graphics, x, y, new Color(0x8A8A9A), "data wire");
		x = legendEntry(graphics, x, y, new Color(0xD04040), "data wire error");
		x = legendEntry(graphics, x, y, new Color(0x6A5ACD), "constant");
		x = legendEntry(graphics, x, y, new Color(0x5AC8A0), "string");
		legendEntry(graphics, x, y, new Color(0xC060A8), "colour block");

		// 第二行：变量方块（int / str / null 三种颜色，未设置是灰）。
		int variableX = 8;
		int variableY = y + 12;
		variableX = legendEntry(graphics, variableX, variableY, new Color(0xF0A050), "variable int");
		variableX = legendEntry(graphics, variableX, variableY, new Color(0x50A0F0), "variable str");
		legendEntry(graphics, variableX, variableY, new Color(0x9A7AD0), "variable null");

		int gradientY = top + 38;
		graphics.setColor(TEXT_DIM);
		graphics.drawString("gate / dust level:", 8, gradientY + 9);
		int gradientLeft = 96;
		int gradientWidth = Math.min(180, Math.max(60, width - gradientLeft - 80));
		for (int i = 0; i < gradientWidth; i++) {
			graphics.setColor(levelColor(Math.round(i * SignalProtocol.MAX_LEVEL / (float) gradientWidth)));
			graphics.fillRect(gradientLeft + i, gradientY, 1, 10);
		}
		graphics.setColor(TEXT_DIM);
		graphics.drawString("0", gradientLeft - 8, gradientY + 9);
		graphics.drawString("15", gradientLeft + gradientWidth + 4, gradientY + 9);
		graphics.drawString("other blocks = hashed muted colour   |   near-black = air",
				gradientLeft + gradientWidth + 26, gradientY + 9);
	}

	private static int legendEntry(Graphics2D graphics, int x, int y, Color color, String label) {
		graphics.setColor(color);
		graphics.fillRect(x, y - 8, 9, 9);
		graphics.setColor(TEXT_DIM);
		graphics.drawString(label, x + 12, y + 1);
		return x + 12 + graphics.getFontMetrics().stringWidth(label) + 14;
	}

	/**
	 * 方块状态 → 图表颜色。
	 *
	 * <p>本模组的方块有语义色：导线通电=薄荷、逻辑门按电平渐变、常量=靛蓝、字符串=青绿、
	 * 数据线出错=红、纯色方块=洋红；原版红石线按强度渐变；其余方块按类型散列出一个稳定颜色。
	 */
	static Color colorOf(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof WireBlock) {
			return state.get(WireBlock.POWERED) ? new Color(0x8CF0C8) : new Color(0x3A3A44);
		}
		if (block instanceof CircuitBlock) {
			return levelColor(state.get(CircuitBlock.POWER));
		}
		if (block instanceof ConstantBlock) {
			return new Color(0x6A5ACD);
		}
		if (block instanceof StringValueBlock) {
			return new Color(0x5AC8A0);
		}
		if (block instanceof VariableBlock) {
			// 变量的类型也在 BlockState 里，所以图里能直接按类型分辨。
			return switch (VariableBlock.kindOf(state)) {
				case INT -> new Color(0xF0A050);
				case STR -> new Color(0x50A0F0);
				case NULL -> new Color(0x9A7AD0);
				case UNSET -> new Color(0x606070);
			};
		}
		if (block instanceof DataWireBlock) {
			return state.get(DataWireBlock.ERROR) ? new Color(0xD04040) : new Color(0x8A8A9A);
		}
		if (block instanceof ColorBlock) {
			return new Color(0xC060A8);
		}
		if (state.isOf(Blocks.AIR)) {
			return new Color(0x1C1C22);
		}
		if (state.isOf(Blocks.REDSTONE_WIRE)) {
			int power = state.get(RedstoneWireBlock.POWER);
			return blended(new Color(0x301010), new Color(0xFF4020), power / (float) SignalProtocol.MAX_LEVEL);
		}
		int hash = block.hashCode();
		float hue = ((hash >> 3) & 0xFF) / 255f;
		return Color.getHSBColor(hue, 0.28f, 0.55f);
	}

	private static Color levelColor(int level) {
		return blended(new Color(0x3A3A46), new Color(0xF0E080), SignalProtocol.clamp(level) / (float) SignalProtocol.MAX_LEVEL);
	}

	private static Color blended(Color from, Color to, float ratio) {
		float t = Math.max(0f, Math.min(1f, ratio));
		return new Color(
				(int) (from.getRed() + (to.getRed() - from.getRed()) * t),
				(int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
				(int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t));
	}
}
