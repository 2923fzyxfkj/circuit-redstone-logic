package l.circuit_redstone.logic.cas;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import l.circuit_redstone.logic.cas.block.AmplifierBlock;
import l.circuit_redstone.logic.cas.block.BrightnessSensorBlock;
import l.circuit_redstone.logic.cas.block.CircuitBlock;
import l.circuit_redstone.logic.cas.block.ClockBlock;
import l.circuit_redstone.logic.cas.block.ColorBlock;
import l.circuit_redstone.logic.cas.block.ComparatorGateBlock;
import l.circuit_redstone.logic.cas.block.ConstantBlock;
import l.circuit_redstone.logic.cas.block.DataWireBlock;
import l.circuit_redstone.logic.cas.block.HiddenSignBlock;
import l.circuit_redstone.logic.cas.block.HoverBlock;
import l.circuit_redstone.logic.cas.block.LimiterBlock;
import l.circuit_redstone.logic.cas.block.LogicGateBlock;
import l.circuit_redstone.logic.cas.block.ScannerBlock;
import l.circuit_redstone.logic.cas.block.StringValueBlock;
import l.circuit_redstone.logic.cas.block.VariableBlock;
import l.circuit_redstone.logic.cas.block.WireBlock;
import l.circuit_redstone.logic.cas.block.entity.CircuitBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.HiddenSignBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.ClockBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.ColorBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.ConstantBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.LightSensorBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.ScannerBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.StringValueBlockEntity;
import l.circuit_redstone.logic.cas.block.entity.VariableBlockEntity;
import l.circuit_redstone.logic.cas.item.TooltipBlockItem;
import l.circuit_redstone.logic.cas.item.TooltipItem;
import l.circuit_redstone.logic.cas.item.VariableBlockItem;
import l.circuit_redstone.logic.cas.logic.GateKind;

/**
 * 注册中心。所有静态实例都在 {@link #init()} 里（也就是在主类 onInitialize 期间）创建并注册，
 * 不在类加载期做任何注册动作。
 */
public final class ModRegistry {
	public static WireBlock WIRE;

	public static LogicGateBlock AND_GATE;
	public static LogicGateBlock OR_GATE;
	public static LogicGateBlock NOT_GATE;
	public static LogicGateBlock XOR_GATE;

	public static LimiterBlock LIMITER;
	/** 比较器：后方常量与右方常量按左侧红石等级（1..5）比较，成立输出高 bit。 */
	public static ComparatorGateBlock COMPARATOR;
	/** 时钟：每 {@code /circuit-set} 设置的 tick 数输出一个 tick 的高电平。 */
	public static ClockBlock CLOCK;
	public static ScannerBlock SCANNER;
	/** 信号增强器：输入激活即输出 15，零延迟。 */
	public static AmplifierBlock AMPLIFIER;
	/** 亮度传感器：输出与自身光照等级相同的信号强度。 */
	public static BrightnessSensorBlock BRIGHTNESS_SENSOR;
	/** 常量：保存一个整数，供扫描器取值输出（自身不输出红石信号）。 */
	public static ConstantBlock CONSTANT;
	/** 字符串：保存一段文本（目前不参与红石逻辑）。 */
	public static StringValueBlock STRING_VALUE;
	/** 变量：一个方块存一个 int 或 str，数据线可读可写，挖掉后数据跟着物品走。 */
	public static VariableBlock VARIABLE;
	/** 数据线：把背面的常量/字符串写进正面的数据方块（支持串联）。 */
	public static DataWireBlock DATA_WIRE;
	/** 纯色方块：颜色可写（十六进制），客户端按颜色着色。 */
	public static ColorBlock COLOR_BLOCK;
	/** 悬空方块：完整方块（能托红石粉），但玩家可以穿过，只画一圈白线。 */
	public static HoverBlock HOVER_BLOCK;
	/** 隐形告示牌：背景不可见，只用方块实体渲染器画文字。 */
	public static HiddenSignBlock HIDDEN_SIGN;

	/** 电路终端：持有它才能使用 /circuit-terminal。 */
	public static Item TERMINAL;

