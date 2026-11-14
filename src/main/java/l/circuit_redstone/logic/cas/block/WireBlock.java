package l.circuit_redstone.logic.cas.block;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import l.circuit_redstone.logic.cas.logic.SignalProtocol;
import l.circuit_redstone.logic.cas.update.CircuitUpdates;

/**
 * 导线方块：传递二进制信号（0/1），不衰减，六向可连，并能与原版红石线互相连接。
 *
 * <p>连接关系（NORTH/EAST/SOUTH/WEST/UP/DOWN 六个布尔属性）只用于渲染与状态展示，
 * 电气连通性由相邻关系决定：相邻的导线、原版红石线、以及本模组电路方块的水平端口都算连接。
 * 与原版红石线的信号转换完全依赖 {@link SignalProtocol}：
 * 本方块供电时对外提供 {@code DAC(1) = 15}，读取原版红石线时用 {@code ADC(level>0) = 1}。
 */
public class WireBlock extends Block implements CircuitPart {
	/** 方向 → 连接属性。 */
	public static final Map<Direction, BooleanProperty> CONNECTIONS;
	/** 是否带电。 */
	public static final BooleanProperty POWERED = BooleanProperty.of("powered");

	static {
		Map<Direction, BooleanProperty> connections = new EnumMap<>(Direction.class);
		connections.put(Direction.NORTH, BooleanProperty.of("north"));
		connections.put(Direction.EAST, BooleanProperty.of("east"));
		connections.put(Direction.SOUTH, BooleanProperty.of("south"));
		connections.put(Direction.WEST, BooleanProperty.of("west"));
		connections.put(Direction.UP, BooleanProperty.of("up"));
		connections.put(Direction.DOWN, BooleanProperty.of("down"));
		CONNECTIONS = Collections.unmodifiableMap(connections);
	}

	private static final VoxelShape CORE_SHAPE = Block.createCuboidShape(6.0, 6.0, 6.0, 10.0, 10.0, 10.0);

	public WireBlock(Settings settings) {
		super(settings);
		BlockState defaultState = getDefaultState().with(POWERED, false);
		for (BooleanProperty connection : CONNECTIONS.values()) {
			defaultState = defaultState.with(connection, false);
		}
		setDefaultState(defaultState);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
		for (BooleanProperty connection : CONNECTIONS.values()) {
			builder.add(connection);
		}
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		World world = ctx.getWorld();
		BlockPos pos = ctx.getBlockPos();
		BlockState state = getDefaultState();
		for (Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
			Direction direction = entry.getKey();
			state = state.with(entry.getValue(), connectsTo(direction, world.getBlockState(pos.offset(direction))));
		}
		return state;
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		BooleanProperty connection = CONNECTIONS.get(direction);
		if (connection == null) {
			return state;
		}
		return state.with(connection, connectsTo(direction, neighborState));
	}

	/**
	 * @param direction     从本导线指向邻居的方向
	 * @param neighborState 邻居状态
	 */
	private static boolean connectsTo(Direction direction, BlockState neighborState) {
		Block neighbor = neighborState.getBlock();
		if (neighbor instanceof WireBlock) {
			return true;
		}
		if (neighborState.isOf(Blocks.REDSTONE_WIRE)) {
			return true;
		}
		if (neighbor instanceof CircuitBlock) {
			// 邻居朝向本导线的面 = direction.getOpposite()
			return CircuitBlock.canConnectWireOn(direction.getOpposite());
		}
		return false;
	}

	/* ---------------------------------------------------------------- 供电 */

	/**
	 * 导线对外只提供弱电：与原版红石线一致地"只影响相邻方块"，不做强电穿透，
	 * 避免隔着实心方块把信号漏到别的电路上。
	 */
	@Override
	public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		return state.get(POWERED) ? SignalProtocol.wireToRedstone(SignalProtocol.WIRE_HIGH) : SignalProtocol.MIN_LEVEL;
	}

	@Override
	public boolean emitsRedstonePower(BlockState state) {
		return true;
	}

	/* ---------------------------------------------------------------- 形状 */

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return CORE_SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	/* ---------------------------------------------------------------- 更新 */

	@Override
	public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
		// 只入队；整张网络的求值在 CircuitUpdates 的 flush 里完成。
		CircuitUpdates.markDirty(world, pos);
	}

	/**
	 * 与原版红石线耦合的网络会给自己安排周期性的计划刻（见 {@code WireNetwork#recalculate}）。
	 * 计划刻随区块存档保留，所以世界重载后链条依然有效。
	 */
	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		CircuitUpdates.markDirty(world, pos);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		// 我们自己在整网求值时也会写 POWERED，那种"同方块属性变化"不需要再触发一轮求值。
		if (oldState.isOf(state.getBlock())) {
			return;
		}
		CircuitUpdates.markDirty(world, pos);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		// 导线自身没有方块实体，但这条调用链是通用的：父类负责清理方块实体，别漏。
		super.onStateReplaced(state, world, pos, newState, moved);
		if (moved || state.isOf(newState.getBlock())) {
			return;
		}
		CircuitUpdates.markDirty(world, pos);
		for (Direction direction : Direction.values()) {
			CircuitUpdates.markDirty(world, pos.offset(direction));
		}
	}
}
