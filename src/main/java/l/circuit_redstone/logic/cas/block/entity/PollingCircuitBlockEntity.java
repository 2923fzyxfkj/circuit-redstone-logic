package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.CircuitBlock;

/**
 * "需要周期性重算"的电路方块的公共方块实体。
 *
 * <p>亮度传感器和扫描器都属于这类：它们读取的是**周围环境**（光照等级 / 空间方块数量），
 * 而环境变化不会触发相邻更新，所以必须靠计时器主动重扫；
 * 但绝不能每 tick 都扫，所以统一在这里做"每 {@code intervalTicks} tick 才刷新一次"。
 */
public abstract class PollingCircuitBlockEntity extends CircuitBlockEntity {
	private final int intervalTicks;
	private int ticksUntilPoll;

	protected PollingCircuitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int intervalTicks) {
		super(type, pos, state);
		this.intervalTicks = Math.max(1, intervalTicks);
		this.ticksUntilPoll = this.intervalTicks;
	}

	/** 由方块的 {@code getTicker} 每 tick 调用；只有计时到点才真正重算一次。 */
	public void poll(World world, BlockPos pos, BlockState state) {
		if (world.isClient()) {
			return;
		}
		if (--this.ticksUntilPoll > 0) {
			return;
		}
		this.ticksUntilPoll = this.intervalTicks;
		if (state.getBlock() instanceof CircuitBlock circuitBlock) {
			circuitBlock.refresh(world, pos, state);
		}
	}
}
