package l.circuit_redstone.logic.cas.logic;

/**
 * 「比较器」方块支持的比较模式：由左侧红石信号等级（1..5）选择。
 *
 * <p>语义固定为"后方输入 A 与右方输入 B 的关系"，A 在前、B 在后：
 * <ul>
 *   <li>{@code 1}：A &lt; B（小于）</li>
 *   <li>{@code 2}：A &lt;= B（小于等于）</li>
 *   <li>{@code 3}：A == B（等于）</li>
 *   <li>{@code 4}：A &gt;= B（大于等于）</li>
 *   <li>{@code 5}：A &gt; B（大于）</li>
 * </ul>
 *
 * <p>0 与大于 5 的等级都视为"没有有效模式"，此时比较器输出低电平。
 */
public final class ComparisonMode {
	/** 小于（左侧等级 1）。 */
	public static final int LESS = 1;
	/** 小于等于（左侧等级 2）。 */
	public static final int LESS_OR_EQUAL = 2;
	/** 等于（左侧等级 3）。 */
	public static final int EQUAL = 3;
	/** 大于等于（左侧等级 4）。 */
	public static final int GREATER_OR_EQUAL = 4;
	/** 大于（左侧等级 5）。 */
	public static final int GREATER = 5;
	/** 有效模式的下界。 */
	public static final int MIN_MODE = LESS;
	/** 有效模式的上界。 */
	public static final int MAX_MODE = GREATER;

	private ComparisonMode() {
	}

	/** 该红石等级是否是一个有效模式。 */
	public static boolean isValid(int mode) {
		return mode >= MIN_MODE && mode <= MAX_MODE;
	}

	/**
	 * 按模式判断 {@code first} 与 {@code second} 的关系是否成立。
	 *
	 * @param mode   模式（1..5）
	 * @param first  后方常量值（A）
	 * @param second 右方常量值（B）
	 * @return 成立返回 true；模式非法时返回 false
	 */
	public static boolean test(int mode, int first, int second) {
		return switch (mode) {
			case LESS -> first < second;
			case LESS_OR_EQUAL -> first <= second;
			case EQUAL -> first == second;
			case GREATER_OR_EQUAL -> first >= second;
			case GREATER -> first > second;
			default -> false;
		};
	}
}
