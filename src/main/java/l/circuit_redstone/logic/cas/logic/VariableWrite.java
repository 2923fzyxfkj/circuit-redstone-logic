package l.circuit_redstone.logic.cas.logic;

import java.util.Optional;

/**
 * 数据线把源值写进变量方块时的类型规则。
 *
 * <ul>
 *   <li>变量<b>已经设置过</b>：按它当前的类型转换（和写常量/字符串完全一样的规则，
 *       例如文本 {@code "abc"} 写进 int 变量会失败）；</li>
 *   <li>变量<b>还没设置</b>（或已经是 {@code null}）：跟着源的类型走——源是常量就设成 int，
 *       源是文本就设成 str，源是 {@code null} 就设成 {@code null}。
 *       这样第一次写入不需要先手工 {@code /circuit-set}，也不会出现"写不进去又看不出原因"。</li>
 * </ul>
 */
public final class VariableWrite {
	private VariableWrite() {
	}

	/**
	 * 计算写进变量后应该得到的值。
	 *
	 * @return 转换失败（例如文本内容不是整数却要写进 int 变量）时返回 empty
	 */
	public static Optional<DataConversion.Value> convert(DataConversion.Value source, VariableKind target) {
		if (target == VariableKind.UNSET || target == VariableKind.NULL) {
			return Optional.of(source);
		}
		// null 走转换规则后会变成 0 / 空串（DataConversion.Value#asConstant / #asText）。
		return DataConversion.convert(source,
				target == VariableKind.INT ? DataConversion.Kind.CONSTANT : DataConversion.Kind.TEXT);
	}
}
