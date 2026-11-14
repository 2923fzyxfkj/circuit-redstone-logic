package l.circuit_redstone.logic.cas.block;

import java.util.OptionalInt;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.block.entity.ScannerBlockEntity;
import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.ScannerMath;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 输出任意信号器（Scanner）。
 *
 * <p>沿左右方与上方各检测 15 格（{@link SignalProtocol#MAX_LEVEL}）：
 * <ul>
 *   <li>射线上有<b>常量方块</b>时，直接输出该常量的值（上 &gt; 左 &gt; 右，同向取最近），
 *       不再统计方块数量；</li>
 *   <li>否则统计非空气方块数量，合并规则见 {@link ScannerMath}
 *       （上方有方块则取上方的数量，否则左右取较大者）。</li>
 * </ul>
 *
 * <p>性能：扫描由 {@link ScannerBlockEntity} 每
 * {@link ScannerBlockEntity#SCAN_INTERVAL_TICKS} tick 触发一次，绝不每 tick 扫描；
 * 相邻方块变化时则通过 {@code neighborUpdate} 立刻重算，保证响应速度。
 */
public class ScannerBlock extends CircuitBlock {
	/** 检测距离：与红石最高等级同源，都是 15 格。 */
	public static final int SCAN_RANGE = SignalProtocol.MAX_LEVEL;

	public ScannerBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return ScannerBlockEntity.create(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) {
			return null;
		}
		return (tickWorld, tickPos, tickState, blockEntity) -> {
			if (blockEntity instanceof ScannerBlockEntity scanner) {
				scanner.poll(tickWorld, tickPos, tickState);
			}
		};
	}

	@Override
	protected int computeLevel(CircuitContext context, BlockPos pos, BlockState state) {
		Direction leftDirection = left(state);
		Direction rightDirection = right(state);

		OptionalInt constant = ScannerMath.pickConstant(
				nearestConstant(context, pos, Direction.UP),
				nearestConstant(context, pos, leftDirection),
				nearestConstant(context, pos, rightDirection));
		if (constant.isPresent()) {
			int value = constant.getAsInt();
			// 值域夹取统一由 CircuitBlock#evaluate 完成，这里只负责记录日志。
			if (SignalProtocol.clamp(value) != state.get(POWER)) {
				CircuitRedstone.LOGGER.info("[{}] scanner at {}: constant value {} -> power={}",
						CircuitRedstone.MOD_ID, pos.toShortString(), value, SignalProtocol.clamp(value));
			}
			return value;
		}

		int leftCount = countBlocks(context, pos, leftDirection);
		int rightCount = countBlocks(context, pos, rightDirection);
		int upCount = countBlocks(context, pos, Direction.UP);
		int level = ScannerMath.combine(leftCount, rightCount, upCount);
		if (level != state.get(POWER)) {
			// 电平变化时打印三向计数，便于排查"看起来没方块却有输出"的情况：
			// 射线会数到红石线/导线/火把/水/树叶/墙后等一切非空气方块。
			CircuitRedstone.LOGGER.info("[{}] scanner at {}: left={} right={} up={} -> power={}",
					CircuitRedstone.MOD_ID, pos.toShortString(), leftCount, rightCount, upCount, level);
		}
		return level;
	}

	/** 沿某个方向找最近的常量方块；没有则返回 {@link OptionalInt#empty()}。 */
	private static OptionalInt nearestConstant(CircuitContext context, BlockPos origin, Direction direction) {
		for (int step = 1; step <= SCAN_RANGE; step++) {
			BlockPos cell = origin.offset(direction, step);
			if (context.getBlockState(cell).getBlock() instanceof ConstantBlock) {
				return OptionalInt.of(context.getConstantValue(cell));
			}
		}
		return OptionalInt.empty();
	}

	/** 统计某个方向上 15 格内的非空气方块数量（沙盒里查不到的位置按空气算）。 */
	private static int countBlocks(CircuitContext context, BlockPos origin, Direction direction) {
		int count = 0;
		for (int step = 1; step <= SCAN_RANGE; step++) {
			if (!context.getBlockState(origin.offset(direction, step)).isAir()) {
				count++;
			}
		}
		return count;
	}
}
