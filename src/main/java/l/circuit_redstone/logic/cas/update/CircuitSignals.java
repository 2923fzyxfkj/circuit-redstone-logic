package l.circuit_redstone.logic.cas.update;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import l.circuit_redstone.logic.cas.block.CircuitBlock;
import l.circuit_redstone.logic.cas.block.WireBlock;
import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 统一的"从某个方块读取信号电平"入口。
 *
 * <p>只依赖 {@link CircuitContext}，因此真实世界（{@link WorldCircuitContext}）与
 * 调试沙盒（{@code SimulationSandbox}）共用同一套判定，不会出现两份会互相跑偏的逻辑。
 *
 * <p>方向参数沿用原版约定：由读取方指向发射方；发射方朝读取方的面是 {@code direction.getOpposite()}。
 */
public final class CircuitSignals {
	private CircuitSignals() {
	}

	/** 读取发射方提供的电平（完整版，含实心方块强电）。电路方块读取端口用这个。 */
	public static int readLevel(CircuitContext context, BlockPos emitterPos, Direction directionFromReaderToEmitter) {
		BlockState emitterState = context.getBlockState(emitterPos);

		if (emitterState.getBlock() instanceof WireBlock) {
			return emitterState.get(WireBlock.POWERED)
					? SignalProtocol.wireToRedstone(SignalProtocol.WIRE_HIGH)
					: SignalProtocol.MIN_LEVEL;
		}
		if (emitterState.getBlock() instanceof CircuitBlock) {
			return readCircuitLevel(emitterState, directionFromReaderToEmitter);
		}
		if (emitterState.isOf(Blocks.REDSTONE_WIRE)) {
			return SignalProtocol.clamp(emitterState.get(RedstoneWireBlock.POWER));
		}
		return SignalProtocol.clamp(context.getVanillaLevel(emitterPos, directionFromReaderToEmitter));
	}

	/** 读取弱电电平（不做实心方块强电放大）。导线网络判定外部信号源用这个。 */
	public static int readWeakSourceLevel(CircuitContext context, BlockPos emitterPos,
			Direction directionFromReaderToEmitter) {
		BlockState emitterState = context.getBlockState(emitterPos);

		if (emitterState.getBlock() instanceof WireBlock) {
			return emitterState.get(WireBlock.POWERED)
					? SignalProtocol.wireToRedstone(SignalProtocol.WIRE_HIGH)
					: SignalProtocol.MIN_LEVEL;
		}
		if (emitterState.getBlock() instanceof CircuitBlock) {
			return readCircuitLevel(emitterState, directionFromReaderToEmitter);
		}
		if (emitterState.isOf(Blocks.REDSTONE_WIRE)) {
			return SignalProtocol.clamp(emitterState.get(RedstoneWireBlock.POWER));
		}
		return SignalProtocol.clamp(context.getVanillaWeakLevel(emitterPos, directionFromReaderToEmitter));
	}

	private static int readCircuitLevel(BlockState emitterState, Direction directionFromReaderToEmitter) {
		Direction emitterFaceTowardReader = directionFromReaderToEmitter.getOpposite();
		return SignalProtocol.clamp(CircuitBlock.powerForFace(emitterState, emitterFaceTowardReader));
	}
}
