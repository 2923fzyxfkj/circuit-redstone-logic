package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.entity.LightSensorBlockEntity;
import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 亮度传感器：读取<b>自身所在位置</b>的光照等级（0..15），输出同强度的红石信号。
 *
 * <p>光照等级是原版的"方块亮度"，即天空光与方块光取较大者
 * （天空光会按当前时间/天气的暗化程度折算）。
 *
 * <p>光照变化不会触发相邻更新，因此输出由
 * {@link LightSensorBlockEntity} 每 {@link LightSensorBlockEntity#POLL_INTERVAL_TICKS} tick 轮询一次；
 * 相邻方块变化时仍会通过 {@code neighborUpdate} 立刻重算。
 */
public class BrightnessSensorBlock extends CircuitBlock {
	public BrightnessSensorBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return LightSensorBlockEntity.create(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) {
			return null;
		}
		return (tickWorld, tickPos, tickState, blockEntity) -> {
			if (blockEntity instanceof LightSensorBlockEntity sensor) {
				sensor.poll(tickWorld, tickPos, tickState);
			}
		};
	}

	/**
	 * 输出 = 周围（自身 + 六个相邻位置）的最高光照等级。
	 *
	 * <p>传感器本身是<b>遮光</b>的实心方块，所以自身位置的光照恒为 0，
	 * 真实读数来自它挨着的那些空间：露天空气 = 15、火把旁 = 14、被方块包住 = 0。
	 * 如果把方块做成透明（{@code nonOpaque}），天光会从传感器身上漏进去，
	 * 于是"被结构包住"的位置也会读到 13/14，这正是之前的错误行为。
	 */
	@Override
	protected int computeLevel(CircuitContext context, BlockPos pos, BlockState state) {
		int brightest = context.getLightLevel(pos);
		for (Direction direction : Direction.values()) {
			brightest = Math.max(brightest, context.getLightLevel(pos.offset(direction)));
		}
		return SignalProtocol.clamp(brightest);
	}
}
