import java.util.OptionalInt;

import l.circuit_redstone.logic.cas.logic.ClockTiming;
import l.circuit_redstone.logic.cas.logic.ColorHex;
import l.circuit_redstone.logic.cas.logic.ComparisonMode;
import l.circuit_redstone.logic.cas.logic.DataConversion;
import l.circuit_redstone.logic.cas.logic.ExchangeRule;
import l.circuit_redstone.logic.cas.logic.GateKind;
import l.circuit_redstone.logic.cas.logic.LevelUtil;
import l.circuit_redstone.logic.cas.logic.ScannerMath;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;
import l.circuit_redstone.logic.cas.logic.VariableAssignment;

/**
 * 纯逻辑自测：不依赖 Minecraft，只验证信号协议与布尔函数。
 *
 * <p>用法（先 build 出 classes）：
 * <pre>
 * javac -encoding UTF-8 -cp build/classes/java/main -d build/tmp/logiccheck tools/LogicCheck.java
 * java -cp "build/classes/java/main;build/tmp/logiccheck" LogicCheck
 * </pre>
 */
public final class LogicCheck {
	private static int checks;
	private static int failures;

	public static void main(String[] args) {
		adc();
		dac();
		gates();
		secondaryInput();
		limiter();
		scanner();
		comparison();
		clock();
		dataConversion();
		colorHex();
		variableAssignment();
		exchangeRule();

		System.out.printf("%n%d checks, %d failures%n", checks, failures);
		if (failures > 0) {
			System.out.println("LOGIC CHECK FAILED");
			System.exit(1);
		}
		System.out.println("LOGIC CHECK PASSED");
	}

	private static void adc() {
		// 红石 -> 导线：0 -> 0，>0 -> 1
		expectInt("adc(0)", 0, SignalProtocol.redstoneToWire(0));
		expectInt("adc(1)", 1, SignalProtocol.redstoneToWire(1));
		expectInt("adc(8)", 1, SignalProtocol.redstoneToWire(8));
		expectInt("adc(15)", 1, SignalProtocol.redstoneToWire(15));
		expectInt("adc(-4)", 0, SignalProtocol.redstoneToWire(-4));
		expectInt("adc(99)", 1, SignalProtocol.redstoneToWire(99));
	}

	private static void dac() {
		// 导线 -> 红石：0 -> 0，非 0 -> 15
		expectInt("dac(0)", 0, SignalProtocol.wireToRedstone(0));
		expectInt("dac(1)", 15, SignalProtocol.wireToRedstone(1));
		expectInt("dac(2)", 15, SignalProtocol.wireToRedstone(2));
		expectInt("dacFromBool(true)", 15, SignalProtocol.redstoneFromBoolean(true));
		expectInt("dacFromBool(false)", 0, SignalProtocol.redstoneFromBoolean(false));
	}

	private static void gates() {
		// 完整真值表
		for (GateKind kind : GateKind.values()) {
			for (boolean a : new boolean[] { false, true }) {
				for (boolean b : new boolean[] { false, true }) {
					boolean expected = switch (kind) {
						case AND -> a && b;
						case OR -> a || b;
						case NOT -> !a;
						case XOR -> a ^ b;
					};
					expectBool(kind + "(" + a + ", " + b + ")", expected, kind.apply(a, b));
					expectInt("dacOfGate(" + kind + ", " + a + ", " + b + ")",
							SignalProtocol.redstoneFromBoolean(expected),
							SignalProtocol.wireToRedstone(SignalProtocol.wireFromBoolean(kind.apply(a, b))));
				}
			}
		}
		expectBool("NOT usesSecondaryInput", false, GateKind.NOT.usesSecondaryInput());
		expectBool("AND usesSecondaryInput", true, GateKind.AND.usesSecondaryInput());
	}

	private static void secondaryInput() {
		// 左右两侧 B：左优先，左侧无信号才取右侧
		expectInt("secondary(0,0)", 0, LevelUtil.selectSecondary(0, 0));
		expectInt("secondary(0,5)", 5, LevelUtil.selectSecondary(0, 5));
		expectInt("secondary(5,0)", 5, LevelUtil.selectSecondary(5, 0));
		expectInt("secondary(5,9)", 5, LevelUtil.selectSecondary(5, 9));
		expectInt("secondary(0,15)", 15, LevelUtil.selectSecondary(0, 15));
	}

