package l.circuit_redstone.logic.cas.command;

import java.util.OptionalInt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.block.ClockBlock;
import l.circuit_redstone.logic.cas.block.ColorBlock;
import l.circuit_redstone.logic.cas.block.ConstantBlock;
import l.circuit_redstone.logic.cas.block.HiddenSignBlock;
import l.circuit_redstone.logic.cas.block.StringValueBlock;
import l.circuit_redstone.logic.cas.block.VariableBlock;
import l.circuit_redstone.logic.cas.block.entity.ClockBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.ColorBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.ConstantBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.HiddenSignBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.StringValueBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.VariableBlockEntity;
import l.circuit_redstone.logic.cas.logic.ClockTiming;
import l.circuit_redstone.logic.cas.logic.ColorHex;
import l.circuit_redstone.logic.cas.logic.DataConversion;
import l.circuit_redstone.logic.cas.logic.VariableAssignment;

/**
 * 数据方块写入指令：{@code /circuit-set <值>}。
 *
 * <p>三种用法（同一个指令，靠目标方块决定值怎么解释）：
 * <ul>
 *   <li>{@code /circuit-set 7}：准星指着<b>常量</b>方块时写入整数 7（扫描器会输出它）；</li>
 *   <li>{@code /circuit-set hello}：准星指着<b>字符串</b>方块时写入文本 {@code hello}；</li>
 *   <li>{@code /circuit-set #12AB34}：准星指着<b>纯色方块</b>时写入十六进制颜色。</li>
 * </ul>
 *
 * <p>另外提供 {@code /circuit-set <x> <y> <z> <值>} 的坐标形式：控制台/命令方块没有准星，
 * 也需要能写入（服务端自测就靠它）。
 */
public final class CircuitSetCommand {
	private static final String ARG_POS = "pos";
	private static final String ARG_VALUE = "value";

