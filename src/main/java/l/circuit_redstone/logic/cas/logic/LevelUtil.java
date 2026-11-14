package l.circuit_redstone.logic.cas.logic;

/**
 * 电平选择与限制运算。这里是"限制信号器"语义的唯一出处。
 */
public final class LevelUtil {
	private LevelUtil() {
	}

	/**
	 * 取左右两侧的 B 输入：左侧有信号时优先左侧，否则取右侧（需求：左右都有则优先左方）。
	 */
	public static int selectSecondary(int left, int right) {
		return left > SignalProtocol.MIN_LEVEL ? left : right;
	}

	/**
	 * 限制信号器：输出 min(A, B)。
	 */
	public static int limit(int a, int b) {
		return Math.min(a, b);
	}
}
