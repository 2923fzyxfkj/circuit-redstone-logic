package l.circuit_redstone.logic.cas.logic;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 数据方块（常量 / 字符串）之间流动的"值"及其转换规则。
 *
 * <p>数据线把这套规则当成唯一依据：
 * <ul>
 *   <li>常量 → 常量：原样搬运；常量 → 字符串：转成十进制文本；</li>
 *   <li>字符串 → 字符串：原样搬运；</li>
 *   <li>字符串 → 常量：<b>内容必须是整数</b>（允许首尾空白与正负号），否则转换失败，
 *       数据线显示错误贴图。</li>
 * </ul>
 */
public final class DataConversion {
	/** 值的种类。 */
	public enum Kind {
		/** 常量：一个整数。 */
		CONSTANT,
		/** 字符串：一段文本。 */
		TEXT,
		/** 空值（变量方块的 {@code null}）：写进常量得到 0，写进字符串得到空串。 */
		NULL
	}

	private DataConversion() {
	}

	/**
	 * 严格解析十进制整数：允许首尾空白与正负号，其它一律失败。
	 *
	 * @return 解析成功时的整数；空串、非数字、溢出都返回 {@link OptionalInt#empty()}
	 */
	public static OptionalInt parseConstant(String text) {
		if (text == null) {
			return OptionalInt.empty();
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return OptionalInt.empty();
		}
		try {
			return OptionalInt.of(Integer.parseInt(trimmed));
		} catch (NumberFormatException invalid) {
			return OptionalInt.empty();
		}
	}

	/** 常量 → 文本（十进制，负数带 '-'）。 */
	public static String constantToText(int value) {
		return Integer.toString(value);
	}

	/**
	 * 把源值转换成目标种类。
	 *
	 * @param source 源值
	 * @param target 目标种类
	 * @return 成功时是目标种类的值；字符串内容不是整数却要写进常量时返回 empty
	 */
	public static Optional<Value> convert(Value source, Kind target) {
		if (target == Kind.TEXT) {
			return Optional.of(Value.text(source.asText()));
		}
		return source.asConstant().stream().mapToObj(Value::constant).findFirst();
	}

	/**
	 * 一个数据值：常量（整数）、字符串（文本）或空值（{@code null}）。
	 *
	 * <p>{@code null} 不是"没有值"而是<b>值为空</b>：它照常参与搬运，
	 * 写进常量得到 0、写进字符串得到空串（见 {@link #asText()} / {@link #asConstant()}）。
	 *
	 * @param kind   种类
	 * @param number 常量值（{@link Kind#TEXT} / {@link Kind#NULL} 时无意义）
	 * @param text   文本（{@link Kind#CONSTANT} 时是它的十进制形式；{@link Kind#NULL} 时为空串）
	 */
	public record Value(Kind kind, int number, String text) {
		public static Value constant(int number) {
			return new Value(Kind.CONSTANT, number, constantToText(number));
		}

		public static Value text(String text) {
			return new Value(Kind.TEXT, 0, text);
		}

		/** 空值：搬运到常量就是 0、到字符串就是空串。 */
		public static Value nullValue() {
			return new Value(Kind.NULL, 0, "");
		}

		/** 作为文本的样子：{@code null} 视为空串。 */
		public String asText() {
			return switch (kind) {
				case CONSTANT -> constantToText(number);
				case TEXT -> text;
				case NULL -> "";
			};
		}

		/** 作为整数的样子：{@code null} 视为 0；文本内容不是整数时返回 empty。 */
		public OptionalInt asConstant() {
			return switch (kind) {
				case CONSTANT -> OptionalInt.of(number);
				case TEXT -> parseConstant(text);
				case NULL -> OptionalInt.of(0);
			};
		}
	}
}
