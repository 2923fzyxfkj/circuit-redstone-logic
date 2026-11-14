package l.circuit_redstone.logic.cas.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.block.VariableBlock;
import l.circuit_redstone.logic.cas.block.entity.VariableBlockEntity;
import l.circuit_redstone.logic.cas.logic.VariableKind;

/**
 * 变量方块的物品：多两件事——显示"当前值"，以及放置后把数据写回方块。
 *
 * <p>为什么恢复要自己做：原版 {@code BlockItem#postPlacement} 只会把 {@code BlockEntityTag}
 * 写进方块实体，而变量方块的<b>类型在 BlockState 里</b>（客户端靠它换贴图）。
 * 所以这里在数据写回之后，再把 {@code kind} 同步进 BlockState，放下即可见正确的贴图。
 */
public class VariableBlockItem extends TooltipBlockItem {
	public VariableBlockItem(Block block, Settings settings, String tooltipKey) {
		super(block, settings, tooltipKey);
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		NbtCompound nbt = BlockItem.getBlockEntityNbt(stack);
		if (nbt == null) {
			return;
		}
		VariableKind kind = VariableKind.byId(nbt.getString(VariableBlockEntity.NBT_KIND));
		if (!kind.isSet()) {
			return;
		}
		String value = switch (kind) {
			case INT -> Integer.toString(nbt.getInt(VariableBlockEntity.NBT_NUMBER));
			case STR -> "\"" + nbt.getString(VariableBlockEntity.NBT_TEXT) + "\"";
			// null 没有载荷，直接写 null。
			case NULL -> "null";
			case UNSET -> "";
		};
		tooltip.add(Text.translatable("tooltip." + CircuitRedstone.MOD_ID + ".variable.current", value)
				.formatted(Formatting.AQUA));
	}

	@Override
	protected boolean postPlacement(BlockPos pos, World world, PlayerEntity player, ItemStack stack,
			BlockState state) {
		boolean placed = super.postPlacement(pos, world, player, stack, state);
		if (world.isClient) {
			return placed;
		}
		NbtCompound nbt = BlockItem.getBlockEntityNbt(stack);
		if (nbt == null || !(world.getBlockEntity(pos) instanceof VariableBlockEntity variable)) {
			return placed;
		}
		// 自己读一遍：不依赖 BlockEntityTag 里是否有 id 字段（掉落物是 loot 生成的）。
		variable.readNbt(nbt);
		world.setBlockState(pos, state.with(VariableBlock.KIND, variable.getKind()), Block.NOTIFY_LISTENERS);
		return placed;
	}
}
