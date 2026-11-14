package l.circuit_redstone.logic.cas.sim;

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
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.BrightnessSensorBlock;
import l.circuit_redstone.logic.cas.block.CircuitBlock;
import l.circuit_redstone.logic.cas.block.ConstantBlock;
import l.circuit_redstone.logic.cas.block.ScannerBlock;
import l.circuit_redstone.logic.cas.block.WireBlock;
import l.circuit_redstone.logic.cas.logic.CircuitContext;
import l.circuit_redstone.logic.cas.logic.SignalProtocol;
import l.circuit_redstone.logic.cas.logic.WireRule;
import l.circuit_redstone.logic.cas.update.CircuitSignals;
import l.circuit_redstone.logic.cas.update.WorldCircuitContext;

/**
 * 推演沙盒：起点所在导线网络的内存快照 + 一份 {@link CircuitContext} 实现。
 *
 * <p>快照在<b>主线程</b>用只读方式采集（{@code World} 不能跨线程访问），
 * 之后所有推演都在后台线程对这份内存数据做，不会碰真实世界。
 */
public final class SimulationSandbox implements CircuitContext {
	private static final BlockState AIR = Blocks.AIR.getDefaultState();

	/** 边界源键：位置 + 由读取方指向发射方的方向。 */
	public record PortKey(BlockPos pos, Direction direction) {
	}

	/**
	 * 沙盒中的一段导线网络。
	 *
	 * @param members       成员位置
	 * @param ports         边界候选信号源
	 * @param containsStart 是否包含注入脉冲的起点
	 */
	public record Network(List<BlockPos> members, List<WireRule.Port> ports, boolean containsStart) {
	}

	private final BlockPos start;
	private final List<BlockPos> orderedKeys;
	private final Map<BlockPos, BlockState> states = new HashMap<>();
	private final List<Network> networks = new ArrayList<>();
	private final List<BlockPos> circuitBlocks = new ArrayList<>();
	private final Map<PortKey, Integer> vanillaLevels = new HashMap<>();
	private final Map<PortKey, Integer> vanillaWeakLevels = new HashMap<>();
	private final Map<BlockPos, Integer> lightLevels = new HashMap<>();
	/** 快照里出现的常量方块的值（值在方块实体里，需要单独采集）。 */
	private final Map<BlockPos, Integer> constantValues = new HashMap<>();
	/** 沙盒边界外侧邻居的状态（只用于让红石线等原版方块的电平可读，不参与推演）。 */
	private final Map<BlockPos, BlockState> edgeStates = new HashMap<>();
	private final boolean truncated;

	private SimulationSandbox(BlockPos start, List<BlockPos> orderedKeys, boolean truncated) {
		this.start = start;
		this.orderedKeys = orderedKeys;
		this.truncated = truncated;
	}

	/**
	 * 起点对应的导线种子：起点本身是导线就是它自己；否则取它六个相邻方向上的导线
	 * （这样 {@code test start} 也可以从逻辑门的坐标开始，推演它周边连接的那些网络）。
	 *
	 * @return 起点既不是导线、六邻也没有导线时返回空列表
	 */
	static List<BlockPos> seedsOf(World world, BlockPos start) {
		if (world.getBlockState(start).getBlock() instanceof WireBlock) {
			return List.of(start);
		}
		List<BlockPos> seeds = new ArrayList<>();
		for (Direction direction : Direction.values()) {
			BlockPos neighborPos = start.offset(direction);
			if (world.isChunkLoaded(neighborPos) && world.getBlockState(neighborPos).getBlock() instanceof WireBlock) {
				seeds.add(neighborPos);
			}
		}
		return seeds;
	}

