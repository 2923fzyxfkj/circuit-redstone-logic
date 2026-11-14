package l.circuit_redstone.logic.cas.block;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.entity.ConstantBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.StringValueBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.VariableBlockEntity;
import l.circuit_redstone.logic.cas.logic.DataConversion;
import l.circuit_redstone.logic.cas.logic.VariableKind;
import l.circuit_redstone.logic.cas.logic.VariableWrite;
import l.circuit_redstone.logic.cas.update.CircuitSignals;
import l.circuit_redstone.logic.cas.update.WorldCircuitContext;

/**
 * 数据线方块：把<b>背面</b>的数据方块内容写进<b>正面</b>的数据方块。
 *
 * <ul>
 *   <li>{@code FACING} 指向数据流动方向（正面），背面是 {@code FACING.getOpposite()}，
 *       与原版中继器相反、与本模组逻辑门一致；箭头画在顶面，随 {@code facing} 旋转。</li>
 *   <li>连续摆放的数据线会<b>接力</b>：源值沿链条一直找到真正的数据方块（最多
 *       {@link #MAX_CHAIN_LENGTH} 跳，遇到环就放弃）。只有"正面紧贴数据方块"的那一根负责写入。</li>
 *   <li>转换规则见 {@link DataConversion}：常量↔字符串都支持，只有"字符串内容不是整数却要写进常量"
 *       算冲突，此时本方块进入 {@link #ERROR} 状态、换成错误贴图。</li>
 *   <li>驱动方式是一个自续的计划刻（{@link #TRANSFER_INTERVAL_TICKS} = 1 tick），
 *       不依赖方块实体、也不依赖红石信号；计划刻随区块存档保存，世界重载后继续工作。</li>
 * </ul>
 */
public class DataWireBlock extends Block implements CircuitPart {
	/** 数据流动方向（正面）。 */
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	/** 类型转换失败（字符串内容不是整数却要写进常量）。 */
	public static final BooleanProperty ERROR = BooleanProperty.of("error");
	/** 一条接力链最多穿过多少根数据线（防环、防无限走）。 */
	public static final int MAX_CHAIN_LENGTH = 64;
	/** 传输间隔（tick）：每 tick 一次，串联链路每跳 1 tick 收敛。 */
	public static final int TRANSFER_INTERVAL_TICKS = 1;

