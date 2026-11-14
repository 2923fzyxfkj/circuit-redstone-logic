package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 信号增强器（Signal Amplifier）：输入激活（电平 &gt; 0）就输出满强度 15，<b>零游戏刻延迟</b>。
 *
 * <p>它做的事情就是 {@code DAC(ADC(输入))}：输入端（后方）只要有任何强度，输出面立刻给 15。
 * 对比原版中继器（1~4 红石刻延迟），它在同一游戏刻内收敛。
 */
public class AmplifierBlock extends CircuitBlock {
	public AmplifierBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected int computeLevel(CircuitContext context, BlockPos pos, BlockState state) {
		int inputLevel = readPort(context, pos, back(state));
		return SignalProtocol.wireToRedstone(SignalProtocol.redstoneToWire(inputLevel));
	}
}
