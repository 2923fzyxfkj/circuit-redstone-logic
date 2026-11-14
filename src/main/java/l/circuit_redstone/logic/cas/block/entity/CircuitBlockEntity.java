package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 电路方块的方块实体：保存输出电平（0..15）。
 *
 * <p>BlockState 里的 {@code POWER} 是同一份数据的镜像（用于客户端同步、亮度、比较器），
 * 这里保存的才是权威副本；{@code CircuitBlock#refresh} 是唯一同时更新两者的地方。
 */
public class CircuitBlockEntity extends BlockEntity {
	private static final String NBT_LEVEL = "level";

	private int level = SignalProtocol.MIN_LEVEL;

	protected CircuitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** 方块实体工厂（{@code BlockEntityType.Builder} 需要 (BlockPos, BlockState) 形式）。 */
	public static CircuitBlockEntity create(BlockPos pos, BlockState state) {
		return new CircuitBlockEntity(ModRegistry.CIRCUIT_BLOCK_ENTITY, pos, state);
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = SignalProtocol.clamp(level);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		this.level = SignalProtocol.clamp(nbt.getInt(NBT_LEVEL));
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		nbt.putInt(NBT_LEVEL, this.level);
	}
}
