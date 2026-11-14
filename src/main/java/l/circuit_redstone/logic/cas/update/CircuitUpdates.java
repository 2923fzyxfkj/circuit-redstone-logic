package l.circuit_redstone.logic.cas.update;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.CircuitBlock;
import l.circuit_redstone.logic.cas.block.WireBlock;

/**
 * 方块更新的唯一调度器（需求 §3 "防无限递归更新"的核心）。
 *
 * <p>约定：{@code neighborUpdate} / {@code onBlockAdded} / {@code onStateReplaced} / {@code scheduledTick}
 * 一律只调用 {@link #markDirty(World, BlockPos)} 入队，绝不在回调里计算或写方块；
 * 真正的求值集中在每个世界 tick 末尾的 {@link #flush(ServerWorld)} 中迭代完成。
 * 因为整个过程是工作队列循环（不是 Java 递归调用），同一 tick 内不可能产生递归方块更新。
 *
 * <p>"代数"（generation）机制：任何一次电平变化都会让代数 +1，
 * 导线网络记录自己是第几代算出来的；代数变了就重算。
 * 这样既能在同一 tick 内收敛到稳定值，又不会对没变化的网络反复做整网 BFS。
 *
 * <p>三重保险：
 * <ul>
 *   <li>{@link #STEP_BUDGET}：单 tick 处理步数上限，防止巨型电路卡死主线程；</li>
 *   <li>{@link #MAX_EVALUATIONS_PER_POSITION}：同一 tick 内同一位置被反复求值的次数上限，
 *       把"NOT 门自环"这类反馈降级成 1 tick 振荡器而不是死循环；</li>
 *   <li>{@link #FLUSHING}：重入锁，flush 期间新入队的位置由外层循环继续消费。</li>
 * </ul>
 */
public final class CircuitUpdates {
	/** 单个世界单个 tick 最多处理的位置数（吞吐上限）。 */
	private static final int STEP_BUDGET = 16384;
	/**
	 * 同一 tick 内同一位置最多被求值多少次——也就是"反馈电路在一个游戏刻里最多能推进几轮"。
	 *
	 * <p>原版红石的节奏是"每红石刻（2 游戏刻）推进一次"；把这里调大就相当于解除这个节流：
	 * 振荡器 / 反馈环可以在一个游戏刻里翻转多轮，电路内部速度只受 CPU 与本预算限制，
	 * 不再被"一 tick 一轮"钉死。代价是反馈环越活跃越费 CPU，所以必须有上限
	 * （超过上限的位置会被挂起，留到下一个 tick 继续，避免活锁）。
	 */
	private static final int MAX_EVALUATIONS_PER_POSITION = 32;

	private static final Map<World, Deque<BlockPos>> PENDING = new IdentityHashMap<>();
	private static final Set<World> FLUSHING = Collections.newSetFromMap(new IdentityHashMap<>());
	private static boolean initialized;

	private CircuitUpdates() {
	}

	/** 在 {@code onInitialize()} 中调用一次，注册 tick 与卸载事件。 */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_WORLD_TICK.register(CircuitUpdates::flush);
		ServerWorldEvents.UNLOAD.register((server, world) -> forget(world));
	}

	/** 标记某个位置需要重新求值。客户端与空世界直接忽略。 */
	public static void markDirty(World world, BlockPos pos) {
		if (world == null || world.isClient()) {
			return;
		}
		PENDING.computeIfAbsent(world, key -> new ArrayDeque<>()).add(pos.toImmutable());
	}

	private static void forget(World world) {
		PENDING.remove(world);
		FLUSHING.remove(world);
	}

	private static void flush(ServerWorld world) {
		Deque<BlockPos> queue = PENDING.get(world);
		if (queue == null || queue.isEmpty()) {
			return;
		}
		if (!FLUSHING.add(world)) {
			// 已经在 flush 中：本轮新入队的位置留给外层循环继续消费。
			return;
		}
		try {
			Map<BlockPos, Integer> evaluations = new HashMap<>();
			Map<BlockPos, Integer> wireStamps = new HashMap<>();
			Set<BlockPos> carryOver = new HashSet<>();
			// 用数组承载，便于在循环里递增。
			int[] generation = { 0 };
			int steps = 0;

			while (!queue.isEmpty()) {
				if (steps++ >= STEP_BUDGET) {
					break;
				}
				BlockPos pos = queue.poll();
				int evaluated = evaluations.getOrDefault(pos, 0);
				if (evaluated >= MAX_EVALUATIONS_PER_POSITION) {
					// 本 tick 已反复翻转：放弃本轮，留到下一个 tick 重新开始，避免活锁。
					carryOver.add(pos);
					continue;
				}
				evaluations.put(pos, evaluated + 1);
				if (!world.isChunkLoaded(pos)) {
					continue;
				}
				evaluate(world, pos, wireStamps, generation);
			}

			queue.addAll(carryOver);
		} finally {
			FLUSHING.remove(world);
		}
	}

	private static void evaluate(ServerWorld world, BlockPos pos, Map<BlockPos, Integer> wireStamps, int[] generation) {
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		if (block instanceof WireBlock) {
			Integer stamp = wireStamps.get(pos);
			if (stamp != null && stamp.intValue() == generation[0]) {
				// 本 tick 已经按当前状态算过这张网络，且此后没有任何电平变化。
				return;
			}
			if (WireNetwork.recalculate(world, pos, wireStamps, generation[0])) {
				generation[0]++;
			}
			return;
		}

		if (block instanceof CircuitBlock circuitBlock) {
			if (circuitBlock.refresh(world, pos, state)) {
				generation[0]++;
			}
		}
	}
}
