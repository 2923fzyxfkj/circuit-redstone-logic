package l.circuit_redstone.logic.cas.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.block.entity.ColorBlockEntity;
import l.circuit_redstone.logic.cas.logic.ColorHex;

/**
 * 纯色方块：放下时是黑色，用 {@code /circuit-set <十六进制>} 改颜色。
 *
 * <p>颜色存在方块实体里（24 位 RGB），客户端通过 {@code ColorProviderRegistry} 给方块的
 * 白色贴图着色。因为颜色不是 BlockState 的一部分，客户端不会自动重建网格，所以改色后会：
 * <ol>
 *   <li>把方块实体数据单独发给看得到它的客户端（{@link ColorBlockEntity#syncToClients()}）；</li>
 *   <li>安排一个 1 tick 后的计划刻，把 {@link #SHADE} 换个值——纯属"逼客户端重建网格"，
 *       此时客户端已经拿到新颜色，重建立刻生效。</li>
 * </ol>
 *
 * <p>它不参与红石逻辑，也不参与数据线的读写（数据线只认常量 / 字符串）。
 */
public class ColorBlock extends CircuitValueBlock {
	/** 默认颜色：黑色。 */
	public static final int DEFAULT_COLOR = 0x000000;
	/** 变体数量：仅用于触发客户端网格重建。 */
	private static final int SHADE_VARIANTS = 4;
	/** 纯占位的状态属性：值本身没有含义，只要求"变一下"。 */
	public static final IntProperty SHADE = IntProperty.of("shade", 0, SHADE_VARIANTS - 1);

	public ColorBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(SHADE, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(SHADE);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return ColorBlockEntity.create(pos, state);
	}

	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		world.setBlockState(pos, state.with(SHADE, nextShade(state.get(SHADE))), NOTIFY_LISTENERS);
	}

	/** 请求客户端重建一次网格（颜色变了以后调用）。 */
	public static void requestClientRefresh(World world, BlockPos pos) {
		world.scheduleBlockTick(pos, ModRegistry.COLOR_BLOCK, 1);
	}

	private static int nextShade(int shade) {
		return shade + 1 >= SHADE_VARIANTS ? 0 : shade + 1;
	}

	/** 默认颜色的十六进制文本（反馈文案用）。 */
	public static String defaultColorText() {
		return ColorHex.format(DEFAULT_COLOR);
	}
}
