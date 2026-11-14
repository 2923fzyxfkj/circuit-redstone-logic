package l.circuit_redstone.logic.cas.update;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.ConstantBlock;
import l.circuit_redstone.logic.cas.logic.CircuitContext;

/**
 * 真实世界的 {@link CircuitContext} 实现：所有查询都直接落到 {@link World}。
 *
 * <p>只能在服务端主线程使用（{@code World} 不是线程安全的）；
 * 沙盒推演请用 {@code SimulationSandbox}。
 */
public final class WorldCircuitContext implements CircuitContext {
	private final World world;

	private WorldCircuitContext(World world) {
		this.world = world;
	}

	public static WorldCircuitContext of(World world) {
		return new WorldCircuitContext(world);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return world.getBlockState(pos);
	}

	@Override
	public int getVanillaLevel(BlockPos emitterPos, Direction directionFromReaderToEmitter) {
		return world.getEmittedRedstonePower(emitterPos, directionFromReaderToEmitter);
	}

	@Override
	public int getVanillaWeakLevel(BlockPos emitterPos, Direction directionFromReaderToEmitter) {
		return world.getBlockState(emitterPos).getWeakRedstonePower(world, emitterPos, directionFromReaderToEmitter);
	}

	@Override
	public int getLightLevel(BlockPos pos) {
		return world.getLightLevel(pos);
	}

	@Override
	public int getConstantValue(BlockPos pos) {
		return ConstantBlock.valueAt(world, pos);
	}
}
