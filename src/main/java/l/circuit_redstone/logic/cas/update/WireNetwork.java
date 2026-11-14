package l.circuit_redstone.logic.cas.update;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.CircuitRedstone;
import l.circuit_redstone.logic.cas.block.WireBlock;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;
import l.circuit_redstone.logic.cas.logic.WireRule;

/**
 * 导线网络求值。
 *
 * <p>整张网络一次性求值：网络电平 = 整张网络上是否存在外部信号源。
 * 因为导线是二进制且不衰减，若按"逐个方块看邻居"的方式求值，
 * 一个无源的导线环会互相供能而永久锁死；整网求值从根上避免这种自锁。
 *
 * <p>网络只由本模组导线组成（6 向、不衰减）。原版红石线<b>不是</b>导体，
 * 而是按 ADC 协议：读取贴着我方网络的那一格红石线的强度，`> 0` 即为高电平。
 * 红石线的强度由 {@link DustPower} 按原版衰减规则重算，并把"本次求值的导线网络"当作不存在，
 * 这样既保留衰减（远端强度 0 就是 0），又不会出现"导线点亮红石线、红石线又点亮导线"的自锁。
 *
 * <p>贴着红石线的网络会给自己安排一个周期性的方块计划刻（{@link #DUST_RESCAN_INTERVAL_TICKS}）：
 * 红石线只在自身强度变化时通知邻居，而"红石线周围的来源变化"（以及它被我方供电掩盖的变化）
 * 不会通知我们的导线。计划刻会随区块存档保留，所以世界重载后链条依然有效；纯导线网络不安排计划刻。
 *
 * <p>算法是迭代式 BFS（无递归）：收集网络 → 判定源 → 统一写回 → 定点通知邻居。
 */
public final class WireNetwork {
	/** 单张网络最多评估的位置数，超过则截断并告警。 */
	private static final int MAX_NETWORK_POSITIONS = 4096;
	/** 贴着红石线的网络的低频重算间隔（tick）。 */
	private static final int DUST_RESCAN_INTERVAL_TICKS = 10;
	private static final Direction[] DIRECTIONS = Direction.values();

	private WireNetwork() {
	}

	

	/**
	 * 重算包含 {@code start} 的整张导线网络。
	 *
	 * @param stamps 记录"这张网络是第几代算出来的"，供调度器判断是否需要重算
	 * @param stamp  当前代数
	 * @return 是否真的改变了任何导线的状态
	 */
	public static boolean recalculate(World world, BlockPos start, Map<BlockPos, Integer> stamps, int stamp) {
		if (!(world.getBlockState(start).getBlock() instanceof WireBlock)) {
			return false;
		}

		// 第一步：只收集本模组导线（互不衰减），同时记录边界。
		List<BlockPos> members = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		List<WireRule.Port> plainSources = new ArrayList<>();
		Set<BlockPos> dustBoundaries = new HashSet<>();
		boolean truncated = false;

		visited.add(start);
		open.add(start);
		while (!open.isEmpty()) {
			BlockPos pos = open.poll();
			members.add(pos);
			if (members.size() > MAX_NETWORK_POSITIONS) {
				truncated = true;
				break;
			}
			for (Direction direction : DIRECTIONS) {
				BlockPos neighborPos = pos.offset(direction);
				if (!world.isChunkLoaded(neighborPos)) {
					continue;
				}
				BlockState neighborState = world.getBlockState(neighborPos);
				if (neighborState.getBlock() instanceof WireBlock) {
					if (visited.add(neighborPos)) {
						open.add(neighborPos);
					}
					continue;
				}
				if (neighborState.isOf(Blocks.REDSTONE_WIRE)) {
					dustBoundaries.add(neighborPos);
					continue;
				}
				plainSources.add(new WireRule.Port(neighborPos, direction));
			}
		}

		if (truncated) {
			CircuitRedstone.LOGGER.warn("[{}] wire network at {} exceeds {} positions; evaluation truncated",
					CircuitRedstone.MOD_ID, start.toShortString(), MAX_NETWORK_POSITIONS);
		}

		// 第二步：判定源。红石线按"排除本网络供电后的原生强度"参与，其余方块直接读弱电。
		boolean hasSource = false;
		Map<BlockPos, Integer> dustPowerCache = new HashMap<>();
		for (BlockPos dustPos : dustBoundaries) {
			if (DustPower.independentPower(WorldCircuitContext.of(world), dustPos, visited, dustPowerCache) > SignalProtocol.MIN_LEVEL) {
				hasSource = true;
				break;
			}
		}
		if (!hasSource) {
			hasSource = WireRule.hasAnySource(plainSources, (emitterPos, directionFromReader) ->
					CircuitSignals.readWeakSourceLevel(WorldCircuitContext.of(world), emitterPos, directionFromReader));
		}

		// 第三步：统一写回，只有变化的位置才通知邻居。
		List<BlockPos> changed = new ArrayList<>();
		for (BlockPos pos : members) {
			stamps.put(pos, stamp);
			BlockState state = world.getBlockState(pos);
			Block block = state.getBlock();
			if (!(block instanceof WireBlock)) {
				continue;
			}
			if (state.get(WireBlock.POWERED) == hasSource) {
				continue;
			}
			// 只发客户端通知，不触发原版邻居级联；邻居由下面显式定点通知。
			world.setBlockState(pos, state.with(WireBlock.POWERED, hasSource), Block.NOTIFY_LISTENERS);
			changed.add(pos);
		}

		for (BlockPos pos : changed) {
			world.updateNeighborsAlways(pos, world.getBlockState(pos).getBlock());
		}

		if (!dustBoundaries.isEmpty()) {
			// 自我续期的低频计划刻：保证"红石线周围来源变化"这类收不到通知的情况也能收敛。
			world.scheduleBlockTick(start, world.getBlockState(start).getBlock(), DUST_RESCAN_INTERVAL_TICKS);
		}

		return !changed.isEmpty();
	}
}

