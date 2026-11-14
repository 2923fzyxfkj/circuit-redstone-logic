package l.circuit_redstone.logic.cas.logic;

import java.util.OptionalInt;

/**
 * "输出任意信号器"（Scanner）的结果合并规则。
 */
public final class ScannerMath {
	private ScannerMath() {
	}

	/**
	 * 合并三个方向检测到的非空气方块数量：
	 * 上方有方块时取上方的数量；否则左右两侧取较大的那个。
	 *
	 * @param left  左侧检测数量
	 * @param right 右侧检测数量
	 * @param up    上方检测数量
	 */
	public static int combine(int left, int right, int up) {
		if (up > SignalProtocol.MIN_LEVEL) {
			return up;
		}
		return Math.max(left, right);
	}

	/**
	 * 常量优先规则：射线上有常量方块时直接输出它的值，不再统计方块数量。
	 *
	 * <p>方向优先级与 {@link #combine} 一致：上 &gt; 左 &gt; 右（同方向取最近的常量）。
	 *
	 * @param up    上方向最近的常量值
	 * @param left  左方向最近的常量值
	 * @param right 右方向最近的常量值
	 * @return 有常量时返回该值（可能超出 0..15，由调用方 clamp），否则 {@code empty}
	 */
	public static OptionalInt pickConstant(OptionalInt up, OptionalInt left, OptionalInt right) {
		if (up.isPresent()) {
			return up;
		}
		if (left.isPresent()) {
			return left;
		}
		return right;
	}
}
