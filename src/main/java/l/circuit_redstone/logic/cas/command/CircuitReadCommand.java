package l.circuit_redstone.logic.cas.command;

import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
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

import l.circuit_redstone.logic.cas.block.ConstantBlock;
import l.circuit_redstone.logic.cas.block.StringValueBlock;
import l.circuit_redstone.logic.cas.block.VariableBlock;
import l.circuit_redstone.logic.cas.logic.DataConversion;
import l.circuit_redstone.logic.cas.logic.VariableAssignment;

/**
 * 数据方块读出指令：{@code /circuit-read}。
 *
 * <ul>
 *   <li>准星对准常量 / 字符串方块时，输出 {@code <方块名> (<x, y, z>): <值>}；</li>
 *   <li>坐标形式 {@code /circuit-read <x> <y> <z>} 给控制台/命令方块用（服务端自测走这条）；</li>
 *   <li>目标不是数据方块时给出明确报错，而不是默默无输出。</li>
 * </ul>
 */
public final class CircuitReadCommand {
	private static final String ARG_POS = "pos";

	private CircuitReadCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		// 坐标形式先注册：Brigadier 按插入顺序尝试子节点（1.2.9 用 LinkedHashMap），
		// 否则坐标形式会被"准星形式"抢走。准星形式这里不接参数，不存在歧义。
		dispatcher.register(CommandManager.literal("circuit-read")
				.then(CommandManager.argument(ARG_POS, BlockPosArgumentType.blockPos())
						.executes(CircuitReadCommand::readAtPos))
				.executes(CircuitReadCommand::readAimed));
	}

	private static int readAimed(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.read.players_only"));
			return 0;
		}
		BlockPos target = CommandTargets.aimedBlock(player);
		if (target == null) {
			source.sendError(Text.translatable("command.circuit-redstone_logic.read.no_target"));
			return 0;
		}
		return report(source, target);
	}

	private static int readAtPos(CommandContext<ServerCommandSource> context) {
		return report(context.getSource(), BlockPosArgumentType.getBlockPos(context, ARG_POS));
	}

	/** 读出一个数据方块的值并按 {@code <方块名> (<坐标>): <值>} 输出。 */
	private static int report(ServerCommandSource source, BlockPos pos) {
		ServerWorld world = source.getWorld();
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		if (block instanceof ConstantBlock) {
			int value = ConstantBlock.valueAt(world, pos);
			source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.read.result",
					Text.translatable(block.getTranslationKey()),
					Text.literal(pos.toShortString()),
					String.valueOf(value)), false);
			return 1;
		}

		if (block instanceof StringValueBlock) {
			String text = StringValueBlock.textAt(world, pos);
			source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.read.result",
					Text.translatable(block.getTranslationKey()),
					Text.literal(pos.toShortString()),
					"\"" + text + "\""), false);
			return 1;
		}

		if (block instanceof VariableBlock) {
			Optional<DataConversion.Value> value = VariableBlock.valueAt(world, pos);
			String text = value.map(VariableAssignment::describe)
					.orElseGet(() -> Text.translatable(
							"command.circuit-redstone_logic.read.variable_unset").getString());
			source.sendFeedback(() -> Text.translatable("command.circuit-redstone_logic.read.result",
					Text.translatable(block.getTranslationKey()),
					Text.literal(pos.toShortString()),
					text), false);
			return 1;
		}

		source.sendError(Text.translatable("command.circuit-redstone_logic.read.not_data"));
		return 0;
	}
}
