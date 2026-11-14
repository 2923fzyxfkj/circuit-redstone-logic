package l.circuit_redstone.logic.cas.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.logic.ExchangeRule;

/**
 * 非管理员可用的 {@code /circuit-terminal} 指令。
 *
 * <p>只有背包里带着"电路终端"（{@code circuit-redstone_logic:terminal}）的玩家才能使用：
 * <ul>
 *   <li>{@code /circuit-terminal list}：列出本模组全部方块/物品（名称: id，动态获取）；</li>
 *   <li>{@code /circuit-terminal get <id>}：扣除 4 个导线，给予 1 个 {@code circuit-redstone_logic:<id>}。</li>
 * </ul>
 *
 * <p>规则与防漏洞：
 * <ol>
 *   <li>禁止"终端换终端"——那等于用 4 个导线刷出无限终端，直接拒绝；</li>
 *   <li>允许 {@code get wire} 这个彩蛋：扣 4 个导线、再给 1 个导线；</li>
 *   <li>扣除导线<b>之前</b>先模拟"扣完再放入"的背包状态，放不下就直接拒绝，
 *       避免物品掉地上或凭空消失。</li>
 * </ol>
 */
public final class CircuitTerminalCommand {
	private static final String ARG_ID = "id";
	private static final String ARG_AMOUNT = "amount";

	private CircuitTerminalCommand() {
	}

