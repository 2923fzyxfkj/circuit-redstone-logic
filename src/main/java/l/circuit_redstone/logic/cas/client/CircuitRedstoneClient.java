package l.circuit_redstone.logic.cas.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.block.entity.ColorBlockEntity;

/**
 * 客户端初始化，两件事：
 * <ol>
 *   <li>给"纯色方块"按方块实体里的颜色着色（白模型 × tint）；</li>
 *   <li>把"悬空方块"放进 <b>cutout</b> 渲染层——它的贴图是"不白白框 + 中间全透明"，
 *       必须按 alpha 阈值裁掉中间像素，solid 层会把中间渲染成白块。</li>
 * </ol>
 */
public final class CircuitRedstoneClient implements ClientModInitializer {
	/** 不着色时的乘数（保持贴图原样）。 */
	private static final int NO_TINT = 0xFFFFFF;

	@Override
	public void onInitializeClient() {
		ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> colorAt(world, pos),
				ModRegistry.COLOR_BLOCK);
		BlockRenderLayerMap.INSTANCE.putBlock(ModRegistry.HOVER_BLOCK, RenderLayer.getCutout());
		// 隐形告示牌：方块本体是全透明贴图，必须走 cutout；否则 solid 层会把透明像素渲染成黑块。
		BlockRenderLayerMap.INSTANCE.putBlock(ModRegistry.HIDDEN_SIGN, RenderLayer.getCutout());
		BlockEntityRendererRegistry.register(ModRegistry.HIDDEN_SIGN_BLOCK_ENTITY, HiddenSignRenderer::new);
	}

	private static int colorAt(BlockRenderView world, BlockPos pos) {
		if (world == null || pos == null) {
			return NO_TINT;
		}
		return world.getBlockEntity(pos) instanceof ColorBlockEntity colorBlock ? colorBlock.getColor() : NO_TINT;
	}
}
