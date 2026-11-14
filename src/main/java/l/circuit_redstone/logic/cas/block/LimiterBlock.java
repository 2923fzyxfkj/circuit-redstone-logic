package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.LevelUtil;

/**
 * 限制信号器：后方输入 A，左右方输入 B（左优先），输出 {@code min(A, B)}。
 *
 * <p>输入按 0..15 电平处理：导线只会给出 0/15，原版红石线可以给出中间值，
 * 因此在纯导线电路里它就等价于 AND 门，在模拟电路里则表现为"取较小者"。
 */
public class LimiterBlock extends CircuitBlock {
	public LimiterBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected int computeLevel(CircuitContext context, BlockPos pos, BlockState state) {
		int a = readPort(context, pos, back(state));
		int b = LevelUtil.selectSecondary(
				readPort(context, pos, left(state)),
				readPort(context, pos, right(state)));
		return LevelUtil.limit(a, b);
	}
}
