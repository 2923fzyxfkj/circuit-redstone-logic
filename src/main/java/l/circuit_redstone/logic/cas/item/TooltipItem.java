package l.circuit_redstone.logic.cas.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/**
 * 带详细说明的纯物品（例如电路终端）。
 *
 * <p>说明文本放在语言文件里，按行拆成 {@code tooltip.circuit-redstone_logic.<物品id>.0}、{@code .1} ……
 */
public class TooltipItem extends Item {
	private final String tooltipKey;

	public TooltipItem(Settings settings, String tooltipKey) {
		super(settings);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		Tooltips.appendLines(tooltip, tooltipKey);
	}
}
