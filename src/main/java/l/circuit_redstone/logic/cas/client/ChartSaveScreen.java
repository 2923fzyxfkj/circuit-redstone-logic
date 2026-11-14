package l.circuit_redstone.logic.cas.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import l.circuit_redstone.logic.cas.debug.ChartSaver;

/**
 * 游戏内的"另存为"对话框：<b>目录</b> + <b>文件名</b> + 保存 / 取消 / 系统对话框。
 *
 * <p>为什么需要它：Gradle 的 {@code runClient} 会把 JVM 设成 {@code -Djava.awt.headless=true}，
 * AWT 原生 FileDialog 根本弹不出来（而且是静默跳过）。这个 Screen 不依赖 AWT，一定可见，
 * 而且目录可以改：填绝对路径，或填相对路径（相对游戏目录）。
 */
public final class ChartSaveScreen extends Screen {
	private static final int FIELD_WIDTH = 380;
	private static final int BUTTON_WIDTH = 150;
	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final int DIM_COLOR = 0xA0A0A0;
	private static final int ERROR_COLOR = 0xFF8080;

	private final byte[] payload;
	private final String suggestedName;
	private final Consumer<Path> onSaved;

	private TextFieldWidget folderField;
	private TextFieldWidget nameField;
	private Text status = Text.empty();

	public ChartSaveScreen(byte[] payload, String suggestedName, Consumer<Path> onSaved) {
		super(Text.translatable(titleKeyFor(suggestedName)));
		this.payload = payload;
		this.suggestedName = suggestedName;
		this.onSaved = onSaved;
	}

	/** 标题跟着内容走：运行图 / 视频。 */
	private static String titleKeyFor(String suggestedName) {
		return extensionOf(suggestedName).equals(".gif")
				? "screen.circuit-redstone_logic.chart_save.title_video"
				: "screen.circuit-redstone_logic.chart_save.title";
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int fieldX = centerX - FIELD_WIDTH / 2;

		this.folderField = new TextFieldWidget(this.textRenderer, fieldX, 74, FIELD_WIDTH, 20,
				Text.translatable("screen.circuit-redstone_logic.chart_save.folder_label"));
		this.folderField.setText(ChartSaver.directory().toString());
		this.folderField.setMaxLength(512);
		this.addDrawableChild(this.folderField);

		this.nameField = new TextFieldWidget(this.textRenderer, fieldX, 116, FIELD_WIDTH, 20,
				Text.translatable("screen.circuit-redstone_logic.chart_save.file_label"));
		this.nameField.setText(this.suggestedName);
		this.nameField.setMaxLength(96);
		this.addDrawableChild(this.nameField);

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("screen.circuit-redstone_logic.chart_save.save"), button -> save())
				.position(centerX - BUTTON_WIDTH - 4, 148).width(BUTTON_WIDTH).build());
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("screen.circuit-redstone_logic.chart_save.cancel"), button -> close())
				.position(centerX + 4, 148).width(BUTTON_WIDTH).build());
		// 想靠系统对话框浏览文件夹时用这个（headless 环境下会被跳过）。
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("screen.circuit-redstone_logic.chart_save.system"), button -> systemDialog())
				.position(centerX - BUTTON_WIDTH / 2, 172).width(BUTTON_WIDTH).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		int centerX = this.width / 2;
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 40, TEXT_COLOR);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("screen.circuit-redstone_logic.chart_save.folder_label"), centerX, 62, DIM_COLOR);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("screen.circuit-redstone_logic.chart_save.file_label"), centerX, 104, DIM_COLOR);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("screen.circuit-redstone_logic.chart_save.hint"), centerX, 196, DIM_COLOR);
		if (!this.status.getString().isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer, this.status, centerX, 214, ERROR_COLOR);
		}
	}

	private void save() {
		String fileName = this.nameField.getText().trim();
		if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")
				|| fileName.contains(":")) {
			this.status = Text.translatable("screen.circuit-redstone_logic.chart_save.invalid");
			return;
		}
		// 扩展名跟随这次要保存的东西：运行图是 .png，视频是 .gif。
		// 玩家自己填了扩展名就照他的写，不硬塞 .png（否则 GIF 会变成 xxx.gif.png）。
		String extension = extensionOf(this.suggestedName);
		if (extensionOf(fileName).isEmpty() && !extension.isEmpty()) {
			fileName = fileName + extension;
		}
		Path directory;
		try {
			String folder = this.folderField.getText().trim();
			directory = folder.isEmpty() ? ChartSaver.directory() : Path.of(folder);
		} catch (InvalidPathException invalid) {
			this.status = Text.translatable("screen.circuit-redstone_logic.chart_save.invalid_folder",
					invalid.getMessage());
			return;
		}
		if (!directory.isAbsolute()) {
			directory = FabricLoader.getInstance().getGameDir().resolve(directory);
		}
		Path target = directory.resolve(fileName);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, this.payload);
		} catch (IOException failure) {
			this.status = Text.translatable("screen.circuit-redstone_logic.chart_save.failed",
					String.valueOf(failure));
			return;
		}
		this.onSaved.accept(target);
		this.close();
	}

	/** 取扩展名（含点，小写）；没有扩展名就返回空串。 */
	private static String extensionOf(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
	}

	/** 可选：调用系统原生"另存为"对话框（可以浏览文件夹，但 headless 环境不可用）。 */
	private void systemDialog() {
		String fileName = this.nameField.getText().trim();
		if (fileName.isEmpty()) {
			fileName = this.suggestedName;
		}
		ChartSaver.offerSaveDialog(this.payload, fileName, target -> {
			MinecraftClient client = this.client;
			if (client != null) {
				client.execute(this::close);
			}
			this.onSaved.accept(target);
		}, () -> {
			MinecraftClient client = this.client;
			if (client != null) {
				client.execute(() -> this.status = Text.translatable(
						"screen.circuit-redstone_logic.chart_save.no_system_dialog"));
			}
		});
	}
}
