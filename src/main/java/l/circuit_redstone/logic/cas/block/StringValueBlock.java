package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import l.circuit_redstone.logic.cas.block.entity.StringValueBlockEntity;

/**
 * 字符串方块：保存一段文本，用 {@code /circuit-set <字符串>} 写入。
 *
 * <p>目前<b>不参与红石逻辑</b>（不会被扫描器当成常量、不输出信号），
 * 只是"可写的文本数据方块"，为后续字符串运算方块留出数据来源。
 */
public class StringValueBlock extends CircuitValueBlock {
	public StringValueBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return StringValueBlockEntity.create(pos, state);
	}

	/**
	 * 读取某位置保存的文本。
	 *
	 * @return 该位置不是字符串方块时返回空串
	 */
	public static String textAt(BlockView world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof StringValueBlockEntity stringValue ? stringValue.getText() : "";
	}
}
