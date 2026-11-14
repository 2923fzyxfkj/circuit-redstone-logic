package l.circuit_redstone.logic.cas.logic;

import java.util.OptionalInt;

/**
 * 纯色方块的颜色值：`#RRGGBB` / `RRGGBB` / `0xRRGGBB` 的解析与格式化。
 */
public final class ColorHex {
	/** 只保留 RGB 三个分量。 */
	public static final int RGB_MASK = 0xFFFFFF;
	/** 十六进制颜色文本的位数（6 = RRGGBB）。 */
	private static final int HEX_DIGITS = 6;
	/** 颜色分量基数（十六进制）。 */
	private static final int HEX_RADIX = 16;

	private ColorHex() {
	}

	/**
	 * 解析一个十六进制颜色。
	 *
	 * @param text 允许 {@code #RRGGBB}、{@code 0xRRGGBB} 或裸 {@code RRGGBB}
	 * @return 解析成功时的 RGB 值；位数不对或含非十六进制字符时返回 {@link OptionalInt#empty()}
	 */
	public static OptionalInt parse(String text) {
		if (text == null) {
			return OptionalInt.empty();
		}
		String trimmed = text.trim();
		if (trimmed.startsWith("#")) {
			trimmed = trimmed.substring(1);
		} else if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
			trimmed = trimmed.substring(2);
		}
		if (trimmed.length() != HEX_DIGITS) {
			return OptionalInt.empty();
		}
		try {
			return OptionalInt.of(Integer.parseInt(trimmed, HEX_RADIX) & RGB_MASK);
		} catch (NumberFormatException invalid) {
			return OptionalInt.empty();
		}
	}

	/** 格式化成 {@code #RRGGBB}（大写）。 */
	public static String format(int rgb) {
		return String.format("#%0" + HEX_DIGITS + "X", rgb & RGB_MASK);
	}
}