	/**
	 * 采集起点所在的导线网络（只读世界，不改动任何东西）。
	 *
	 * @return 起点四周都没有导线时返回 null；网络超过上限时快照会被截断（{@link #truncated()}）
	 */
	public static SimulationSandbox capture(World world, BlockPos start, int maxNodes) {
		List<BlockPos> seeds = seedsOf(world, start);
		if (seeds.isEmpty()) {
			return null;
		}
		WorldCircuitContext worldContext = WorldCircuitContext.of(world);

		Map<BlockPos, BlockState> states = new HashMap<>();
		Set<BlockPos> wires = new HashSet<>();
		List<BlockPos> wireOrder = new ArrayList<>();
		Deque<BlockPos> open = new ArrayDeque<>();

		for (BlockPos seed : seeds) {
			wires.add(seed);
			wireOrder.add(seed);
			states.put(seed, world.getBlockState(seed));
			open.add(seed);
		}
		Set<BlockPos> seedSet = new HashSet<>(seeds);
		boolean truncated = false;

		while (!open.isEmpty()) {
			BlockPos pos = open.poll();
			for (Direction direction : Direction.values()) {
				BlockPos neighborPos = pos.offset(direction);
				if (wires.contains(neighborPos) || !world.isChunkLoaded(neighborPos)) {
					continue;
				}
				BlockState neighborState = world.getBlockState(neighborPos);
				if (!(neighborState.getBlock() instanceof WireBlock)) {
					continue;
				}
				if (wires.size() >= maxNodes) {
					truncated = true;
					continue;
				}
				wires.add(neighborPos);
				wireOrder.add(neighborPos);
				states.put(neighborPos, neighborState);
				open.add(neighborPos);
			}
		}

		// 相邻电路方块：让沙盒里的门/限制器/增强器/扫描器/传感器也能被推演
		List<BlockPos> circuitBlocks = new ArrayList<>();
		for (BlockPos pos : wireOrder) {
			for (Direction direction : Direction.values()) {
				BlockPos neighborPos = pos.offset(direction);
				if (states.containsKey(neighborPos)) {
					continue;
				}
				BlockState neighborState = world.getBlockState(neighborPos);
				if (neighborState.getBlock() instanceof CircuitBlock && states.size() < maxNodes * 2) {
					states.put(neighborPos, neighborState);
					circuitBlocks.add(neighborPos);
				}
			}
		}

		// 扫描器的射线格也放进快照，否则沙盒里数不到方块
		for (BlockPos pos : circuitBlocks) {
			BlockState state = states.get(pos);
			if (!(state.getBlock() instanceof ScannerBlock)) {
				continue;
			}
			Direction facing = state.get(CircuitBlock.FACING);
			for (Direction direction : List.of(facing.rotateYCounterclockwise(), facing.rotateYClockwise(),
					Direction.UP)) {
				for (int step = 1; step <= ScannerBlock.SCAN_RANGE; step++) {
					BlockPos cell = pos.offset(direction, step);
					if (states.containsKey(cell)) {
						continue;
					}
					BlockState cellState = world.getBlockState(cell);
					if (!cellState.isAir()) {
						states.put(cell, cellState);
					}
				}
			}
		}

		// 常量方块的值保存在方块实体里（不在 BlockState 里），因此要单独采集一份，
		// 否则沙盒里的扫描器读不到常量、推演结果会和真实世界不一致。
		Map<BlockPos, Integer> constantValues = new HashMap<>();
		for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
			if (entry.getValue().getBlock() instanceof ConstantBlock) {
				constantValues.put(entry.getKey(), ConstantBlock.valueAt(world, entry.getKey()));
			}
		}

		List<BlockPos> orderedKeys = new ArrayList<>(states.keySet());
		orderedKeys.sort((a, b) -> {
			int cmp = Integer.compare(a.getY(), b.getY());
			if (cmp != 0) {
				return cmp;
			}
			cmp = Integer.compare(a.getX(), b.getX());
			return cmp != 0 ? cmp : Integer.compare(a.getZ(), b.getZ());
		});

		SimulationSandbox sandbox = new SimulationSandbox(start, orderedKeys, truncated);
		sandbox.states.putAll(states);
		sandbox.circuitBlocks.addAll(circuitBlocks);
		sandbox.constantValues.putAll(constantValues);

		// 导线网络分组 + 边界源采集（弱电口径）
		Set<BlockPos> assigned = new HashSet<>();
		for (BlockPos seed : wireOrder) {
			if (!assigned.add(seed)) {
				continue;
			}
			List<BlockPos> members = new ArrayList<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			queue.add(seed);
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				members.add(pos);
				for (Direction direction : Direction.values()) {
					BlockPos neighborPos = pos.offset(direction);
					if (wires.contains(neighborPos) && assigned.add(neighborPos)) {
						queue.add(neighborPos);
					}
				}
			}

