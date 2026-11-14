package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.GateKind;
import l.circuit_redstone.logic.cas.logic.LevelUtil;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 逻辑门方块（AND / OR / NOT / XOR 共用一个类，由 {@link GateKind} 区分）。
 *
 * <p>输入 A：后方（输出面的对面）。输入 B：左右两侧，左侧优先。
 * 两个输入先各自经过 ADC（>0 → 1）参与布尔运算，输出再经 DAC（0/1 → 0/15）。
 */
public class LogicGateBlock extends CircuitBlock {
	private final GateKind kind;

	public LogicGateBlock(GateKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	@Override
	protected int computeLevel(CircuitContext context, BlockPos pos, BlockState state) {
		boolean a = SignalProtocol.isWireHigh(
				SignalProtocol.redstoneToWire(readPort(context, pos, back(state))));
		boolean b = false;
		if (kind.usesSecondaryInput()) {
			int sideLevel = LevelUtil.selectSecondary(
					readPort(context, pos, left(state)),
					readPort(context, pos, right(state)));
			b = SignalProtocol.isWireHigh(SignalProtocol.redstoneToWire(sideLevel));
		}
		return SignalProtocol.wireToRedstone(SignalProtocol.wireFromBoolean(kind.apply(a, b)));
	}
}
