package l.circuit_redstone.logic.cas.update;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;

/**
 * 原版红石线强度的"独立"求值（真实世界与调试沙盒共用）。
 *
 * <p>按原版规则重算某段红石线的强度，但把"本次求值的导线网络"当作不存在：
 * <pre>
 * power(红石线) = max( 非红石线邻居的弱电, 相邻红石线的 power - 1 )
 * </pre>
 * 于是既保留逐格衰减（远端强度 0 就是 0），又不会出现"导线点亮红石线、红石线又点亮导线"的自锁。
 */
final class DustPower {
	/** 单次求值最多遍历多少格红石线。 */
	private static final int MAX_COMPONENT_POSITIONS = 4096;
	private static final Direction[] DIRECTIONS = Direction.values();

	private DustPower() {
	}

	/**
	 * @param context          世界视图
	 * @param start            起始红石线位置
	 * @param excludedEmitters 本次求值的导线网络位置（视为不供电）
	 * @param componentCache   同一次求值内的缓存：红石线位置 → 独立强度
	 * @return 0..{@link SignalProtocol#MAX_LEVEL}
	 */
	static int independentPower(CircuitContext context, BlockPos start, Set<BlockPos> excludedEmitters,
			Map<BlockPos, Integer> componentCache) {
		Integer cached = componentCache.get(start);
		if (cached != null) {
			return cached;
		}

		List<BlockPos> component = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		visited.add(start);
		open.add(start);

		while (!open.isEmpty()) {
			BlockPos pos = open.poll();
			component.add(pos);
			if (component.size() > MAX_COMPONENT_POSITIONS) {
				break;
			}
			for (Direction direction : DIRECTIONS) {
				BlockPos neighborPos = pos.offset(direction);
				if (context.getBlockState(neighborPos).isOf(Blocks.REDSTONE_WIRE) && visited.add(neighborPos)) {
					open.add(neighborPos);
				}
			}
		}

		// 基础电源：非红石线邻居的弱电（排除本次求值的导线网络）。
		Map<BlockPos, Integer> power = new HashMap<>();
		for (BlockPos pos : component) {
			int base = SignalProtocol.MIN_LEVEL;
			for (Direction direction : DIRECTIONS) {
				BlockPos neighborPos = pos.offset(direction);
				BlockState neighborState = context.getBlockState(neighborPos);
				if (neighborState.isOf(Blocks.REDSTONE_WIRE)) {
					continue;
				}
				if (excludedEmitters.contains(neighborPos)) {
					continue;
				}
				int level = CircuitSignals.readWeakSourceLevel(context, neighborPos, direction);
				if (level > base) {
					base = level;
				}
			}
			power.put(pos, base);
		}

		// 红石线之间每格衰减 1：单调松弛直到稳定（值只增不减且上限 15，必然收敛）。
		Deque<BlockPos> queue = new ArrayDeque<>(component);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int current = power.get(pos);
			if (current <= SignalProtocol.MIN_LEVEL) {
				continue;
			}
			int candidate = current - 1;
			for (Direction direction : DIRECTIONS) {
				BlockPos neighborPos = pos.offset(direction);
				Integer neighborPower = power.get(neighborPos);
				if (neighborPower == null) {
					continue;
				}
				if (candidate > neighborPower) {
					power.put(neighborPos, candidate);
					queue.add(neighborPos);
				}
			}
		}

		for (BlockPos pos : component) {
			componentCache.put(pos, power.get(pos));
		}
		return power.get(start);
	}
}
