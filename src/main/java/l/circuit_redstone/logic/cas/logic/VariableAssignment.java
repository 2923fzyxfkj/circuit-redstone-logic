package l.circuit_redstone.logic.cas.logic;

import java.util.Locale;

/**
 * 变量方块的赋值语法：{@code int=<整数>} 与 {@code str="<文本>"}。
 *
 * <p>为什么单独抽一个类：解析规则要在<b>没有世界</b>的情况下测（{@code tools/LogicCheck}），
 * 命令层只负责把 {@link Result} 翻译成聊天栏消息。
 *
 * <p>两条语法（类型名大小写不敏感，{@code =} 两边可以有空格）：
 * <pre>
 * int=42          → 整数 42
 * str="hello"     → 文本 hello（双引号是语法的一部分，不存进值里）
 * </pre>
 * 文本里的双引号不做转义：{@code str="a"b"} 的值就是 {@code a"b}（首尾引号之间的一切）。
 */
public final class VariableAssignment {
	/** 解析结果。 */
	public sealed interface Result permits Success, Failure {
	}

	/**
	 * 解析成功。
	 *
	 * @param value 要写进变量的值（常量或文本）
	 */
	public record Success(DataConversion.Value value) implements Result {
	}

	/**
	 * 解析失败。
	 *
	 * @param error  错误种类：{@code need_equals} / {@code bad_kind} / {@code bad_number} / {@code need_quotes}
	 * @param detail 出问题的原文片段（拼进错误消息里给玩家看）
	 */
	public record Failure(String error, String detail) implements Result {
	}

	private VariableAssignment() {
	}

	/** 解析 {@code 类型=值} 形式的原文；{@code null} 是唯一不需要值的写法。 */
	public static Result parse(String raw) {
		String text = raw == null ? "" : raw.trim();
		if (text.equalsIgnoreCase("null")) {
			return new Success(DataConversion.Value.nullValue());
		}
		int equals = text.indexOf('=');
		if (equals < 0) {
			// 没有等号：老语法 /circuit-set 42 对准变量时就是这种，要明确告诉玩家新写法。
			return new Failure("need_equals", text);
		}
		String name = text.substring(0, equals).trim().toLowerCase(Locale.ROOT);
		String valueText = text.substring(equals + 1).trim();

		// null 不带值；"null=1" 这种要单独提示，否则会掉进"文本要加引号"的错误分支。
		if (name.equals("null")) {
			return new Failure("null_takes_no_value", valueText);
		}

		// 这里只用字符串比较、不引用 VariableKind：解析规则要能在没有 Minecraft 的
		// 纯逻辑自测（tools/LogicCheck）里跑，而枚举实现了 MC 的接口。
		if (name.equals("int")) {
			try {
				return new Success(DataConversion.Value.constant(Integer.parseInt(valueText)));
			} catch (NumberFormatException invalid) {
				return new Failure("bad_number", valueText);
			}
		}
		if (!name.equals("str")) {
			return new Failure("bad_kind", name);
		}

		// str：值必须被一对双引号包住，避免"看起来是文本、实际是数字"的歧义。
		if (valueText.length() < 2 || !valueText.startsWith("\"") || !valueText.endsWith("\"")) {
			return new Failure("need_quotes", valueText);
		}
		return new Success(DataConversion.Value.text(valueText.substring(1, valueText.length() - 1)));
	}

	/** 值的展示形式：整数直接写、文本加双引号、空值写 {@code null}。 */
	public static String describe(DataConversion.Value value) {
		return switch (value.kind()) {
			case CONSTANT -> Integer.toString(value.number());
			case TEXT -> "\"" + value.text() + "\"";
			case NULL -> "null";
		};
	}

	/** 类型名的标准写法（错误消息里提示玩家用哪个）。 */
	public static String names() {
		return "null / int / str";
	}
}
