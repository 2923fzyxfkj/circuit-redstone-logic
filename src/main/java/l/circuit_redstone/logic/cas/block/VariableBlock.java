package l.circuit_redstone.logic.cas.block;

import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.block.entity.VariableBlockEntity;
import l.circuit_redstone.logic.cas.logic.DataConversion;
import l.circuit_redstone.logic.cas.logic.VariableKind;

/**
 * 变量方块：一个方块存<b>一个变量</b>，类型是 int 或 str，用
 * {@code /circuit-set int=42} / {@code /circuit-set str="hi"} 写入。
 *
 * <p>与常量/字符串方块的区别：
 * <ul>
 *   <li><b>类型可变</b>：同一个方块这一刻是整数、下一刻可以是文本；</li>
 *   <li><b>数据线双向</b>：既能当源被数据线读走，也能被数据线写入；</li>
 *   <li><b>跟着物品走</b>：挖掉掉落的物品带着完整数据，重新放下后原样恢复。</li>
 * </ul>
 *
 * <p>它<b>不发射红石信号</b>（和常量/字符串一样只是数据容器），扫描器也不读它。
 *
 * <p>类型放在 BlockState 属性 {@link #KIND} 里，客户端据此换贴图；数值放在方块实体里。
 * 两者必须一起改，所以统一走 {@link #apply}。
 */
public class VariableBlock extends CircuitValueBlock {
	/** 变量类型（{@code unset} / {@code int} / {@code str}），同时决定贴图。 */
	public static final EnumProperty<VariableKind> KIND = EnumProperty.of("kind", VariableKind.class);

	public VariableBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(KIND, VariableKind.UNSET));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(KIND);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return VariableBlockEntity.create(pos, state);
	}

	/** 读取某个位置的变量类型（不是变量方块时返回 {@link VariableKind#UNSET}）。 */
	public static VariableKind kindOf(BlockState state) {
		return state.get(KIND);
	}

	/**
	 * 读取某个位置的变量值。
	 *
	 * @return 未设置（或不是变量方块）时返回 empty —— 数据线把它当成"这一侧没有源"；
	 *         {@code null} 是<b>有值</b>的，返回 {@link DataConversion.Value#nullValue()}
	 */
	public static Optional<DataConversion.Value> valueAt(BlockView world, BlockPos pos) {
		if (!(world.getBlockEntity(pos) instanceof VariableBlockEntity variable)) {
			return Optional.empty();
		}
		return switch (variable.getKind()) {
			case UNSET -> Optional.empty();
			case NULL -> Optional.of(DataConversion.Value.nullValue());
			case INT -> Optional.of(DataConversion.Value.constant(variable.getNumber()));
			case STR -> Optional.of(DataConversion.Value.text(variable.getText()));
		};
	}

	/**
	 * 把值写进变量，并同步 BlockState 的类型（贴图立刻变）。
	 *
	 * @return 内容是否真的发生了变化（调用方据此决定要不要通知邻居）
	 */
	public static boolean apply(World world, BlockPos pos, BlockState state, DataConversion.Value value) {
		if (!(world.getBlockEntity(pos) instanceof VariableBlockEntity variable)) {
			return false;
		}
		VariableKind kind = switch (value.kind()) {
			case CONSTANT -> VariableKind.INT;
			case TEXT -> VariableKind.STR;
			case NULL -> VariableKind.NULL;
		};
		boolean changed = switch (kind) {
			case INT -> variable.getKind() != VariableKind.INT || variable.getNumber() != value.number();
			case STR -> variable.getKind() != VariableKind.STR || !variable.getText().equals(value.text());
			case NULL -> variable.getKind() != VariableKind.NULL;
			// apply 的入参不会是"未设置"，UNSET 走不到这里。
			case UNSET -> false;
		};
		if (!changed) {
			return false;
		}
		switch (kind) {
			case INT -> variable.setValue(kind, value.number(), "");
			case STR -> variable.setValue(kind, 0, value.text());
			// null 没有载荷：整数与文本都清掉，避免残留旧值被误读。
			case NULL -> variable.setValue(kind, 0, "");
			case UNSET -> {
			}
		}
		BlockState updated = state.with(KIND, kind);
		if (updated != state) {
			// 只发客户端通知：类型变化只是贴图，不需要原版邻居级联。
			world.setBlockState(pos, updated, Block.NOTIFY_LISTENERS);
		}
		return true;
	}

	/**
	 * 中键取方块（创造模式）也带数据：用一个带 {@code BlockEntityTag} 的物品代替空物品。
	 */
	@Override
	public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
		ItemStack stack = new ItemStack(ModRegistry.VARIABLE);
		if (world.getBlockEntity(pos) instanceof VariableBlockEntity variable && variable.getKind().isSet()) {
			BlockItem.setBlockEntityNbt(stack, ModRegistry.VARIABLE_BLOCK_ENTITY, variable.toItemNbt());
		}
		return stack;
	}
}
