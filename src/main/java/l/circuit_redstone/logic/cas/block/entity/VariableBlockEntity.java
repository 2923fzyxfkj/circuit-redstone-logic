package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.logic.VariableKind;

/**
 * 变量方块的方块实体：保存"一个变量"的完整内容（类型 + 整数 + 文本）。
 *
 * <p>为什么三个字段都存：类型要跟着物品走（挖掉后的掉落物要能原样放回去），
 * 而整数与文本是两种类型的载荷，只有一个是有效的；两个都存可以让玩家在同类型之间
 * 反复改值时不丢数据，也让 NBT 结构固定、便于 {@code copy_nbt} 复制。
 *
 * <p>BlockState 里的 {@code kind} 是同一份类型的镜像（客户端要靠它换贴图）；
 * 写入统一走 {@code VariableBlock#apply}，那里会同时更新两者。
 */
public class VariableBlockEntity extends BlockEntity {
	/** NBT 键：类型（{@code unset} / {@code int} / {@code str}）。 */
	public static final String NBT_KIND = "kind";
	/** NBT 键：整数值。 */
	public static final String NBT_NUMBER = "number";
	/** NBT 键：文本值。 */
	public static final String NBT_TEXT = "text";
	/** 文本长度上限，与字符串方块保持一致（单条聊天消息长度）。 */
	public static final int MAX_TEXT_LENGTH = StringValueBlockEntity.MAX_TEXT_LENGTH;

	private VariableKind kind = VariableKind.UNSET;
	private int number;
	private String text = "";

	protected VariableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** 方块实体工厂。 */
	public static VariableBlockEntity create(BlockPos pos, BlockState state) {
		return new VariableBlockEntity(ModRegistry.VARIABLE_BLOCK_ENTITY, pos, state);
	}

	public VariableKind getKind() {
		return kind;
	}

	public int getNumber() {
		return number;
	}

	public String getText() {
		return text;
	}

	/** 一次性写入三种字段（超长文本在这里截断）。 */
	public void setValue(VariableKind kind, int number, String text) {
		this.kind = kind;
		this.number = number;
		this.text = text == null ? "" : (text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text);
		markDirty();
	}

	/** 复制成物品用的 NBT（不含 id/坐标，那些由 {@code BlockItem.setBlockEntityNbt} 补）。 */
	public NbtCompound toItemNbt() {
		NbtCompound nbt = new NbtCompound();
		writeNbt(nbt);
		return nbt;
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		setValue(VariableKind.byId(nbt.getString(NBT_KIND)), nbt.getInt(NBT_NUMBER), nbt.getString(NBT_TEXT));
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		nbt.putString(NBT_KIND, kind.id());
		nbt.putInt(NBT_NUMBER, number);
		nbt.putString(NBT_TEXT, text);
	}
}
