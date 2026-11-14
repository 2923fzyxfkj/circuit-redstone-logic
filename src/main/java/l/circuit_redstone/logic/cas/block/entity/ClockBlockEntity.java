package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.block.ClockBlock;
import l.circuit_redstone.logic.cas.logic.ClockTiming;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 时钟的方块实体：保存频率（tick）与倒计时。
 *
 * <p>每 tick 推进一次：倒计时归零的那一 tick 输出高电平，其余时间输出低电平；
 * 周期与倒计时都写进 NBT，所以世界重载后时钟从上次的相位继续走。
 */
public class ClockBlockEntity extends CircuitBlockEntity {
	public static final String NBT_PERIOD = "period";
	public static final String NBT_COUNTDOWN = "countdown";

	/** 频率（tick）；0 表示还没设置。 */
	private int period;
	private int countdown;

	protected ClockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** 方块实体工厂。 */
	public static ClockBlockEntity create(BlockPos pos, BlockState state) {
		return new ClockBlockEntity(ModRegistry.CLOCK_BLOCK_ENTITY, pos, state);
	}

	public int getPeriodTicks() {
		return period;
	}

	/** 设置频率（tick），并把倒计时重置到新周期。 */
	public void setPeriodTicks(int periodTicks) {
		this.period = periodTicks;
		this.countdown = periodTicks;
		markDirty();
	}

	public boolean hasPeriod() {
		return ClockTiming.isValidPeriod(period);
	}

	/** 由方块的 {@code getTicker} 每 tick 调用。 */
	public void tick(World world, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof ClockBlock clock)) {
			return;
		}
		if (!hasPeriod()) {
			clock.writeLevel(world, pos, state, SignalProtocol.MIN_LEVEL);
			return;
		}
		boolean pulse = ClockTiming.isPulseTick(countdown);
		countdown = ClockTiming.nextCountdown(countdown, period);
		clock.writeLevel(world, pos, state, pulse ? SignalProtocol.MAX_LEVEL : SignalProtocol.MIN_LEVEL);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		this.period = nbt.getInt(NBT_PERIOD);
		this.countdown = nbt.contains(NBT_COUNTDOWN) ? nbt.getInt(NBT_COUNTDOWN) : this.period;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putInt(NBT_PERIOD, this.period);
		nbt.putInt(NBT_COUNTDOWN, this.countdown);
	}
}
