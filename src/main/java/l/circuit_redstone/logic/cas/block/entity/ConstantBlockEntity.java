package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.ModRegistry;

/**
 * 常量方块的方块实体：只保存一个整数（{@code /circuit-set} 写入，扫描器读取）。
 *
 * <p>值域是任意 int；输出时的 0..15 夹取由 {@code CircuitBlock#evaluate} 统一完成，
 * 所以这里不做范围限制，避免"写入时被悄悄改小"。
 */
public class ConstantBlockEntity extends BlockEntity {
	private static final String NBT_VALUE = "value";

	private int value;

	protected ConstantBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** 方块实体工厂。 */
	public static ConstantBlockEntity create(BlockPos pos, BlockState state) {
		return new ConstantBlockEntity(ModRegistry.CONSTANT_BLOCK_ENTITY, pos, state);
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
		markDirty();
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		this.value = nbt.getInt(NBT_VALUE);
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		nbt.putInt(NBT_VALUE, this.value);
	}
}