	private static void limiter() {
		expectInt("min(0,0)", 0, LevelUtil.limit(0, 0));
		expectInt("min(0,15)", 0, LevelUtil.limit(0, 15));
		expectInt("min(15,0)", 0, LevelUtil.limit(15, 0));
		expectInt("min(15,15)", 15, LevelUtil.limit(15, 15));
		expectInt("min(9,4)", 4, LevelUtil.limit(9, 4));
	}

	private static void scanner() {
		// 上方有方块 -> 取上方；否则左右取较大
		expectInt("combine(0,0,0)", 0, ScannerMath.combine(0, 0, 0));
		expectInt("combine(3,5,0)", 5, ScannerMath.combine(3, 5, 0));
		expectInt("combine(5,3,0)", 5, ScannerMath.combine(5, 3, 0));
		expectInt("combine(3,5,2)", 2, ScannerMath.combine(3, 5, 2));
		expectInt("combine(0,0,7)", 7, ScannerMath.combine(0, 0, 7));
		expectInt("combine(9,0,0)", 9, ScannerMath.combine(9, 0, 0));
		expectInt("range equals max level", SignalProtocol.MAX_LEVEL, 15);

		// 常量优先：射线上有常量时直接取它的值（上 > 左 > 右），没有才退回数量合并。
		expectInt("pickConstant(up,left,right)", 7,
				pick(OptionalInt.of(7), OptionalInt.of(3), OptionalInt.of(5)));
		expectInt("pickConstant(left,right)", 3,
				pick(OptionalInt.empty(), OptionalInt.of(3), OptionalInt.of(5)));
		expectInt("pickConstant(right)", 5,
				pick(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.of(5)));
		expectInt("pickConstant(none)", -1,
				pick(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty()));
		// 常量值超出 0..15 时由 clamp 收口（evaluate 统一调用）
		expectInt("clamp(constant 20)", 15, SignalProtocol.clamp(20));
		expectInt("clamp(constant -3)", 0, SignalProtocol.clamp(-3));
	}

	/** 把 {@code OptionalInt} 压成 int 便于断言：没有值时返回 -1。 */
	private static int pick(OptionalInt up, OptionalInt left, OptionalInt right) {
		OptionalInt picked = ScannerMath.pickConstant(up, left, right);
		return picked.isPresent() ? picked.getAsInt() : -1;
	}

	/** 比较器：左侧红石等级 1..5 对应的比较模式（A 在后、B 在右）。 */
	private static void comparison() {
		expectBool("cmp1: 3<5", ComparisonMode.test(ComparisonMode.LESS, 3, 5));
		expectBool("cmp1: 5<3 false", !ComparisonMode.test(ComparisonMode.LESS, 5, 3));
		expectBool("cmp2: 5<=5", ComparisonMode.test(ComparisonMode.LESS_OR_EQUAL, 5, 5));
		expectBool("cmp2: 6<=5 false", !ComparisonMode.test(ComparisonMode.LESS_OR_EQUAL, 6, 5));
		expectBool("cmp3: 4==4", ComparisonMode.test(ComparisonMode.EQUAL, 4, 4));
		expectBool("cmp3: 4==5 false", !ComparisonMode.test(ComparisonMode.EQUAL, 4, 5));
		expectBool("cmp4: 5>=5", ComparisonMode.test(ComparisonMode.GREATER_OR_EQUAL, 5, 5));
		expectBool("cmp4: 4>=5 false", !ComparisonMode.test(ComparisonMode.GREATER_OR_EQUAL, 4, 5));
		expectBool("cmp5: 5>5 false", !ComparisonMode.test(ComparisonMode.GREATER, 5, 5));
		expectBool("cmp5: 6>5", ComparisonMode.test(ComparisonMode.GREATER, 6, 5));
		// 负数常量也要能比较
		expectBool("cmp1: -3<2", ComparisonMode.test(ComparisonMode.LESS, -3, 2));
		// 模式合法性：0 与 >5 都是无效模式
		expectBool("mode 0 invalid", !ComparisonMode.isValid(0));
		expectBool("mode 1 valid", ComparisonMode.isValid(1));
		expectBool("mode 5 valid", ComparisonMode.isValid(5));
		expectBool("mode 6 invalid", !ComparisonMode.isValid(6));
		expectBool("invalid mode -> false", !ComparisonMode.test(0, 1, 2));
	}

