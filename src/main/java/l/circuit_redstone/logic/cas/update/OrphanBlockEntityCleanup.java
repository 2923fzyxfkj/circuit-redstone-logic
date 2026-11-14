package l.circuit_redstone.logic.cas.update;

import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.block.CircuitPart;

/**
 * 本模组方块实体的自愈。
 *
 * <p>处理两类历史遗留问题（都只在区块加载时做一次，不碰别人的数据）：
 * <ol>
 *   <li><b>孤儿实体</b>：方块被拆掉后实体还留在区块里。根因是本模组的
 *       {@code onStateReplaced} 曾经提前 return、没调用 {@code super}
 *       （父类 {@code AbstractBlock#onStateReplaced} 才是移除方块实体的地方）。
 *       现在覆盖都调用了 {@code super}，这里只负责清掉旧存档里已经残留的那些。
 *       否则日志会刷 {@code Block entity ... invalid for ticking}，存档也会越存越大。</li>
 *   <li><b>类型不匹配</b>：早期版本把亮度传感器登记在共享的 {@code circuit} 类型下，
 *       后来拆成了独立的 {@code light_sensor} 类型；旧存档里的实体类型对不上，
 *       原版就不会再驱动它的 ticker（传感器停止轮询，日志同样刷 {@code invalid for ticking}）。
 *       这里用方块自己的工厂换成本方块应有的实体。</li>
 * </ol>
 */
public final class OrphanBlockEntityCleanup {
	private OrphanBlockEntityCleanup() {
	}

	public static void init() {
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> purge(chunk));
	}

	private static void purge(WorldChunk chunk) {
		int removed = 0;
		int repaired = 0;
		// 先复制一份条目，避免"边遍历边删除"。
		for (Map.Entry<BlockPos, BlockEntity> entry : List.copyOf(chunk.getBlockEntities().entrySet())) {
			BlockPos pos = entry.getKey();
			BlockEntity blockEntity = entry.getValue();
			if (!isOurBlockEntity(blockEntity.getType())) {
				continue;
			}
			BlockState state = chunk.getBlockState(pos);
			boolean blockIsOurs = state.getBlock() instanceof CircuitPart;

			if (!blockIsOurs) {
				chunk.removeBlockEntity(pos);
				removed++;
				continue;
			}

			if (blockEntity.getType().supports(state)) {
				continue;
			}
			// 方块是我们的、类型不对：删掉旧实体，再用方块自己的工厂补一个正确类型。
			chunk.removeBlockEntity(pos);
			if (state.getBlock() instanceof BlockEntityProvider provider) {
				BlockEntity replacement = provider.createBlockEntity(pos, state);
				if (replacement != null) {
					chunk.addBlockEntity(replacement);
					repaired++;
				}
			}
		}
		if (removed > 0 || repaired > 0) {
			CircuitRedstone.LOGGER.info("[{}] chunk {}: removed {} orphan block entities, repaired {} mismatched ones",
					CircuitRedstone.MOD_ID, chunk.getPos(), removed, repaired);
		}
	}

	private static boolean isOurBlockEntity(BlockEntityType<?> type) {
		return type == ModRegistry.CIRCUIT_BLOCK_ENTITY
				|| type == ModRegistry.SCANNER_BLOCK_ENTITY
				|| type == ModRegistry.LIGHT_SENSOR_BLOCK_ENTITY
				|| type == ModRegistry.CONSTANT_BLOCK_ENTITY
				|| type == ModRegistry.STRING_VALUE_BLOCK_ENTITY;
	}
}
