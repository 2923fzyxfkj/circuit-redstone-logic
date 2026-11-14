package l.circuit_redstone.logic.cas.sim;

import java.util.Locale;
import java.util.UUID;

/**
 * 一次沙盒推演的结果摘要。
 *
 * @param id         模拟 ID
 * @param reason     结束原因
 * @param ticks      实际推演的 tick 数
 * @param changes    状态变化次数
 * @param cycleTicks 死循环周期（仅 {@link Reason#DEAD_LOOP} 有意义）
 */
public record SimulationSummary(UUID id, Reason reason, int ticks, int changes, int cycleTicks) {
	public enum Reason {
		/** 已稳定（一个 tick 内没有任何状态变化）。 */
		STABLE,
		/** 检测到周期性死循环。 */
		DEAD_LOOP,
		/** 被玩家手动停止。 */
		CANCELLED,
		/** 达到最大推演 tick 数。 */
		TICK_LIMIT,
		/** 超出时间/规模预算。 */
		BUDGET_LIMIT,
		/** 推演内部异常。 */
		ERROR;

		/** 本地化键：command.circuit-redstone_logic.debug.reason.&lt;name&gt;。 */
		public String translationKey() {
			return "command.circuit-redstone_logic.debug.reason." + name().toLowerCase(Locale.ROOT);
		}
	}
}
