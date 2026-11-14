package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.entity.HiddenSignBlockEntity;

/**
 * 隐形告示牌：行为像原版告示牌（能立在地上、存文字），但<b>背景不可见</b>——
 * 方块本身渲染成全透明，只有客户端的方块实体渲染器（{@link l.circuit_redstone.logic.cas.client.HiddenSignRenderer}）
 * 画出来的文字。
 *
 * <p>文字用 {@code /circuit-set <文字>} 写（不需要打开编辑界面）；
 * 文本为空时渲染器会显示斜体灰色的"请输入文本…"占位。
 *
 * <p>{@code FACING} 指向<b>文字正面</b>（朝玩家），与原版告示牌一致。
 */
public class HiddenSignBlock extends Block implements BlockEntityProvider {
	/** 文字正面朝向。 */
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	/** 轮廓：跟原版站立告示牌一样是一根细柱，用来瞄准与放置。 */
	private static final VoxelShape SHAPE = Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);

	public HiddenSignBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// 文字正面朝玩家：FACING = 玩家朝向的反方向（原版告示牌也是这个约定）。
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	/**
	 * <b>没有碰撞箱</b>：玩家可以穿过（和"隐形"一致），也不会被当成台阶站上去。
	 *
	 * <p>为什么不像原版告示牌那样做细柱碰撞：细柱顶面就是一个可站立平面，
	 * 站在一个<b>看不见</b>的方块上会非常莫名其妙，所以这里直接不挡人，
	 * 只保留 {@link #getOutlineShape()} 用于瞄准/放置。
	 */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return HiddenSignBlockEntity.create(pos, state);
	}

	/**
	 * 写入文字并同步给客户端（渲染器每帧读方块实体，服务端改完必须把数据发过去）。
	 *
	 * <p>为什么不是 {@code world.updateListeners(...)}：那只触发"方块状态"的客户端刷新，
	 * 方块实体里的文字不会跟着过去，表现就是"指令说写成功了，牌子上还是占位提示"。
	 *
	 * @return 该位置不是隐形告示牌（没有对应方块实体）时返回 false
	 */
	public static boolean setText(World world, BlockPos pos, String text) {
		if (!(world.getBlockEntity(pos) instanceof HiddenSignBlockEntity sign)) {
			return false;
		}
		sign.setText(text);
		sign.syncToClients();
		return true;
	}

	/** 读取某位置的文字；不是隐形告示牌时返回空串。 */
	public static String textAt(BlockView world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof HiddenSignBlockEntity sign ? sign.getText() : "";
	}
}
