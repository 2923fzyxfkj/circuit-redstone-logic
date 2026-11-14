package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.ModRegistry;

/**
 * 字符串方块的方块实体：保存一段文本（{@code /circuit-set} 写入）。
 *
 * <p>长度上限 {@link #MAX_TEXT_LENGTH}：聊天栏单条消息就是 256 字符，
 * 超过这个长度的文本既没法一次显示也没法一次写回，所以在写入端就截断。
 */
public class StringValueBlockEntity extends BlockEntity {
	private static final String NBT_TEXT = "text";
	/** 文本长度上限（与聊天栏单条消息长度同源）。 */
	public static final int MAX_TEXT_LENGTH = 256;

	private String text = "";

	protected StringValueBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** 方块实体工厂。 */
	public static StringValueBlockEntity create(BlockPos pos, BlockState state) {
		return new StringValueBlockEntity(ModRegistry.STRING_VALUE_BLOCK_ENTITY, pos, state);
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
		markDirty();
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		setText(nbt.getString(NBT_TEXT));
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		nbt.putString(NBT_TEXT, this.text);
	}
}
