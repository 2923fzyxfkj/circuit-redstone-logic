package l.circuit_redstone.logic.cas.logic;

import java.util.Locale;

import net.minecraft.util.StringIdentifiable;

/**
 * 变量方块保存的值类型：未设置 / 整数 / 文本。
 *
 * <p>一个变量方块只保存<b>一个</b>变量，类型由最后一次写入决定（{@code /circuit-set} 或数据线）。
 * 类型同时是 BlockState 的属性值（客户端要按它换贴图），所以这里的 {@link #id()}
 * 就是属性值与 NBT 里用的字符串。
 *
 * <p>为什么常量名是 {@code UNSET / INT / STR}：1.20.4 没有字符串属性，只有
 * {@code EnumProperty}（且要求枚举实现 {@link StringIdentifiable}）；属性值取
 * {@link #asString()}，所以名字必须<b>正好</b>是 {@code unset / int / str}——
 * {@code int} 是 Java 关键字不能当常量名，于是文本用 {@code STR}。
 */
public enum VariableKind implements StringIdentifiable {
	/** 还没设置过（数据线读它时视为"没有源"，也不会把目标清零）。 */
	UNSET,
	/** 空值：{@code /circuit-set null} 写入；搬运到常量是 0、到字符串是空串。 */
	NULL,
	/** 整数（任意 int，与常量方块同域）。 */
	INT,
	/** 文本（最长 {@code StringValueBlockEntity.MAX_TEXT_LENGTH}）。 */
	STR;

	/** BlockState 属性值 / NBT 里的写法：{@code unset} / {@code null} / {@code int} / {@code str}。 */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String asString() {
		return id();
	}

	/** 是否已经设置过（不是 {@link #UNSET}，即 {@code null} 也算"设置过"）。 */
	public boolean isSet() {
		return this != UNSET;
	}

	/** 由 BlockState 属性值或 NBT 字符串还原；不认识的一律当"未设置"。 */
	public static VariableKind byId(String id) {
		if (id == null) {
			return UNSET;
		}
		return switch (id.trim().toLowerCase(Locale.ROOT)) {
			case "null" -> NULL;
			case "int" -> INT;
			case "str" -> STR;
			default -> UNSET;
		};
	}
}