	private CircuitSetCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		// 注意顺序：Brigadier 的子节点按插入顺序尝试（1.2.9 用 LinkedHashMap）。
		// 坐标形式必须先注册，否则 "/circuit-set 30 100 1 7" 会被 greedyString 整串吃掉，
		// 变成"瞄准写入值 30 100 1 7"。
		dispatcher.register(CommandManager.literal("circuit-set")
				// 坐标形式（控制台/命令方块可用）
				.then(CommandManager.argument(ARG_POS, BlockPosArgumentType.blockPos())
						.then(CommandManager.argument(ARG_VALUE, StringArgumentType.greedyString())
								.executes(CircuitSetCommand::setAtPos)))
				// 准星形式
				.then(CommandManager.argument(ARG_VALUE, StringArgumentType.greedyString())
						.executes(CircuitSetCommand::setAimed)));
	}

	private static int setAimed(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.set.players_only"));
			return 0;
		}
		BlockPos target = CommandTargets.aimedBlock(player);
		if (target == null) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.set.no_target"));
			return 0;
		}
		return apply(source, target, StringArgumentType.getString(context, ARG_VALUE));
	}

	private static int setAtPos(CommandContext<ServerCommandSource> context) {
		BlockPos target = BlockPosArgumentType.getBlockPos(context, ARG_POS);
		return apply(context.getSource(), target, StringArgumentType.getString(context, ARG_VALUE));
	}

	/** 按目标方块类型解释 {@code raw}：常量要整数，字符串直接用原文。 */
	private static int apply(ServerCommandSource source, BlockPos pos, String raw) {
		ServerWorld world = source.getWorld();
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		if (block instanceof ConstantBlock) {
			int value;
			try {
				value = Integer.parseInt(raw.trim());
			} catch (NumberFormatException invalid) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.bad_number", raw));
				return 0;
			}
			if (!(world.getBlockEntity(pos) instanceof ConstantBlockEntity constant)) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.not_settable"));
				return 0;
			}
			constant.setValue(value);
			notifyNeighbors(world, pos, block);
			source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.set.ok",
					Text.translatable(block.getTranslationKey()),
					Text.literal(pos.toShortString()),
					String.valueOf(value)), false);
			return 1;
		}

		if (block instanceof ClockBlock) {
			int period;
			try {
				period = Integer.parseInt(raw.trim());
			} catch (NumberFormatException invalid) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.bad_period", raw));
				return 0;
			}
			if (!ClockTiming.isValidPeriod(period)) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.bad_period", raw));
				return 0;
			}
			if (!(world.getBlockEntity(pos) instanceof ClockBlockEntity clock)) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.not_settable"));
				return 0;
			}
			clock.setPeriodTicks(period);
			notifyNeighbors(world, pos, block);
			source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.set.clock",
					Text.translatable(block.getTranslationKey()),
					Text.literal(pos.toShortString()),
					String.valueOf(period)), false);
			return 1;
		}

		if (block instanceof StringValueBlock) {
			if (raw.length() > StringValueBlockEntity.MAX_TEXT_LENGTH) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.too_long",
						String.valueOf(StringValueBlockEntity.MAX_TEXT_LENGTH)));
				return 0;
			}
			if (!(world.getBlockEntity(pos) instanceof StringValueBlockEntity stringValue)) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.not_settable"));
				return 0;
			}
			stringValue.setText(raw);
			notifyNeighbors(world, pos, block);
			source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.set.ok",
					Text.translatable(block.getTranslationKey()),
					Text.literal(pos.toShortString()),
					"\"".concat(raw).concat("\"")), false);
			return 1;
		}

		if (block instanceof HiddenSignBlock) {
			// /circuit-set "" 表示清空（重新显示"请输入文本…"占位）。
			String text = raw.equals("\"\"") ? "" : raw;
			if (text.length() > HiddenSignBlockEntity.MAX_TEXT_LENGTH) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.too_long",
						String.valueOf(HiddenSignBlockEntity.MAX_TEXT_LENGTH)));
				return 0;
			}
			if (!HiddenSignBlock.setText(world, pos, text)) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.not_settable"));
				return 0;
			}
			String shown = text.isEmpty() ? "\"\"" : "\"" + text + "\"";
			source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.set.ok",
					Text.translatable(block.getTranslationKey()),
					Text.literal(pos.toShortString()),
					shown), false);
			return 1;
		}

		if (block instanceof VariableBlock) {
			return setVariable(source, world, pos, block, state, raw);
		}

		if (block instanceof ColorBlock) {
			OptionalInt rgb = ColorHex.parse(raw);
			if (rgb.isEmpty()) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.bad_color", raw));
				return 0;
			}
			if (!(world.getBlockEntity(pos) instanceof ColorBlockEntity color)) {
				source.sendError(Text.translatable("command.circuit-redstone_logic.set.not_settable"));
				return 0;
			}
			color.setColor(rgb.getAsInt());
			color.syncToClients();
			ColorBlock.requestClientRefresh(world, pos);
			String colorText = ColorHex.format(rgb.getAsInt());
			source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.set.ok",
					Text.translatable(block.getTranslationKey()),
					Text.literal(pos.toShortString()),
					colorText), false);
			return 1;
		}

		source.sendError(Text.translatable("command.circuit-redstone_logic.set.not_settable"));
		return 0;
	}

	/** 值改变后通知四周：扫描器只在"自己变脏"时重算，而数据方块的值变化不在 BlockState 里，
	 * 不会自动触发相邻更新，所以这里显式通知一次。 */
	private static void notifyNeighbors(ServerWorld world, BlockPos pos, Block block) {
		world.updateNeighborsAlways(pos, block);
	}

	/**
	 * 变量方块：{@code /circuit-set int=42} 或 {@code /circuit-set str="hi"}。
	 *
	 * <p>语法解析在 {@link VariableAssignment}（纯逻辑、可单测），这里只做"翻译成聊天栏消息"。
	 */
	private static int setVariable(ServerCommandSource source, ServerWorld world, BlockPos pos, Block block,
			BlockState state, String raw) {
		VariableAssignment.Result parsed = VariableAssignment.parse(raw);
		if (parsed instanceof VariableAssignment.Failure failure) {
			source.sendError(switch (failure.error()) {
				case "need_equals" -> Text.translatable(
						"command.circuit-redstone_logic.set.variable_need_equals", failure.detail());
				case "bad_kind" -> Text.translatable(
						"command.circuit-redstone_logic.set.variable_bad_kind", failure.detail(),
						VariableAssignment.names());
				case "need_quotes" -> Text.translatable(
						"command.circuit-redstone_logic.set.variable_need_quotes", failure.detail());
				case "null_takes_no_value" -> Text.translatable(
						"command.circuit-redstone_logic.set.variable_null_takes_no_value", failure.detail());
				default -> Text.translatable("command.circuit-redstone_logic.set.bad_number", failure.detail());
			});
			return 0;
		}
		DataConversion.Value value = ((VariableAssignment.Success) parsed).value();
		if (value.kind() == DataConversion.Kind.TEXT
				&& value.text().length() > VariableBlockEntity.MAX_TEXT_LENGTH) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.set.too_long",
					String.valueOf(VariableBlockEntity.MAX_TEXT_LENGTH)));
			return 0;
		}
		if (!(world.getBlockEntity(pos) instanceof VariableBlockEntity)) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.set.not_settable"));
			return 0;
		}
		boolean changed = VariableBlock.apply(world, pos, state, value);
		if (changed) {
			notifyNeighbors(world, pos, block);
		}
		source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.set.ok",
				Text.translatable(block.getTranslationKey()),
				Text.literal(pos.toShortString()),
				VariableAssignment.describe(value)), false);
		return 1;
	}
}