	/** 时钟：频率合法性、脉冲数量、倒计时推进。 */
	private static void clock() {
		expectBool("period 1 valid", ClockTiming.isValidPeriod(1));
		expectBool("period 0 invalid", !ClockTiming.isValidPeriod(0));
		expectBool("period -5 invalid", !ClockTiming.isValidPeriod(-5));

		// 20 tick 内各频率的脉冲次数：period=1 每 tick 都高，period=5 每 5 tick 一次
		expectInt("p1: 20 ticks -> 20 pulses", 20, ClockTiming.countPulses(1, 20));
		expectInt("p2: 20 ticks -> 10 pulses", 10, ClockTiming.countPulses(2, 20));
		expectInt("p5: 20 ticks -> 4 pulses", 4, ClockTiming.countPulses(5, 20));
		expectInt("p3: 9 ticks -> 3 pulses", 3, ClockTiming.countPulses(3, 9));
		expectInt("p7: 6 ticks -> 0 pulses", 0, ClockTiming.countPulses(7, 6));
		expectInt("p0: 20 ticks -> 0 pulses", 0, ClockTiming.countPulses(0, 20));

		// 倒计时：到 0 那一 tick 就是脉冲 tick，之后装回周期
		expectBool("countdown 1 is pulse", ClockTiming.isPulseTick(1));
		expectBool("countdown 2 not pulse", !ClockTiming.isPulseTick(2));
		expectBool("reload to period", ClockTiming.nextCountdown(1, 5) == 5);
		expectBool("decrement", ClockTiming.nextCountdown(3, 5) == 2);
	}

	/** 数据线搬运时的类型转换规则（常量 / 字符串）。 */
	private static void dataConversion() {
		expectInt("parseConstant(42)", 42, DataConversion.parseConstant("42").orElse(-999));
		expectInt("parseConstant(-7)", -7, DataConversion.parseConstant("-7").orElse(-999));
		expectInt("parseConstant('  5 ')", 5, DataConversion.parseConstant("  5 ").orElse(-999));
		expectInt("parseConstant(abc)=-999", -999, DataConversion.parseConstant("abc").orElse(-999));
		expectInt("parseConstant('')=-999", -999, DataConversion.parseConstant("").orElse(-999));
		expectInt("parseConstant(overflow)=-999", -999, DataConversion.parseConstant("2147483648").orElse(-999));

		// 常量 -> 常量 / 字符串
		expectInt("convert(const,const)", 42,
				DataConversion.convert(DataConversion.Value.constant(42), DataConversion.Kind.CONSTANT)
						.orElseThrow().number());
		expectBool("convert(const,text)=42", "42".equals(
				DataConversion.convert(DataConversion.Value.constant(42), DataConversion.Kind.TEXT)
						.orElseThrow().asText()));
		// 字符串 -> 字符串 / 常量
		expectBool("convert(text,text)", "abc".equals(
				DataConversion.convert(DataConversion.Value.text("abc"), DataConversion.Kind.TEXT).orElseThrow().asText()));
		expectInt("convert(' 15 ',const)", 15,
				DataConversion.convert(DataConversion.Value.text(" 15 "), DataConversion.Kind.CONSTANT)
						.orElseThrow().number());
		expectBool("convert(abc,const) empty",
				DataConversion.convert(DataConversion.Value.text("abc"), DataConversion.Kind.CONSTANT).isEmpty());
		expectBool("convert('',const) empty",
				DataConversion.convert(DataConversion.Value.text(""), DataConversion.Kind.CONSTANT).isEmpty());
	}

