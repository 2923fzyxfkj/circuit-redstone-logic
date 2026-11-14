package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * 悬空方块：看上去只是个"白线框"，但功能上算一个<b>完整方块</b>。
 *
 * <ul>
 *   <li><b>能托东西</b>：{@link #getSidesShape} 返回完整立方体，所以红石粉、火把、压力板等
 *       "下面必须有支撑"的方块可以放在它上面（判定走
 *       {@code BlockState#isSideSolidFullSquare} → {@code SideShapeType.FULL} → 这个形状，
 *       与碰撞形状无关）；</li>
 *   <li><b>玩家可以穿过</b>：{@link #getCollisionShape} 是空的，走路/掉落都不会被它挡住；</li>
 *   <li><b>看起来是镂空的</b>：只画一圈白线（客户端用 cutout 渲染层，中间像素真透明），
 *       并且不遮挡相邻方块的面（{@link #getCullingShape} 返回空）；</li>
 *   <li><b>不参与电路逻辑</b>：它不导电、不存数据，只是"垫块 + 装饰"。</li>
 * </ul>
 *
 * <p>它故意<b>不是</b> {@code nonOpaque}：不透明属性是红石粉能被放置的前提之一
 * （另一个就是上面的 sides shape），镂空只体现在贴图与渲染层上。
 */
public class HoverBlock extends Block {
	/** 轮廓：完整立方体（便于瞄准与放置，也让上方方块"有支撑"）。 */
	private static final VoxelShape OUTLINE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

	public HoverBlock(Settings settings) {
		super(settings);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return OUTLINE;
	}

	/** 玩家可以穿过去：碰撞形状为空。 */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	/**
	 * <b>"能否在我上面放东西"的判定形状</b>：给完整立方体。
	 *
	 * <p>这是这个方块能做"垫块"的关键：红石粉 / 火把 / 压力板判断下方有没有支撑时，
	 * 走的是 {@code BlockState#isSideSolidFullSquare} → {@code SideShapeType.FULL}
	 * → {@code BlockState#getSidesShape} → 这里的 {@code Block#getSidesShape}，
	 * <b>而不是</b>碰撞形状。所以碰撞可以为空（玩家穿过），支撑判定照样成立。
	 */
	@Override
	public VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
		return OUTLINE;
	}

	/** 不遮挡相邻方块的面：否则中间镂空处会露出被剔除掉的背面，看起来像破洞。 */
	@Override
	public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
		return VoxelShapes.empty();
	}
}
