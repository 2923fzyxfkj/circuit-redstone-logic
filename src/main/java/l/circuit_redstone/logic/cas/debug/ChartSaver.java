package l.circuit_redstone.logic.cas.debug;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.client.ChartClientDialogs;

/**
 * 运行图落盘 + "另存为"对话框（两条实现）。
 *
 * <ol>
 *   <li><b>总是</b>先存一份到 {@code <游戏目录>/circuit-charts/}，专用服务器/控制台也能拿到文件；</li>
 *   <li>客户端环境下再让玩家自己选一次保存位置，优先用系统原生的 AWT {@link FileDialog}；
 *       若这个 JVM 是 headless（例如 Gradle 的 {@code runClient} 会设 {@code java.awt.headless=true}），
 *       就改用<b>游戏内</b>的 {@code ChartSaveScreen}（一定看得见）。</li>
 * </ol>
 *
 * <p>AWT 对话框跑在独立线程上（不在服务端线程、也不在渲染线程），所以弹窗期间世界照常 tick。
 */
public final class ChartSaver {
	/** 落盘结果。 */
	public record Saved(Path path, int bytes) {
	}

	private ChartSaver() {
	}

	/** 运行图默认目录：{@code <游戏目录>/circuit-charts}。 */
	public static Path directory() {
		return FabricLoader.getInstance().getGameDir().resolve("circuit-charts");
	}

	/** 是否具备弹 AWT 原生"另存为"的条件（客户端环境 + 不是 headless）。 */
	public static boolean canUseAwtDialog() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
			return false;
		}
		try {
			return !GraphicsEnvironment.isHeadless();
		} catch (Throwable unsupported) {
			return false;
		}
	}

	/** 环境摘要，启动时打一行日志，方便排查"为什么没弹窗"。 */
	public static String environmentDescription() {
		String headless;
		try {
			headless = String.valueOf(GraphicsEnvironment.isHeadless());
		} catch (Throwable unsupported) {
			headless = "unavailable(" + unsupported.getClass().getSimpleName() + ")";
		}
		return "env=" + FabricLoader.getInstance().getEnvironmentType()
				+ ", awtHeadless=" + headless
				+ ", -Djava.awt.headless=" + System.getProperty("java.awt.headless")
				+ ", awtDialog=" + canUseAwtDialog();
	}

	/** 保存到默认目录；失败返回 null。 */
	public static Saved saveDefault(byte[] payload, String fileName) {
		try {
			Path directory = directory();
			Files.createDirectories(directory);
			Path target = directory.resolve(fileName);
			Files.write(target, payload);
			return new Saved(target, payload.length);
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.error("[{}] failed to save chart", CircuitRedstone.MOD_ID, failure);
			return null;
		}
	}

	/** 保存同名文本清单（逐条变化，纯文本更好读）；失败返回 null。 */
	public static Path saveText(String text, String fileName) {
		try {
			Path directory = directory();
			Files.createDirectories(directory);
			Path target = directory.resolve(fileName);
			Files.writeString(target, text);
			return target;
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.error("[{}] failed to save chart text", CircuitRedstone.MOD_ID, failure);
			return null;
		}
	}

	/**
	 * 弹系统原生的"另存为"对话框。
	 *
	 * @param onSaved  玩家选好路径并写成功后回调（在对话框线程上执行，调用方自己切回主线程）
	 * @param fallback 对话框打不开时改走这里（通常是游戏内界面）
	 */
	public static void offerSaveDialog(byte[] payload, String suggestedName, Consumer<Path> onSaved,
			Runnable fallback) {
		if (!canUseAwtDialog()) {
			fallback.run();
			return;
		}
		Thread thread = new Thread(() -> openDialog(payload, suggestedName, onSaved, fallback),
				"circuit-redstone-chart-save");
		thread.setDaemon(true);
		thread.start();
	}

	private static void openDialog(byte[] payload, String suggestedName, Consumer<Path> onSaved, Runnable fallback) {
		FileDialog dialog = null;
		try {
			// 标题跟着内容走：运行图是 png，视频是 gif。
			String title = suggestedName.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")
					? "Save circuit video"
					: "Save circuit chart";
			dialog = new FileDialog((Frame) null, title, FileDialog.SAVE);
			dialog.setFile(suggestedName);
			dialog.setDirectory(directory().toString());
			dialog.setVisible(true);
			String file = dialog.getFile();
			String chosenDirectory = dialog.getDirectory();
			if (file == null || chosenDirectory == null) {
				return;
			}
			Path target = Path.of(chosenDirectory, file);
			Files.write(target, payload);
			onSaved.accept(target);
		} catch (Throwable failure) {
			// AWT 在某些环境（无显示、被精简的 JVM）会直接抛错；此时默认目录里的那份已经写好了，
			// 再补一个游戏内对话框，至少让玩家能自己选名字。
			CircuitRedstone.LOGGER.warn("[{}] AWT save-as dialog failed, falling back to the in-game screen: {}",
					CircuitRedstone.MOD_ID, failure.toString());
			fallback.run();
		} finally {
			if (dialog != null) {
				dialog.dispose();
			}
		}
	}

	/**
	 * 客户端环境里弹"游戏内另存为界面"（不依赖 AWT，一定可见）。
	 *
	 * @return 是否成功投递（非客户端 / 没有客户端实例时返回 false）
	 */
	public static boolean offerInGameDialog(byte[] payload, String suggestedName, Consumer<Path> onSaved) {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
			return false;
		}
		ChartClientDialogs.openSaveScreen(payload, suggestedName, onSaved);
		return true;
	}
}
