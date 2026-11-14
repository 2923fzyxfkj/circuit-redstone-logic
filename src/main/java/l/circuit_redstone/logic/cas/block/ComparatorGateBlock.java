package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.ComparisonMode;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 比较器：把"后方常量"和"右方常量"按"左方红石等级"给出的比较模式做比较，输出一个 bit。
 *
 * <p>端口（沿用本模组的约定：{@code FACING} 指向输出面）：
 * <ul>
 *   <li><b>后方</b>（{@code FACING} 的反面）：第一个操作数 A，必须贴着<b>常量</b>方块；</li>
 *   <li><b>右方</b>：第二个操作数 B，必须贴着<b>常量</b>方块；</li>
 *   <li><b>左方</b>：模式输入，读原版红石等级 1..5（1=小于、2=小于等于、3=等于、
 *       4=大于等于、5=大于）；0 或大于 5 视为无效模式。</li>
 * </ul>
 *
 * <p>比较成立输出高电平 bit（{@link SignalProtocol#MAX_LEVEL}），否则输出
 * {@link SignalProtocol#MIN_LEVEL}；任一侧没有常量、或模式无效时同样输出低电平。
 * 比较用的是常量里保存的原始整数（不夹取到 0..15）。
 */
public class ComparatorGateBlock extends CircuitBlock {
	public ComparatorGateBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected int computeLevel(CircuitContext context, BlockPos pos, BlockState state) {
		int mode = readPort(context, pos, left(state));
		Integer first = constantValue(context, pos.offset(back(state)));
		Integer second = constantValue(context, pos.offset(right(state)));

		int level = SignalProtocol.MIN_LEVEL;
		if (ComparisonMode.isValid(mode) && first != null && second != null
				&& ComparisonMode.test(mode, first, second)) {
			level = SignalProtocol.MAX_LEVEL;
		}

		if (level != state.get(POWER)) {
			// 只在电平变化时打印一次，方便排查"为什么没输出"。
			CircuitRedstone.LOGGER.info("[{}] comparator at {}: mode={} A={} B={} -> power={}",
					CircuitRedstone.MOD_ID, pos.toShortString(), mode, first, second, level);
		}
		return level;
	}

	/** 读某位置的常量值；该位置不是常量方块时返回 null（与"常量值为 0"区分开）。 */
	private static Integer constantValue(CircuitContext context, BlockPos pos) {
		if (!(context.getBlockState(pos).getBlock() instanceof ConstantBlock)) {
			return null;
		}
		return context.getConstantValue(pos);
	}
}
