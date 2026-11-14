package l.circuit_redstone.logic.cas.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;

/**
 * 客户端侧的小桥：在客户端线程上打开游戏内"另存为"界面。
 *
 * <p>这个类只在客户端环境被触碰（调用方先判断 {@code EnvType.CLIENT}），
 * 专用服务器不会加载它，因此里面引用 {@link MinecraftClient} 是安全的。
 */
public final class ChartClientDialogs {
	private ChartClientDialogs() {
	}

	/** 在客户端线程上打开保存界面。 */
	public static void openSaveScreen(byte[] payload, String suggestedName, Consumer<Path> onSaved) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		client.execute(() -> client.setScreen(new ChartSaveScreen(payload, suggestedName, onSaved)));
	}
}
