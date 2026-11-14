package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;

/**
 * "数据方块"的公共基类：常量（数值）与字符串（文本）。
 *
 * <p>它们<b>不输出任何红石信号</b>，只是可以被 {@code /circuit-set} 写入、被扫描器读取的
 * 数据容器；之所以要有公共基类，是为了让"当前这块是不是本模组的电路方块"这类判断
 * （调试指令的全量快照、物品分组等）有一处统一入口。
 */
public abstract class CircuitValueBlock extends Block implements BlockEntityProvider, CircuitPart {
	protected CircuitValueBlock(Settings settings) {
		super(settings);
	}

	/**
	 * 放置时不需要朝向：数据方块没有端口，永远用默认状态。
	 * 覆盖这里是为了显式声明"无 FACING"，避免以后误加旋转逻辑。
	 */
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState();
	}

	@Override
	public abstract BlockEntity createBlockEntity(BlockPos pos, BlockState state);
}