	/** 纯色方块的十六进制颜色解析。 */
	private static void colorHex() {
		expectInt("ColorHex.parse(#12AB34)", 0x12AB34, ColorHex.parse("#12AB34").orElse(-1));
		expectInt("ColorHex.parse(12ab34)", 0x12AB34, ColorHex.parse("12ab34").orElse(-1));
		expectInt("ColorHex.parse(0x12AB34)", 0x12AB34, ColorHex.parse("0x12AB34").orElse(-1));
		expectInt("ColorHex.parse(#FFF)=-1", -1, ColorHex.parse("#FFF").orElse(-1));
		expectInt("ColorHex.parse(#GGGGGG)=-1", -1, ColorHex.parse("#GGGGGG").orElse(-1));
		expectBool("ColorHex.format", "#12AB34".equals(ColorHex.format(0x12AB34)));
		expectBool("ColorHex.format(black)", "#000000".equals(ColorHex.format(0)));
	}

	/** 变量方块的赋值语法：{@code int=…} / {@code str="…"}。 */
	private static void variableAssignment() {
		// 正常用法
		VariableAssignment.Result intCase = VariableAssignment.parse("int=42");
		expectBool("var(int=42) success", intCase instanceof VariableAssignment.Success);
		if (intCase instanceof VariableAssignment.Success success) {
			expectInt("var(int=42) value", 42, success.value().number());
			expectBool("var(int=42) is constant", success.value().kind() == DataConversion.Kind.CONSTANT);
		}

		VariableAssignment.Result strCase = VariableAssignment.parse("STR=\"hello world\"");
		expectBool("var(STR=\"hello world\") success", strCase instanceof VariableAssignment.Success);
		if (strCase instanceof VariableAssignment.Success success) {
			expectBool("var(str) text", "hello world".equals(success.value().text()));
			expectBool("var(str) is text", success.value().kind() == DataConversion.Kind.TEXT);
		}

		// 等号两边与首尾空白都要容忍
		expectBool("var( int = -7 ) success",
				VariableAssignment.parse("  int = -7  ") instanceof VariableAssignment.Success);
		// 空文本是合法值
		VariableAssignment.Result emptyText = VariableAssignment.parse("str=\"\"");
		expectBool("var(str=\"\") success", emptyText instanceof VariableAssignment.Success);
		if (emptyText instanceof VariableAssignment.Success success) {
			expectBool("var(str=\"\") empty", success.value().text().isEmpty());
		}

		// 失败分支：错误种类要能精确区分（命令层按它选消息）
		expectFailure("var(42) need_equals", "need_equals", VariableAssignment.parse("42"));
		expectFailure("var(bool=1) bad_kind", "bad_kind", VariableAssignment.parse("bool=1"));
		expectFailure("var(int=abc) bad_number", "bad_number", VariableAssignment.parse("int=abc"));
		expectFailure("var(int=) bad_number", "bad_number", VariableAssignment.parse("int="));
		expectFailure("var(str=hi) need_quotes", "need_quotes", VariableAssignment.parse("str=hi"));
		expectFailure("var(str=\"hi) need_quotes", "need_quotes", VariableAssignment.parse("str=\"hi"));
		expectFailure("var(null=1) null_takes_no_value", "null_takes_no_value",
				VariableAssignment.parse("null=1"));

		// null：不需要值，大小写不敏感
		VariableAssignment.Result nullCase = VariableAssignment.parse("NULL");
		expectBool("var(null) success", nullCase instanceof VariableAssignment.Success);
		if (nullCase instanceof VariableAssignment.Success success) {
			expectBool("var(null) is null", success.value().kind() == DataConversion.Kind.NULL);
		}
		// null 搬运规则：写进常量得到 0、写进字符串得到空串
		expectInt("null -> constant", 0,
				DataConversion.convert(DataConversion.Value.nullValue(), DataConversion.Kind.CONSTANT)
						.orElseThrow().number());
		expectBool("null -> text is empty", DataConversion
				.convert(DataConversion.Value.nullValue(), DataConversion.Kind.TEXT)
				.orElseThrow().asText().isEmpty());
		expectBool("null asText empty", DataConversion.Value.nullValue().asText().isEmpty());
		expectInt("null asConstant", 0, DataConversion.Value.nullValue().asConstant().orElse(-1));

		// 展示形式：文本带引号，整数不带
		expectBool("var describe(const)", "42".equals(
				VariableAssignment.describe(DataConversion.Value.constant(42))));
		expectBool("var describe(text)", "\"hi\"".equals(
				VariableAssignment.describe(DataConversion.Value.text("hi"))));
		expectBool("var describe(null)", "null".equals(
				VariableAssignment.describe(DataConversion.Value.nullValue())));
	}

