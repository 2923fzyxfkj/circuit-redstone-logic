package l.circuit_redstone.logic.cas.logic;

/**
 * 时钟的计时规则（纯逻辑，方便单测）：每 {@code period} 个 tick 产生一个 tick 的高电平。
 *
 * <p>倒计时从 {@code period} 开始，每 tick 减 1；减到 0（含更小）的那一 tick 就是"脉冲 tick"，
 * 随后重新装回 {@code period}。于是：
 * <ul>
 *   <li>{@code period = 1}：每个 tick 都是脉冲（常高）；</li>
 *   <li>{@code period = 5}：第 5、10、15… tick 输出高电平，其余为低；</li>
 *   <li>{@code period <= 0}：未设置，始终输出低电平。</li>
 * </ul>
 */
public final class ClockTiming {
	/** 允许的最小频率（tick）。 */
	public static final int MIN_PERIOD_TICKS = 1;

	private ClockTiming() {
	}

	/** 频率是否有效（至少 1 tick）。 */
	public static boolean isValidPeriod(int period) {
		return period >= MIN_PERIOD_TICKS;
	}

	/** 当前这一 tick 是否该输出脉冲。 */
	public static boolean isPulseTick(int countdown) {
		return countdown - 1 <= 0;
	}

	/** 推进倒计时：脉冲 tick 之后重新装回 {@code period}。 */
	public static int nextCountdown(int countdown, int period) {
		return isPulseTick(countdown) ? period : countdown - 1;
	}

	/**
	 * 模拟 {@code ticks} 个 tick，统计其中有多少个是脉冲 tick（单测用）。
	 *
	 * @param period 频率（tick）
	 * @param ticks  模拟的总 tick 数
	 * @return 脉冲次数；频率非法时为 0
	 */
	public static int countPulses(int period, int ticks) {
		if (!isValidPeriod(period) || ticks <= 0) {
			return 0;
		}
		int countdown = period;
		int pulses = 0;
		for (int tick = 0; tick < ticks; tick++) {
			if (isPulseTick(countdown)) {
				pulses++;
			}
			countdown = nextCountdown(countdown, period);
		}
		return pulses;
	}
}
