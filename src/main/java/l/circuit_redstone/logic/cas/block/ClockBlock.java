package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.entity.ClockBlockEntity;
import l.circuit_redstone.logic.cas.logic.CircuitContext;

/**
 * 时钟：用 {@code /circuit-set <tick>} 设置频率，之后每 {@code <tick>} 个 tick
 * 向<b>输出面</b>（{@code FACING}）输出一个 tick 的高电平。
 *
 * <p>与其他电路方块不同，时钟的输出来自它自己的计时器，而不是邻居求值：
 * {@link #computeLevel} 直接返回当前电平，所以被邻居刷新时不会被清掉。
 * 计时由 {@link ClockBlockEntity} 每 tick 推进，周期与倒计时都存进方块实体，世界重载后继续。
 */
public class ClockBlock extends CircuitBlock {
	public ClockBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return ClockBlockEntity.create(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) {
			return null;
		}
		return (tickWorld, tickPos, tickState, blockEntity) -> {
			if (blockEntity instanceof ClockBlockEntity clock) {
				clock.tick(tickWorld, tickPos, tickState);
			}
		};
	}

	@Override
	protected int computeLevel(CircuitContext context, BlockPos pos, BlockState state) {
		// 输出由自己的计时器决定：这里返回当前电平，保证被刷新时不被覆盖。
		return state.get(POWER);
	}
}