	/** 在 {@code onInitialize()} 中调用一次。 */
	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("circuit-terminal")
				.then(CommandManager.literal("list")
						.executes(CircuitTerminalCommand::list))
				.then(CommandManager.literal("get")
						.then(CommandManager.argument(ARG_ID, StringArgumentType.word())
								// 不写数量 = 买一批（普通方块 1 个、悬空/纯色 16 个），与旧行为一致。
								.executes(context -> get(context, 0))
								.then(CommandManager.argument(ARG_AMOUNT, IntegerArgumentType.integer(1))
										.executes(context -> get(context,
												IntegerArgumentType.getInteger(context, ARG_AMOUNT)))))));
	}

	/* ---------------------------------------------------------------- list */

	private static int list(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = requireTerminalHolder(source);
		if (player == null) {
			return 0;
		}

		List<Identifier> ids = ModRegistry.registeredItemIds().stream()
				.sorted(Comparator.comparing(Identifier::toString))
				.toList();
		for (Identifier id : ids) {
			Item item = Registries.ITEM.get(id);
			// 名称用可翻译文本发送，客户端会自动显示本地化名称（服务端拿不到翻译）。
			source.sendFeedback(() -> Text.translatable(item.getTranslationKey())
					.append(Text.literal(": " + id)), false);
		}
		return ids.size();
	}

	/* ---------------------------------------------------------------- get */

	/**
	 * 兑换。
	 *
	 * @param requested 玩家要的数量；{@code 0} 表示"不写数量"，按一批算
	 */
	private static int get(CommandContext<ServerCommandSource> context, int requested) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = requireTerminalHolder(source);
		if (player == null) {
			return 0;
		}

		String rawId = StringArgumentType.getString(context, ARG_ID);
		Identifier id = parseOwnId(rawId);
		Item item = id == null ? null : Registries.ITEM.getOrEmpty(id).orElse(null);
		if (item == null) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.terminal.unknown",
					id == null ? rawId : id.toString()));
			return 0;
		}

		// 防漏洞 1：绝对不能"终端兑换终端"（等于用 4 个导线刷无限终端）。
		if (item == ModRegistry.TERMINAL) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.terminal.exchange_terminal"));
			return 0;
		}

		String path = id.getPath();
		int perBatch = ExchangeRule.perBatch(path);
		int amount = requested <= 0 ? perBatch : requested;

		// 数量必须按整批买：普通方块的"一批"就是 1 个，任何正整数都合法；
		// 悬空方块 / 纯色方块一批 16 个，就必须买 16、32、48……
		if (amount < perBatch) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.terminal.amount_too_small",
					String.valueOf(perBatch), String.valueOf(perBatch)));
			return 0;
		}
		if (!ExchangeRule.isValidAmount(path, amount)) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.terminal.amount_not_multiple",
					String.valueOf(perBatch)));
			return 0;
		}

		int wireCost = ExchangeRule.costFor(path, amount);
		int wireCount = countWires(player);
		if (wireCount < wireCost) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.terminal.need_wires",
					String.valueOf(wireCost), String.valueOf(wireCount)));
			return 0;
		}

		ItemStack reward = new ItemStack(item, amount);

		// 防漏洞 2：先模拟"扣导线 + 放奖品"之后的空间，放不下就绝不扣导线。
		if (!fitsAfterExchange(player, item, wireCost, amount)) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.terminal.inventory_full"));
			return 0;
		}

		removeWires(player, wireCost);
		if (!player.getInventory().insertStack(reward)) {
			// 理论上不会走到这里（上面已检查容量）；兜底也不吞物品。
			player.dropItem(reward, false);
		}
		source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.terminal.granted",
				String.valueOf(amount), Text.translatable(item.getTranslationKey()),
				String.valueOf(ExchangeRule.WIRE_COST)), false);
		return 1;
	}

	/**
	 * 解析参数：允许写 {@code wire}，也允许写 {@code circuit-redstone_logic:wire}；
	 * 只接受本模组的 id（其它命名空间返回 null）。
	 */
	private static Identifier parseOwnId(String rawId) {
		Identifier id;
		if (rawId.indexOf(':') >= 0) {
			try {
				id = new Identifier(rawId);
			} catch (RuntimeException e) {
				return null;
			}
		} else {
			id = CircuitRedstone.id(rawId);
		}
		return CircuitRedstone.MOD_ID.equals(id.getNamespace()) ? id : null;
	}

	/* ---------------------------------------------------------------- 背包 */

	/**
	 * 校验并返回玩家：必须由玩家执行，且背包里带着电路终端。
	 *
	 * @return 通过校验的玩家；否则已发送错误信息并返回 null
	 */
	private static ServerPlayerEntity requireTerminalHolder(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.terminal.players_only"));
			return null;
		}
		if (!hasTerminal(player)) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.terminal.need_terminal"));
			return null;
		}
		return player;
	}

	private static boolean hasTerminal(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			if (inventory.getStack(slot).isOf(ModRegistry.TERMINAL)) {
				return true;
			}
		}
		return false;
	}

	private static int countWires(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		int count = 0;
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isOf(ModRegistry.WIRE.asItem())) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void removeWires(ServerPlayerEntity player, int amount) {
		PlayerInventory inventory = player.getInventory();
		int remaining = amount;
		for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isOf(ModRegistry.WIRE.asItem())) {
				continue;
			}
			int taken = Math.min(remaining, stack.getCount());
			stack.decrement(taken);
			remaining -= taken;
			if (stack.isEmpty()) {
				inventory.setStack(slot, ItemStack.EMPTY);
			}
		}
		inventory.markDirty();
	}

	/**
	 * 模拟"扣除 {@code wireCost} 个导线，再放入 {@code amount} 个 {@code rewardItem}"之后是否放得下。
	 *
	 * <p>只操作背包快照，不会改动真实背包。判定范围与 {@link PlayerInventory#insertStack(ItemStack)}
	 * 一致：主背包 36 格 + 副手 1 格（护甲格不算可用空间）；数量按"先填同类堆、再占空格"模拟，
	 * 所以 16 个一批的兑换不会因为只检查"一个物品"而误判放得下。
	 */
	private static boolean fitsAfterExchange(ServerPlayerEntity player, Item rewardItem, int wireCost, int amount) {
		PlayerInventory inventory = player.getInventory();
		List<ItemStack> snapshot = new ArrayList<>();
		for (ItemStack stack : inventory.main) {
			snapshot.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		for (ItemStack stack : inventory.offHand) {
			snapshot.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}

		Item wireItem = ModRegistry.WIRE.asItem();
		int remaining = wireCost;
		for (ItemStack stack : snapshot) {
			if (remaining <= 0) {
				break;
			}
			if (stack.isEmpty() || !stack.isOf(wireItem)) {
				continue;
			}
			int taken = Math.min(remaining, stack.getCount());
			stack.decrement(taken);
			remaining -= taken;
		}
		if (remaining > 0) {
			return false;
		}

		return simulateInsert(snapshot, rewardItem, amount);
	}

	/** 在背包快照上模拟插入 {@code amount} 个物品：先填同类堆，再占空格。 */
	private static boolean simulateInsert(List<ItemStack> snapshot, Item item, int amount) {
		int maxCount = item.getMaxCount();
		int remaining = amount;
		for (ItemStack stack : snapshot) {
			if (remaining <= 0) {
				return true;
			}
			if (stack.isEmpty() || !stack.isOf(item) || stack.getCount() >= maxCount) {
				continue;
			}
			int put = Math.min(maxCount - stack.getCount(), remaining);
			stack.setCount(stack.getCount() + put);
			remaining -= put;
		}
		for (int index = 0; index < snapshot.size() && remaining > 0; index++) {
			ItemStack stack = snapshot.get(index);
			if (!stack.isEmpty()) {
				continue;
			}
			int put = Math.min(maxCount, remaining);
			snapshot.set(index, new ItemStack(item, put));
			remaining -= put;
		}
		return remaining <= 0;
	}
}
