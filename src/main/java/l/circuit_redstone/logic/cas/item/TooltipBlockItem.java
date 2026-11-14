package l.circuit_redstone.logic.cas.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/**
 * 带详细说明的方块物品。
 *
 * <p>说明文本全部放在语言文件里，按行拆成
 * {@code tooltip.circuit-redstone_logic.<方块id>.0}、{@code .1} ……，一行一个 Text 组件。
 */
public class TooltipBlockItem extends BlockItem {
	private final String tooltipKey;

	public TooltipBlockItem(Block block, Settings settings, String tooltipKey) {
		super(block, settings);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		Tooltips.appendLines(tooltip, tooltipKey);
	}
}