	/** 电路终端的兑换规则：一批 4 导线，数量必须按整批买。 */
	private static void exchangeRule() {
		expectInt("exchange.wireCost", 4, ExchangeRule.WIRE_COST);
		expectInt("exchange.default", 1, ExchangeRule.DEFAULT_AMOUNT);
		expectInt("exchange.bulk", 16, ExchangeRule.BULK_AMOUNT);
		// 每批数量
		expectInt("perBatch(hover_block)", 16, ExchangeRule.perBatch("hover_block"));
		expectInt("perBatch(color_block)", 16, ExchangeRule.perBatch("color_block"));
		expectInt("perBatch(and_gate)", 1, ExchangeRule.perBatch("and_gate"));
		expectInt("perBatch(variable)", 1, ExchangeRule.perBatch("variable"));
		expectInt("perBatch(null)", 1, ExchangeRule.perBatch(null));

		// 普通方块：任意正整数都是整批，价格 = 4 × 数量
		expectBool("valid(and_gate,1)", ExchangeRule.isValidAmount("and_gate", 1));
		expectBool("valid(and_gate,3)", ExchangeRule.isValidAmount("and_gate", 3));
		expectBool("invalid(and_gate,0)", !ExchangeRule.isValidAmount("and_gate", 0));
		expectInt("cost(and_gate,1)", 4, ExchangeRule.costFor("and_gate", 1));
		expectInt("cost(and_gate,3)", 12, ExchangeRule.costFor("and_gate", 3));

		// 批量方块：必须是 16 的倍数且至少 16
		expectBool("valid(hover_block,16)", ExchangeRule.isValidAmount("hover_block", 16));
		expectBool("valid(hover_block,32)", ExchangeRule.isValidAmount("hover_block", 32));
		expectBool("invalid(hover_block,15)", !ExchangeRule.isValidAmount("hover_block", 15));
		expectBool("invalid(hover_block,17)", !ExchangeRule.isValidAmount("hover_block", 17));
		expectInt("cost(hover_block,16)", 4, ExchangeRule.costFor("hover_block", 16));
		expectInt("cost(hover_block,32)", 8, ExchangeRule.costFor("hover_block", 32));
		expectInt("cost(hover_block,15)=-1", -1, ExchangeRule.costFor("hover_block", 15));
	}

	private static void expectFailure(String name, String expectedError, VariableAssignment.Result result) {
		checks++;
		if (!(result instanceof VariableAssignment.Failure failure)) {
			failures++;
			System.out.printf("FAIL %-38s expected failure '%s', got success%n", name, expectedError);
			return;
		}
		if (!expectedError.equals(failure.error())) {
			failures++;
			System.out.printf("FAIL %-38s expected '%s' actual '%s'%n", name, expectedError, failure.error());
		}
	}

	private static void expectInt(String name, int expected, int actual) {
		checks++;
		if (expected != actual) {
			failures++;
			System.out.printf("FAIL %-38s expected=%d actual=%d%n", name, expected, actual);
		}
	}

	private static void expectBool(String name, boolean expected, boolean actual) {
		checks++;
		if (expected != actual) {
			failures++;
			System.out.printf("FAIL %-38s expected=%b actual=%b%n", name, expected, actual);
		}
	}

	/** 只关心"必须为真"的断言。 */
	private static void expectBool(String name, boolean actual) {
		expectBool(name, true, actual);
	}

	private LogicCheck() {
	}
}
