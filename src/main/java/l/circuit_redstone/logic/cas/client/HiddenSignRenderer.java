package l.circuit_redstone.logic.cas.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

import l.circuit_redstone.logic.cas.block.HiddenSignBlock;
import l.circuit_redstone.logic.cas.block.entity.HiddenSignBlockEntity;

/**
 * 隐形告示牌的渲染器：<b>只画文字</b>，不画任何背景板。
 *
 * <p>为什么不用原版告示牌：原版会把木板/字面一起画出来，"隐形"就无从谈起；
 * 这里用 {@link BlockEntityRenderer} 自己排版文字，于是方块本体完全透明、只剩字。
 *
 * <p>排版规则：
 * <ul>
 *   <li>文字贴在方块正面和背面各画一次（两面都能看）；</li>
 *   <li>按宽度自动换行，最多 {@link #MAX_LINES} 行；</li>
 *   <li>文字为空时显示斜体灰色的"请输入文本…"占位，避免放下后什么都看不见。</li>
 * </ul>
 */
public class HiddenSignRenderer implements BlockEntityRenderer<HiddenSignBlockEntity> {
	/** 渲染缩放：原版告示牌文字约 0.02（1/50）方块大小。 */
	private static final float TEXT_SCALE = 0.03F;
	/** 行高（缩放前的像素值）。 */
	private static final float LINE_HEIGHT = 10.0F;
	/** 一行最多多宽（缩放前的像素值）。 */
	private static final float MAX_LINE_WIDTH = 60.0F;
	/** 最多显示几行。 */
	private static final int MAX_LINES = 4;
	/** 正常文字颜色：白色（背景是透明的，黑字在暗处看不见）。 */
	private static final int TEXT_COLOR = 0xFFFFFF;
	/** 占位文字颜色：灰色。 */
	private static final int PLACEHOLDER_COLOR = 0xAAAAAA;
	/** 文字离方块表面的距离（沿法线）。必须 ≥ 0.5：这个方块是完整立方体，字面就在 0.5 处，
	 *  0.4375 会把文字埋进方块内部、被方块面挡住。 */
	private static final double SURFACE_OFFSET = 0.501;

	public HiddenSignRenderer(BlockEntityRendererFactory.Context context) {
		// 一次性日志：排查"只有方块没有字"时，先确认渲染器到底有没有被创建。
		l.circuit_redstone.logic.cas.CircuitRedstone.LOGGER.info("[{}] hidden sign renderer created",
				l.circuit_redstone.logic.cas.CircuitRedstone.MOD_ID);
	}

	@Override
	public void render(HiddenSignBlockEntity entity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof HiddenSignBlock)) {
			return;
		}
		Direction facing = state.get(HiddenSignBlock.FACING);
		String raw = entity.getText();
		boolean placeholder = raw.isBlank();
		TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
		List<Text> lines = placeholder
				? List.of(Text.translatable("block.circuit-redstone_logic.hidden_sign.placeholder")
						.formatted(Formatting.ITALIC))
				: wrap(renderer, raw);
		int color = placeholder ? PLACEHOLDER_COLOR : TEXT_COLOR;

		// 正反两面各画一次：站在哪边都能看到文字。
		for (int side = 0; side < 2; side++) {
			matrices.push();
			matrices.translate(0.5, 0.9, 0.5);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation() + (side == 0 ? 0.0F : 180.0F)));
			matrices.translate(0.0, 0.0, -SURFACE_OFFSET);
			matrices.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
			for (int index = 0; index < lines.size(); index++) {
				Text line = lines.get(index);
				float width = renderer.getWidth(line);
				renderer.draw(line, -width / 2.0F, index * LINE_HEIGHT, color, false,
						matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL,
						0, light);
			}
			matrices.pop();
		}
	}

	/** 按显示宽度把文本切成最多 {@link #MAX_LINES} 行；显式 {@code \n} 也当换行。 */
	private static List<Text> wrap(TextRenderer renderer, String text) {
		List<Text> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int index = 0; index < text.length() && lines.size() < MAX_LINES; index++) {
			char character = text.charAt(index);
			if (character == '\n') {
				lines.add(Text.literal(current.toString()));
				current.setLength(0);
				continue;
			}
			String candidate = current.toString() + character;
			if (!current.isEmpty() && renderer.getWidth(candidate) > MAX_LINE_WIDTH) {
				lines.add(Text.literal(current.toString()));
				current.setLength(0);
				if (lines.size() >= MAX_LINES) {
					break;
				}
			}
			current.append(character);
		}
		if (!current.isEmpty() && lines.size() < MAX_LINES) {
			lines.add(Text.literal(current.toString()));
		}
		if (lines.isEmpty()) {
			lines.add(Text.literal(""));
		}
		return lines;
	}
}
