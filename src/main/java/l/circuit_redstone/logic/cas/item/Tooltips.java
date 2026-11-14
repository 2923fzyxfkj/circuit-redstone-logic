package l.circuit_redstone.logic.cas.item;

import java.util.List;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;

/**
 * Tooltip 分行工具。
 *
 * <p>Minecraft 的物品提示框是<b>一行一个 {@link Text} 组件</b>；把 {@code "\n"} 塞进同一个
 * Text 在 1.20.4 里并不会换行（中文没有空格可以断行，整条会顶满一行）。
 * 所以这里按 {@code <base>.0}, {@code <base>.1} … 逐个追加组件，一行一个。
 */
public final class Tooltips {
	/** 单个 tooltip 最多多少行（防御性上限）。 */
	private static final int MAX_LINES = 16;

	private Tooltips() {
	}

	/**
	 * 把 {@code baseKey.0..n-1} 逐行追加到 tooltip。
	 *
	 * <p>行数不写死在 Java 里：一直读到"下一个键在当前语言里不存在"为止，
	 * 因此语言文件是行数的唯一来源（校验脚本会保证两种语言行数一致）。
	 */
	public static void appendLines(List<Text> tooltip, String baseKey) {
		Language language = Language.getInstance();
		boolean appended = false;
		for (int index = 0; index < MAX_LINES; index++) {
			String key = baseKey + "." + index;
			if (!language.hasTranslation(key)) {
				break;
			}
			tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
			appended = true;
		}
		if (!appended) {
			// 兜底：没有编号键时至少显示一条，避免"物品完全没说明"。
			tooltip.add(Text.translatable(baseKey).formatted(Formatting.GRAY));
		}
	}
}