			List<WireRule.Port> ports = new ArrayList<>();
			for (BlockPos pos : members) {
				for (Direction direction : Direction.values()) {
					BlockPos neighborPos = pos.offset(direction);
					if (wires.contains(neighborPos)) {
						continue;
					}
					// 边界邻居一律作为候选源；沙盒内的电路方块会在求值时读到它"当前"的电平。
					ports.add(new WireRule.Port(neighborPos, direction));
					PortKey key = new PortKey(neighborPos, direction);
					sandbox.edgeStates.putIfAbsent(neighborPos, world.getBlockState(neighborPos));
					sandbox.vanillaWeakLevels.putIfAbsent(key,
							CircuitSignals.readWeakSourceLevel(worldContext, neighborPos, direction));
				}
			}
			sandbox.networks.add(new Network(List.copyOf(members), List.copyOf(ports),
					members.stream().anyMatch(seedSet::contains)));
		}

		// 电路方块端口（沙盒外的邻居）按采集值参与求值
		for (BlockPos pos : circuitBlocks) {
			for (Direction direction : Direction.values()) {
				BlockPos neighborPos = pos.offset(direction);
				if (states.containsKey(neighborPos)) {
					continue;
				}
				sandbox.edgeStates.putIfAbsent(neighborPos, world.getBlockState(neighborPos));
				PortKey key = new PortKey(neighborPos, direction);
				sandbox.vanillaLevels.putIfAbsent(key,
						CircuitSignals.readLevel(worldContext, neighborPos, direction));
				sandbox.vanillaWeakLevels.putIfAbsent(key,
						CircuitSignals.readWeakSourceLevel(worldContext, neighborPos, direction));
			}
		}

		// 亮度传感器：采集自身与六邻的光照
		for (BlockPos pos : circuitBlocks) {
			if (!(sandbox.states.get(pos).getBlock() instanceof BrightnessSensorBlock)) {
				continue;
			}
			sandbox.lightLevels.put(pos, world.getLightLevel(pos));
			for (Direction direction : Direction.values()) {
				BlockPos neighborPos = pos.offset(direction);
				sandbox.lightLevels.put(neighborPos, world.getLightLevel(neighborPos));
			}
		}

		return sandbox;
	}

	/* ---------------------------------------------------------------- 访问 */

	public BlockPos start() {
		return start;
	}

	public boolean truncated() {
		return truncated;
	}

	public List<Network> networks() {
		return List.copyOf(networks);
	}

	public List<BlockPos> circuitBlocks() {
		return List.copyOf(circuitBlocks);
	}

	public void set(BlockPos pos, BlockState state) {
		states.put(pos, state);
	}

	/** 边界弱电（导线网络判定源用）。 */
	public int weakLevel(BlockPos pos, Direction directionFromReaderToEmitter) {
		return vanillaWeakLevels.getOrDefault(new PortKey(pos, directionFromReaderToEmitter), 0);
	}

	/** 当前全局配置的 64 位哈希（死循环检测用；只存哈希，不堆字符串）。 */
	public long configurationHash() {
		long hash = 0xcbf29ce484222325L;
		for (BlockPos pos : orderedKeys) {
			BlockState state = states.getOrDefault(pos, AIR);
			hash = mix(hash, pos.hashCode());
			hash = mix(hash, System.identityHashCode(state));
			// 常量值不是 BlockState 的一部分，否则"只改常量值"的循环会被误判成没变化。
			Integer constant = constantValues.get(pos);
			if (constant != null) {
				hash = mix(hash, constant);
			}
		}
		return hash;
	}

	private static long mix(long hash, int value) {
		long next = hash ^ (value & 0xFFFFFFFFL);
		return next * 0x100000001b3L;
	}

	/* ---------------------------------------------------------------- CircuitContext */

	@Override
	public BlockState getBlockState(BlockPos pos) {
		BlockState state = states.get(pos);
		if (state != null) {
			return state;
		}
		return edgeStates.getOrDefault(pos, AIR);
	}

	@Override
	public int getVanillaLevel(BlockPos emitterPos, Direction directionFromReaderToEmitter) {
		return vanillaLevels.getOrDefault(new PortKey(emitterPos, directionFromReaderToEmitter), 0);
	}

	@Override
	public int getVanillaWeakLevel(BlockPos emitterPos, Direction directionFromReaderToEmitter) {
		return weakLevel(emitterPos, directionFromReaderToEmitter);
	}

	@Override
	public int getLightLevel(BlockPos pos) {
		return lightLevels.getOrDefault(pos, 0);
	}

	@Override
	public int getConstantValue(BlockPos pos) {
		return constantValues.getOrDefault(pos, SignalProtocol.MIN_LEVEL);
	}
}

