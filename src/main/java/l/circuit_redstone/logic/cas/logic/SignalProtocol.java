package l.circuit_redstone.logic.cas.logic;

/**
 * 信号转换协议（需求 §1）：红石信号等级与导线二进制电平之间的唯一转换入口。
 *
 * <p>红石域：0..15（原版红石强度）。导线域：0 / 1（二进制）。
 * 所有"红石 → 导线"必须走 {@link #redstoneToWire(int)}（ADC），
 * 所有"导线 → 红石"必须走 {@link #wireToRedstone(int)}（DAC）。
 */
public final class SignalProtocol {
	/** 导线低电平。 */
	public static final int WIRE_LOW = 0;
	/** 导线高电平。 */
	public static final int WIRE_HIGH = 1;
	/** 红石最低等级，同时也是"无信号"。 */
	public static final int MIN_LEVEL = 0;
	/** 红石最高等级；本模组所有信号强度的上界都由它派生。 */
	public static final int MAX_LEVEL = 15;

	private SignalProtocol() {
	}

	/**
	 * ADC：红石 → 导线。等级 == 0 输出低电平，等级 > 0 输出高电平。
	 */
	public static int redstoneToWire(int redstoneLevel) {
		return clamp(redstoneLevel) == MIN_LEVEL ? WIRE_LOW : WIRE_HIGH;
	}

	/**
	 * DAC：导线 → 红石。低电平输出 0，高电平输出 {@link #MAX_LEVEL}。
	 */
	public static int wireToRedstone(int wireLevel) {
		return wireLevel == WIRE_LOW ? MIN_LEVEL : MAX_LEVEL;
	}

	/** 布尔 → 导线电平。 */
	public static int wireFromBoolean(boolean high) {
		return high ? WIRE_HIGH : WIRE_LOW;
	}

	/** 布尔 → 红石等级（内部即 DAC）。 */
	public static int redstoneFromBoolean(boolean high) {
		return wireToRedstone(wireFromBoolean(high));
	}

	/** 导线电平是否为高。 */
	public static boolean isWireHigh(int wireLevel) {
		return wireLevel != WIRE_LOW;
	}

	/** 把任意等级夹到 [MIN_LEVEL, MAX_LEVEL]。 */
	public static int clamp(int level) {
		if (level < MIN_LEVEL) {
			return MIN_LEVEL;
		}
		return Math.min(level, MAX_LEVEL);
	}
}
