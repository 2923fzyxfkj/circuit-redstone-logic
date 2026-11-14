package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.entity.CircuitBlockEntity;
import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;
import l.circuit_redstone.logic.cas.update.CircuitSignals;
import l.circuit_redstone.logic.cas.update.CircuitUpdates;
import l.circuit_redstone.logic.cas.update.WorldCircuitContext;

/**
 * 所有"电路方块"（逻辑门 / 限制信号器 / 扫描器）的公共基类。
 *
 * <p>端口约定：
 * <ul>
 *   <li>{@code FACING} 指向 <b>输出面</b>（与原版中继器相反，原版 FACING 指向输入面）；</li>
 *   <li>后方（{@code FACING.getOpposite()}）是输入 A；</li>
 *   <li>左 / 右（绕 Y 轴旋转）是输入 B，左右都有时左优先。</li>
 * </ul>
 *
 * <p>状态：{@link #POWER} 是 0..15 的输出电平，写进 BlockState，因此客户端自动同步、
 * 亮度可以直接由它驱动；BlockEntity 里保存同一份电平作为权威副本。
 */
public abstract class CircuitBlock extends Block implements BlockEntityProvider, CircuitPart {
	/** 输出方向（输出面）。 */
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	/** 输出电平 0..15。 */
	public static final IntProperty POWER = IntProperty.of("power", SignalProtocol.MIN_LEVEL, SignalProtocol.MAX_LEVEL);

	protected CircuitBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(POWER, SignalProtocol.MIN_LEVEL).with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(POWER, FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// 输出背向玩家：玩家看向的方向就是输出方向（与原版中继器的摆放手感一致）。
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return CircuitBlockEntity.create(pos, state);
	}

	/**
	 * 计算本方块此刻应该输出的电平（0..15）。
	 *
	 * <p>只依赖 {@link CircuitContext}：真实世界传世界视图，调试沙盒传内存快照，
	 * 因此"电路逻辑"只有这一份实现。
	 */
	protected abstract int computeLevel(CircuitContext context, BlockPos pos, BlockState state);

	/** 纯求值：不写方块，返回应输出的电平。沙盒推演与真实世界共用。 */
	public final int evaluate(CircuitContext context, BlockPos pos, BlockState state) {
		return SignalProtocol.clamp(computeLevel(context, pos, state));
	}

	/**
	 * 重新求值并写回电平。这是本类唯一的状态写入口，保证 BlockState 与 BlockEntity 不失步。
	 *
	 * @return 电平是否发生变化
	 */
	public final boolean refresh(World world, BlockPos pos, BlockState state) {
		if (world.isClient()) {
			return false;
		}
		int newLevel = evaluate(WorldCircuitContext.of(world), pos, state);
		CircuitBlockEntity blockEntity = world.getBlockEntity(pos) instanceof CircuitBlockEntity circuit
				? circuit
				: null;
		int previousLevel = blockEntity != null ? blockEntity.getLevel() : state.get(POWER);
		if (blockEntity != null) {
			blockEntity.setLevel(newLevel);
		}
		if (newLevel == previousLevel && newLevel == state.get(POWER)) {
			return false;
		}
		// 只发客户端通知，不触发原版邻居级联；随后定点通知邻居，避免同 tick 递归更新。
		world.setBlockState(pos, state.with(POWER, newLevel), Block.NOTIFY_LISTENERS);
		world.updateNeighborsAlways(pos, this);
		world.updateComparators(pos, this);
		return true;
	}

	/* ---------------------------------------------------------------- 供电 */

	/**
	 * 直接把某个电平写到方块上，不重新求值。
	 *
	 * <p>给"电平由自己决定"的方块用（例如时钟的输出由它自己的计时器驱动）。
	 * 与 {@link #refresh} 一样只发客户端通知 + 定点通知邻居，避免邻居级联递归。
	 *
	 * @return 电平是否发生变化
	 */
	public final boolean writeLevel(World world, BlockPos pos, BlockState state, int level) {
		if (world.isClient()) {
			return false;
		}
		int clamped = SignalProtocol.clamp(level);
		if (world.getBlockEntity(pos) instanceof CircuitBlockEntity blockEntity) {
			blockEntity.setLevel(clamped);
		}
		if (state.get(POWER) == clamped) {
			return false;
		}
		world.setBlockState(pos, state.with(POWER, clamped), Block.NOTIFY_LISTENERS);
		world.updateNeighborsAlways(pos, this);
		world.updateComparators(pos, this);
		return true;
	}

	/**
	 * 本方块朝某个面提供的电平。
	 *
	 * @param emitterFaceTowardReceiver 本方块朝向接收方的那个面
	 */
	public static int powerForFace(BlockState state, Direction emitterFaceTowardReceiver) {
		return emitterFaceTowardReceiver == state.get(FACING) ? state.get(POWER) : 0;
	}

	/**
	 * 原版约定：{@code direction} 由读取方指向发射方，因此本方块朝向读取方的面是 {@code direction.getOpposite()}。
	 */
	@Override
	public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		return powerForFace(state, direction.getOpposite());
	}

	@Override
	public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		return powerForFace(state, direction.getOpposite());
	}

	@Override
	public boolean emitsRedstonePower(BlockState state) {
		return true;
	}

	@Override
	public boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return state.get(POWER);
	}

	/* ---------------------------------------------------------------- 端口 */

	/** 输入 A 所在方向（后方）。 */
	public static Direction back(BlockState state) {
		return state.get(FACING).getOpposite();
	}

	/** 输入 B 的左侧方向。 */
	public static Direction left(BlockState state) {
		return state.get(FACING).rotateYCounterclockwise();
	}

	/** 输入 B 的右侧方向。 */
	public static Direction right(BlockState state) {
		return state.get(FACING).rotateYClockwise();
	}

	/** 读取某个端口上的信号电平。 */
	protected final int readPort(CircuitContext context, BlockPos pos, Direction directionFromThisBlock) {
		return CircuitSignals.readLevel(context, pos.offset(directionFromThisBlock), directionFromThisBlock);
	}

	/** 导线能否连到本方块的该面（水平四面为端口）。 */
	public static boolean canConnectWireOn(Direction faceTowardWire) {
		return faceTowardWire.getAxis().isHorizontal();
	}

	/* ---------------------------------------------------------------- 更新 */

	@Override
	public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
		// 只入队，不在回调里计算或写方块。
		CircuitUpdates.markDirty(world, pos);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		// 同一方块只是属性变化（例如我们自己在 refresh 里写 POWER）时不必重新入队；
		// 真正需要通知邻居的部分已经在 refresh 里显式做过。
		if (oldState.isOf(state.getBlock())) {
			return;
		}
		CircuitUpdates.markDirty(world, pos);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		// 必须先走父类：AbstractBlock#onStateReplaced 是"移除方块实体"的唯一入口
		// （state.hasBlockEntity() && 方块变了 → world.removeBlockEntity）。
		// 漏掉它会在方块被拆掉后留下孤儿方块实体，日志刷
		// "Block entity ... state Block{minecraft:air} invalid for ticking"，并写进存档。
		super.onStateReplaced(state, world, pos, newState, moved);
		// 同一方块的属性变化（例如 POWER 更新）不算替换，避免自激。
		if (moved || state.isOf(newState.getBlock())) {
			return;
		}
		CircuitUpdates.markDirty(world, pos);
		for (Direction direction : Direction.values()) {
			CircuitUpdates.markDirty(world, pos.offset(direction));
		}
	}
}