	public static BlockEntityType<CircuitBlockEntity> CIRCUIT_BLOCK_ENTITY;
	public static BlockEntityType<ScannerBlockEntity> SCANNER_BLOCK_ENTITY;
	public static BlockEntityType<LightSensorBlockEntity> LIGHT_SENSOR_BLOCK_ENTITY;
	public static BlockEntityType<ConstantBlockEntity> CONSTANT_BLOCK_ENTITY;
	public static BlockEntityType<StringValueBlockEntity> STRING_VALUE_BLOCK_ENTITY;
	public static BlockEntityType<VariableBlockEntity> VARIABLE_BLOCK_ENTITY;
	public static BlockEntityType<HiddenSignBlockEntity> HIDDEN_SIGN_BLOCK_ENTITY;
	public static BlockEntityType<ColorBlockEntity> COLOR_BLOCK_ENTITY;
	public static BlockEntityType<ClockBlockEntity> CLOCK_BLOCK_ENTITY;

	private static RegistryKey<ItemGroup> itemGroupKey;

	private static final List<Block> BLOCKS = new ArrayList<>();

	private ModRegistry() {
	}

	public static void init() {
		WIRE = registerBlock("wire", new WireBlock(wireSettings()));

		AND_GATE = registerBlock(GateKind.AND.id() + "_gate", new LogicGateBlock(GateKind.AND, circuitSettings()));
		OR_GATE = registerBlock(GateKind.OR.id() + "_gate", new LogicGateBlock(GateKind.OR, circuitSettings()));
		NOT_GATE = registerBlock(GateKind.NOT.id() + "_gate", new LogicGateBlock(GateKind.NOT, circuitSettings()));
		XOR_GATE = registerBlock(GateKind.XOR.id() + "_gate", new LogicGateBlock(GateKind.XOR, circuitSettings()));

		LIMITER = registerBlock("limiter", new LimiterBlock(circuitSettings()));
		COMPARATOR = registerBlock("comparator", new ComparatorGateBlock(circuitSettings()));
		CLOCK = registerBlock("clock", new ClockBlock(circuitSettings()));
		SCANNER = registerBlock("scanner", new ScannerBlock(circuitSettings()));
		AMPLIFIER = registerBlock("amplifier", new AmplifierBlock(circuitSettings()));
		BRIGHTNESS_SENSOR = registerBlock("brightness_sensor", new BrightnessSensorBlock(sensorSettings()));
		CONSTANT = registerBlock("constant", new ConstantBlock(circuitSettings()));
		STRING_VALUE = registerBlock("string_value", new StringValueBlock(circuitSettings()));
		VARIABLE = registerBlock("variable", new VariableBlock(circuitSettings()),
				(block, key) -> new VariableBlockItem(block, new Item.Settings(), key));
		DATA_WIRE = registerBlock("data_wire", new DataWireBlock(circuitSettings()));
		COLOR_BLOCK = registerBlock("color_block", new ColorBlock(circuitSettings()));
		HOVER_BLOCK = registerBlock("hover_block", new HoverBlock(circuitSettings()));
		HIDDEN_SIGN = registerBlock("hidden_sign", new HiddenSignBlock(circuitSettings()));

		TERMINAL = Registry.register(Registries.ITEM, CircuitRedstone.id("terminal"),
				new TooltipItem(new Item.Settings().maxCount(1), tooltipKey("terminal")));

		CIRCUIT_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("circuit"),
				BlockEntityType.Builder.create(CircuitBlockEntity::create,
						AND_GATE, OR_GATE, NOT_GATE, XOR_GATE, LIMITER, COMPARATOR, AMPLIFIER)
						.build(null));
		CLOCK_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("clock"),
				BlockEntityType.Builder.create(ClockBlockEntity::create, CLOCK).build(null));
		SCANNER_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("scanner"),
				BlockEntityType.Builder.create(ScannerBlockEntity::create, SCANNER).build(null));
		LIGHT_SENSOR_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("light_sensor"),
				BlockEntityType.Builder.create(LightSensorBlockEntity::create, BRIGHTNESS_SENSOR).build(null));
		CONSTANT_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("constant"),
				BlockEntityType.Builder.create(ConstantBlockEntity::create, CONSTANT).build(null));
		STRING_VALUE_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("string_value"),
				BlockEntityType.Builder.create(StringValueBlockEntity::create, STRING_VALUE).build(null));
		VARIABLE_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("variable"),
				BlockEntityType.Builder.create(VariableBlockEntity::create, VARIABLE).build(null));
		HIDDEN_SIGN_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("hidden_sign"),
				BlockEntityType.Builder.create(HiddenSignBlockEntity::create, HIDDEN_SIGN).build(null));
		COLOR_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, CircuitRedstone.id("color_block"),
				BlockEntityType.Builder.create(ColorBlockEntity::create, COLOR_BLOCK).build(null));

		registerItemGroup();
	}

	private static void registerItemGroup() {
		itemGroupKey = RegistryKey.of(RegistryKeys.ITEM_GROUP, CircuitRedstone.id("circuit"));
		Registry.register(Registries.ITEM_GROUP, itemGroupKey, FabricItemGroup.builder()
				.displayName(Text.translatable("itemGroup." + CircuitRedstone.MOD_ID + ".circuit"))
				.icon(() -> new ItemStack(AND_GATE))
				.build());
		ItemGroupEvents.modifyEntriesEvent(itemGroupKey)
				.register(entries -> {
					BLOCKS.forEach(block -> entries.add(new ItemStack(block)));
					entries.add(new ItemStack(TERMINAL));
				});
	}

	/**
	 * 注册一个方块 + 对应的方块物品 + 物品说明。
	 *
	 * <p>物品类型默认是 {@link TooltipBlockItem}；需要额外行为（例如变量方块的"当前值"提示
	 * 与放置后恢复）时通过 {@code itemFactory} 换成子类。
	 */
	private static <T extends Block> T registerBlock(String name, T block) {
		return registerBlock(name, block,
				(registered, key) -> new TooltipBlockItem(registered, new Item.Settings(), key));
	}

	private static <T extends Block> T registerBlock(String name, T block, BlockItemFactory itemFactory) {
		Registry.register(Registries.BLOCK, CircuitRedstone.id(name), block);
		Registry.register(Registries.ITEM, CircuitRedstone.id(name),
				itemFactory.create(block, tooltipKey(name)));
		BLOCKS.add(block);
		return block;
	}

	/** 方块物品工厂（{@code (方块, tooltip 键) -> 物品}）。 */
	@FunctionalInterface
	private interface BlockItemFactory {
		Item create(Block block, String tooltipKey);
	}

	/** 物品说明文本的翻译键：tooltip.circuit-redstone_logic.&lt;id&gt;。 */
	public static String tooltipKey(String path) {
		return "tooltip." + CircuitRedstone.MOD_ID + "." + path;
	}

	private static AbstractBlock.Settings wireSettings() {
		return AbstractBlock.Settings.create()
				.breakInstantly()
				.nonOpaque()
				.notSolid()
				.sounds(BlockSoundGroup.STONE);
	}

	/**
	 * 电路方块通用设置。
	 *
	 * <p>注意：<b>不设置 luminance</b>。方块自己发光会被亮度传感器读成"环境光"
	 * （自己和邻居的光都算），读数就没意义了；通电状态改用贴图区分（{@code <id>_on} 模型）。
	 */
	private static AbstractBlock.Settings circuitSettings() {
		return AbstractBlock.Settings.create()
				.strength(0.5f)
				.sounds(BlockSoundGroup.STONE);
	}

	/**
	 * 亮度传感器：<b>遮光</b>的实心方块（所以它不会让天光从自己身上漏过去），
	 * 读数取它四周相邻位置的最高光照；同样不能发光。
	 */
	private static AbstractBlock.Settings sensorSettings() {
		return AbstractBlock.Settings.create()
				.strength(0.5f)
				.sounds(BlockSoundGroup.STONE);
	}

	/** 已注册的方块数量（主类启动日志用）。 */
	public static int blockCount() {
		return BLOCKS.size();
	}

	/** 本模组注册的全部物品 id（{@code /circuit-terminal list} 用，动态获取）。 */
	public static List<Identifier> registeredItemIds() {
		return Registries.ITEM.getIds().stream()
				.filter(id -> CircuitRedstone.MOD_ID.equals(id.getNamespace()))
				.toList();
	}
}
