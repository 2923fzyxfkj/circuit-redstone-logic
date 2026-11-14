package l.circuit_redstone.logic.cas.logic;

import java.util.Set;

/**
 * 电路终端的兑换规则：一次扣多少导线、一批给多少个、以及自定义数量时的计价。
 *
 * <p>规则（与玩家约定一致）：
 * <ul>
 *   <li>价格按<b>批</b>算，一批 {@link #WIRE_COST} 个导线；</li>
 *   <li>普通方块一批 {@link #DEFAULT_AMOUNT} 个，所以"4 导线 1 个"；</li>
 *   <li>{@link #BULK_PATHS} 里的方块一批 {@link #BULK_AMOUNT} 个，所以"4 导线 16 个"；</li>
 *   <li>自定义数量 {@code /circuit-terminal get <id> <B>}：B <b>必须是整批</b>
 *       （≥ 一批且是每批数量的整数倍），价格 = 批数 × 4。
 *       这样"买 3 个与门"= 12 导线，"买 32 个悬空方块"= 8 导线，且不会出现半批的零头计价。</li>
 * </ul>
 *
 * <p>为什么抽成纯逻辑类：这些是纯数字规则（不碰世界、不碰物品），
 * {@code tools/LogicCheck} 可以直接测；命令层只负责背包与发放。
 */
public final class ExchangeRule {
	/** 一批的导线价格（所有物品统一）。 */
	public static final int WIRE_COST = 4;
	/** 普通方块每批多少个。 */
	public static final int DEFAULT_AMOUNT = 1;
	/** 批量方块每批多少个。 */
	public static final int BULK_AMOUNT = 16;
	/** 一次给 {@link #BULK_AMOUNT} 个的方块（按物品 id 的 path）。 */
	private static final Set<String> BULK_PATHS = Set.of("hover_block", "color_block");

	private ExchangeRule() {
	}

	/**
	 * 某个物品一批给多少个。
	 *
	 * @param path 物品 id 的 path（例如 {@code hover_block}）
	 */
	public static int perBatch(String path) {
		return path != null && BULK_PATHS.contains(path) ? BULK_AMOUNT : DEFAULT_AMOUNT;
	}

	/**
	 * 自定义数量是否合法：至少一批，且是每批数量的整数倍。
	 *
	 * @param path      物品 id 的 path
	 * @param requested 玩家想买的数量
	 */
	public static boolean isValidAmount(String path, int requested) {
		int per = perBatch(path);
		return requested >= per && requested % per == 0;
	}

	/**
	 * 购买 {@code requested} 个需要的导线数。
	 *
	 * @return 非法数量（不是整批）时返回 {@code -1}
	 */
	public static int costFor(String path, int requested) {
		if (!isValidAmount(path, requested)) {
			return -1;
		}
		return WIRE_COST * (requested / perBatch(path));
	}
}