	public DataWireBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(ERROR, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, ERROR);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// 箭头背向玩家：玩家看向的方向就是数据流动方向（与逻辑门的摆放手感一致）。
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
	}

	/* ---------------------------------------------------------------- 驱动 */

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (oldState.isOf(state.getBlock())) {
			// 我们自己在改 ERROR 属性时也会走到这里，不必重排计划刻。
			return;
		}
		schedule(world, pos);
	}

	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		transfer(world, pos, state);
		schedule(world, pos);
	}

	private void schedule(World world, BlockPos pos) {
		world.scheduleBlockTick(pos, this, TRANSFER_INTERVAL_TICKS);
	}

	/* ---------------------------------------------------------------- 传输 */

	/** 传输一次：源值从背面沿链找到，写进正面的数据方块（若有）。 */
	public static void transfer(World world, BlockPos pos, BlockState state) {
		if (world.isClient()) {
			return;
		}
		DataConversion.Value source = readSource(world, pos, state);
		if (source == null) {
			setError(world, pos, state, false);
			return;
		}
		writeFront(world, pos, state, source);
	}

	/**
	 * 沿背面方向穿过连续的数据线，找到真正的"源"；没有则返回 null。
	 *
	 * <p>背面的源分两类：
	 * <ol>
	 *   <li><b>数据方块</b>（常量 / 字符串）：搬它的值，按转换规则写进正面；</li>
	 *   <li><b>红石信号</b>（红石线、拉杆、本模组的导线/门的输出面等）：把它的红石等级 0..15
	 *       当成一个常量值写进正面（正面是常量就是该等级，正面是字符串就是它的十进制文本）。
	 *       背面是空气时视为"没有接任何东西"，不写入。</li>
	 * </ol>
	 */
	private static DataConversion.Value readSource(World world, BlockPos pos, BlockState state) {
		Set<BlockPos> visited = new HashSet<>();
		BlockPos cursor = pos;
		BlockState cursorState = state;
		for (int step = 0; step < MAX_CHAIN_LENGTH; step++) {
			if (!visited.add(cursor)) {
				return null;
			}
			Direction back = cursorState.get(FACING).getOpposite();
			BlockPos backPos = cursor.offset(back);
			BlockState backState = world.getBlockState(backPos);
			Block backBlock = backState.getBlock();
			if (backBlock instanceof ConstantBlock) {
				return DataConversion.Value.constant(ConstantBlock.valueAt(world, backPos));
			}
			if (backBlock instanceof StringValueBlock) {
				return DataConversion.Value.text(StringValueBlock.textAt(world, backPos));
			}
			if (backBlock instanceof VariableBlock) {
				// 未设置的变量没有值：当成"这一侧没有源"，不写入也不算错误。
				return VariableBlock.valueAt(world, backPos).orElse(null);
			}
			if (backBlock instanceof DataWireBlock) {
				cursor = backPos;
				cursorState = backState;
				continue;
			}
			if (backState.isAir()) {
				// 没有接任何东西：保持目标原值，不算错误。
				return null;
			}
			// 剩下的都按"红石信号源"处理：把该位置的红石等级当成常量值。
			int level = CircuitSignals.readLevel(WorldCircuitContext.of(world), backPos, back);
			return DataConversion.Value.constant(level);
		}
		return null;
	}

	/** 把源值写进正面的数据方块；正面不是数据方块时只清错误状态。 */
	private static void writeFront(World world, BlockPos pos, BlockState state, DataConversion.Value source) {
		BlockPos frontPos = pos.offset(state.get(FACING));
		BlockState frontState = world.getBlockState(frontPos);
		Block frontBlock = frontState.getBlock();

		if (frontBlock instanceof ConstantBlock) {
			// 目标种类的转换规则只有 DataConversion 一处实现（字符串内容必须是整数）。
			Optional<DataConversion.Value> converted = DataConversion.convert(source, DataConversion.Kind.CONSTANT);
			if (converted.isEmpty() || !(world.getBlockEntity(frontPos) instanceof ConstantBlockEntity target)) {
				setError(world, pos, state, true);
				return;
			}
			int value = converted.get().number();
			if (target.getValue() != value) {
				target.setValue(value);
				world.updateNeighborsAlways(frontPos, frontBlock);
			}
			setError(world, pos, state, false);
			return;
		}

		if (frontBlock instanceof StringValueBlock) {
			Optional<DataConversion.Value> converted = DataConversion.convert(source, DataConversion.Kind.TEXT);
			if (converted.isEmpty() || !(world.getBlockEntity(frontPos) instanceof StringValueBlockEntity target)) {
				setError(world, pos, state, true);
				return;
			}
			String text = converted.get().text();
			if (text.length() > StringValueBlockEntity.MAX_TEXT_LENGTH) {
				setError(world, pos, state, true);
				return;
			}
			if (!target.getText().equals(text)) {
				target.setText(text);
				world.updateNeighborsAlways(frontPos, frontBlock);
			}
			setError(world, pos, state, false);
			return;
		}

		if (frontBlock instanceof VariableBlock) {
			writeVariable(world, pos, state, frontPos, frontState, source);
			return;
		}

		// 正面不是数据方块（例如又是下一根数据线）：这根线不负责写入。
		setError(world, pos, state, false);
	}

	/** 把源值写进正面的变量方块：按变量当前类型转换（未设置时跟随源的类型）。 */
	private static void writeVariable(World world, BlockPos pos, BlockState state, BlockPos frontPos,
			BlockState frontState, DataConversion.Value source) {
		VariableKind kind = VariableBlock.kindOf(frontState);
		Optional<DataConversion.Value> converted = VariableWrite.convert(source, kind);
		if (converted.isEmpty()) {
			// 例如文本 "abc" 要写进 int 变量：类型冲突，和数据线写常量一样红底。
			setError(world, pos, state, true);
			return;
		}
		DataConversion.Value value = converted.get();
		if (!(world.getBlockEntity(frontPos) instanceof VariableBlockEntity target)) {
			setError(world, pos, state, false);
			return;
		}
		if (value.kind() == DataConversion.Kind.TEXT
				&& value.text().length() > VariableBlockEntity.MAX_TEXT_LENGTH) {
			setError(world, pos, state, true);
			return;
		}
		// 目标已经是同样的值：不要每 tick 都去 setBlockState。
		boolean same = switch (value.kind()) {
			case CONSTANT -> target.getNumber() == value.number() && kind == VariableKind.INT;
			case TEXT -> target.getText().equals(value.text()) && kind == VariableKind.STR;
			case NULL -> kind == VariableKind.NULL;
		};
		if (!same && VariableBlock.apply(world, frontPos, frontState, value)) {
			world.updateNeighborsAlways(frontPos, frontState.getBlock());
		}
		setError(world, pos, state, false);
	}

	private static void setError(World world, BlockPos pos, BlockState state, boolean error) {
		if (state.get(ERROR) != error) {
			world.setBlockState(pos, state.with(ERROR, error), Block.NOTIFY_LISTENERS);
		}
	}
}
