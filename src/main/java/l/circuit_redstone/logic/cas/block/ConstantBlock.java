package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import l.circuit_redstone.logic.cas.block.entity.ConstantBlockEntity;

/**
 * 常量方块：保存一个整数，供"输出任意信号器"（{@link ScannerBlock}）取值输出。
 *
 * <p><b>自身不输出任何红石信号</b>（也不与导线电气连接），它只是数据容器：
 * 用 {@code /circuit-set <数字>} 写入，扫描器在左/右/上射线上看到它时，
 * 输出的就是它保存的值（而不是方块数量）。
 */
public class ConstantBlock extends CircuitValueBlock {
	public ConstantBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return ConstantBlockEntity.create(pos, state);
	}

	/**
	 * 读取某位置的常量值。
	 *
	 * @return 该位置不是常量方块（没有对应方块实体）时返回 0
	 */
	public static int valueAt(BlockView world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof ConstantBlockEntity constant ? constant.getValue() : 0;
	}
}
