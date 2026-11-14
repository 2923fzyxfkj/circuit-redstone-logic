package l.circuit_redstone.logic.cas.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import l.circuit_redstone.logic.cas.CircuitRedstone;

/**
 * 把导出的 PNG 帧序列交给外部 {@code ffmpeg} 编码成 MP4。
 *
 * <p>为什么用外部程序：Java 标准库没有 H.264 编码器，纯手写 MP4/H.264 不现实。
 * 所以这里做两件事：
 * <ol>
 *   <li>启动时探测 {@code ffmpeg} 是否在 PATH 里（只探一次，结果缓存）；</li>
 *   <li>在帧目录里跑 ffmpeg：{@code -framerate 20 -i frame-%04d.png -c:v libx264 -pix_fmt yuv420p}；
 *       如果这个 ffmpeg 没编 libx264，再退回 {@code -c:v mpeg4}。</li>
 * </ol>
 * 找不到 ffmpeg 时完全不影响 GIF —— GIF 是标准库自己编的，永远都会生成。
 */
public final class VideoEncoder {
	/** 与 GIF 一致：一帧一个 tick。 */
	public static final int FRAMES_PER_SECOND = 20;
	/** 编码超时（毫秒）；超时就当失败，避免卡住服务器线程。 */
	private static final long TIMEOUT_MILLIS = 120_000L;

	private static Boolean available;
	private static String command;

	private VideoEncoder() {
	}

	/**
	 * ffmpeg 是否可用。
	 *
	 * <p>只缓存"找到了"这个结论；找不到时每次都重新探测，这样玩家中途装上 ffmpeg
	 * 也不用重启游戏。
	 */
	public static synchronized boolean available() {
		if (Boolean.TRUE.equals(available)) {
			return true;
		}
		command = findCommand();
		available = command != null;
		CircuitRedstone.LOGGER.info("[{}] mp4 encoder: {}", CircuitRedstone.MOD_ID,
				available ? command : "ffmpeg not found in PATH (gif only)");
		return available;
	}

	/**
	 * 编码一个帧目录。
	 *
	 * @param framesDirectory 包含 {@code frame-0000.png ...} 的目录
	 * @param target          目标 mp4 路径
	 * @return 成功时的路径；失败/超时/没有编码器时返回 null
	 */
	public static Path encode(Path framesDirectory, Path target) {
		if (!available()) {
			return null;
		}
		try {
			// 目标目录一般已经存在（和 GIF 同一个文件夹）；不存在就建出来。
			Files.createDirectories(target.toAbsolutePath().getParent());
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.warn("[{}] could not create {}: {}", CircuitRedstone.MOD_ID,
					target.toAbsolutePath().getParent(), failure.toString());
			return null;
		}
		// 先试 libx264，不行再退回 mpeg4（两者都是 yuv420p，所以先补偶数宽高）。
		if (run(framesDirectory, target, "libx264")) {
			return target;
		}
		CircuitRedstone.LOGGER.warn("[{}] libx264 encode failed, retrying with mpeg4", CircuitRedstone.MOD_ID);
		if (run(framesDirectory, target, "mpeg4")) {
			return target;
		}
		// 两次都失败：把可能残留的半截文件删掉，免得玩家以为有 mp4 但打不开。
		try {
			Files.deleteIfExists(target);
		} catch (IOException ignored) {
			// 删不掉就留着，下次 -y 会覆盖
		}
		return null;
	}

	private static boolean run(Path framesDirectory, Path target, String codec) {
		String filter = "pad=ceil(iw/2)*2:ceil(ih/2)*2";
		String[] arguments = {
				command, "-y", "-hide_banner", "-loglevel", "error",
				"-framerate", String.valueOf(FRAMES_PER_SECOND),
				"-start_number", "0",
				"-i", "frame-%04d.png",
				"-vf", filter,
				"-c:v", codec,
				"-pix_fmt", "yuv420p",
				target.toAbsolutePath().toString()
		};
		Path log = framesDirectory.resolve("ffmpeg-" + codec + ".log");
		try {
			ProcessBuilder builder = new ProcessBuilder(arguments);
			builder.directory(framesDirectory.toFile());
			builder.redirectErrorStream(true);
			builder.redirectOutput(log.toFile());
			Process process = builder.start();
			if (!process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				CircuitRedstone.LOGGER.warn("[{}] ffmpeg timed out after {} ms", CircuitRedstone.MOD_ID,
						TIMEOUT_MILLIS);
				return false;
			}
			boolean ok = process.exitValue() == 0 && Files.exists(target) && Files.size(target) > 0;
			if (!ok) {
				// 把 ffmpeg 自己的最后一行错误也带上，排查时不用再去翻临时目录（它会被删掉）。
				CircuitRedstone.LOGGER.warn("[{}] ffmpeg ({}) exited with {}: {}",
						CircuitRedstone.MOD_ID, codec, process.exitValue(), tailOf(log));
			}
			return ok;
		} catch (IOException failure) {
			CircuitRedstone.LOGGER.warn("[{}] ffmpeg ({}) could not start: {}", CircuitRedstone.MOD_ID, codec,
					failure.toString());
			return false;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** 日志文件的最后一行（读不到就返回空串），用于把 ffmpeg 的报错带进主日志。 */
	private static String tailOf(Path log) {
		try {
			java.util.List<String> lines = Files.readAllLines(log);
			for (int i = lines.size() - 1; i >= 0; i--) {
				String line = lines.get(i).strip();
				if (!line.isEmpty()) {
					return line.length() > 300 ? line.substring(0, 300) + "..." : line;
				}
			}
		} catch (IOException ignored) {
			// 日志读不到就算了
		}
		return "";
	}

	private static String findCommand() {
		java.util.List<String> candidates = new java.util.ArrayList<>();
		candidates.add("ffmpeg");
		candidates.add("ffmpeg.exe");
		// 游戏进程可能是 Gradle 守护进程很早以前拉起来的，PATH 可能是旧的；
		// 所以除 PATH 之外再补几个 Windows 上包管理器默认放 ffmpeg 的位置。
		String localAppData = System.getenv("LOCALAPPDATA");
		if (localAppData != null && !localAppData.isBlank()) {
			candidates.add(localAppData + "\\Microsoft\\WinGet\\Links\\ffmpeg.exe");
		}
		String programData = System.getenv("ProgramData");
		if (programData != null && !programData.isBlank()) {
			candidates.add(programData + "\\chocolatey\\bin\\ffmpeg.exe");
		}
		for (String candidate : candidates) {
			if (probe(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean probe(String candidate) {
		try {
			ProcessBuilder builder = new ProcessBuilder(candidate, "-version");
			builder.redirectErrorStream(true);
			builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			Process process = builder.start();
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (IOException missing) {
			return false;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
