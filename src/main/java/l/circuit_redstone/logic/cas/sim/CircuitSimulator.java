package l.circuit_redstone.logic.cas.sim;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import l.circuit_redstone.logic.cas.block.CircuitBlock;
import l.circuit_redstone.logic.cas.block.WireBlock;
import l.circuit_redstone.logic.cas.logic.WireRule;

/**
 * 底层沙盒模拟引擎。
 *
 * <p>职责只有三件事：
 * <ol>
 *   <li>采集快照（主线程、只读）；</li>
 *   <li>在后台线程推演沙盒状态机（一个 tick 一轮，直到稳定/取消/超限）；</li>
 *   <li>把每次状态变化通过 {@link SimulatorCallback} 抛给"执行策略"。</li>
 * </ol>
 *
 * <p>不同子指令的差异只在于传入的 {@link SimulatorCallback} 与 {@link SimulatorOptions}
 * （普通播报 / 死循环检测），推演逻辑本身只此一份。
 */
public final class CircuitSimulator {
	/** 快照节点上限（需求 §1）。 */
	public static final int MAX_NODES = 4096;
	/** 调试脉冲持续多少个 tick（需求：0 -&gt; 1 -&gt; 0）。 */
	private static final int PULSE_TICKS = 1;

	private CircuitSimulator() {
	}

	/**
	 * 起点能否推演：它自己是导线，或它六个相邻方向里有导线（{@link SimulationSandbox#seedsOf}）。
	 * 指令用它给出准确报错，而不是把"没有网络"和"并发已满"混成同一条消息。
	 */
	public static boolean hasNetwork(World world, BlockPos start) {
		return !SimulationSandbox.seedsOf(world, start).isEmpty();
	}

	/**
	 * 需求 §1 的核心方法：深拷贝 {@code startPos} 周边的导线网络，并在后台线程注入一个 tick 的
	 * 高电平脉冲推演传播。
	 *
	 * <p><b>快照在主线程采集</b>（{@code World} 不能跨线程读），推演本身完全在后台线程。
	 *
	 * @return 模拟句柄；起点四周没有导线或并发已满时返回 null
	 */
	public static SimulationHandle simulate(ServerWorld world, UUID ownerId, BlockPos startPos,
			SimulatorCallback callback, SimulatorOptions options) {
		SimulationSandbox sandbox = SimulationSandbox.capture(world, startPos, options.maxNodes());
		if (sandbox == null) {
			return null;
		}
		return SimulationManager.submit(ownerId,
				handle -> run(sandbox, callback, options, handle));
	}

	/**
	 * 在后台线程推演沙盒（由 {@link SimulationManager} 调用）。
	 */
	public static void run(SimulationSandbox sandbox, SimulatorCallback callback, SimulatorOptions options,
			SimulationHandle handle) {
		long deadline = System.nanoTime() + options.maxWallMillis() * 1_000_000L;
		Map<Long, Integer> seenConfigurations = options.detectDeadLoop() ? new HashMap<>() : null;

		callback.onStarted();

		int tick = 0;
		int changes = 0;
		int cycleTicks = 0;
		SimulationSummary.Reason reason = SimulationSummary.Reason.STABLE;
		boolean stable = false;

		try {
			while (tick < options.maxTicks()) {
				if (handle.isCancelled()) {
					reason = SimulationSummary.Reason.CANCELLED;
					break;
				}
				if (System.nanoTime() > deadline) {
					reason = SimulationSummary.Reason.BUDGET_LIMIT;
					break;
				}

				boolean changed = false;
				boolean pulseActive = tick < PULSE_TICKS;

				// 第一轮：导线网络（整网求值，与真实世界同一套 WireRule）
				for (SimulationSandbox.Network network : sandbox.networks()) {
					boolean powered = WireRule.isPowered(network.ports(),
							(emitterPos, directionFromReader) -> l.circuit_redstone.logic.cas.update.CircuitSignals
									.readWeakSourceLevel(sandbox, emitterPos, directionFromReader),
							pulseActive && network.containsStart());
					for (BlockPos pos : network.members()) {
						BlockState oldState = sandbox.getBlockState(pos);
						if (oldState.get(WireBlock.POWERED) == powered) {
							continue;
						}
						BlockState newState = oldState.with(WireBlock.POWERED, powered);
						sandbox.set(pos, newState);
						changed = true;
						changes++;
						if (changes <= options.maxChangeReport()) {
							callback.onStateChange(tick, pos, oldState, newState);
						}
					}
				}

				// 第二轮：电路方块（复用电路方块自己的求值逻辑，不做第二份实现）
				for (BlockPos pos : sandbox.circuitBlocks()) {
					BlockState oldState = sandbox.getBlockState(pos);
					if (!(oldState.getBlock() instanceof CircuitBlock circuitBlock)) {
						continue;
					}
					int level = circuitBlock.evaluate(sandbox, pos, oldState);
					if (oldState.get(CircuitBlock.POWER) == level) {
						continue;
					}
					BlockState newState = oldState.with(CircuitBlock.POWER, level);
					sandbox.set(pos, newState);
					changed = true;
					changes++;
					if (changes <= options.maxChangeReport()) {
						callback.onStateChange(tick, pos, oldState, newState);
					}
				}

				if (!changed) {
					// 一整轮没有任何变化 = 已稳定
					stable = true;
					tick++;
					break;
				}

				tick++;

				if (seenConfigurations != null) {
					long hash = sandbox.configurationHash();
					Integer firstSeen = seenConfigurations.putIfAbsent(hash, tick);
					if (firstSeen != null) {
						throw new InfiniteLoopException(tick - firstSeen);
					}
				}
			}

			if (!stable && reason == SimulationSummary.Reason.STABLE && tick >= options.maxTicks()) {
				reason = SimulationSummary.Reason.TICK_LIMIT;
			}
		} catch (InfiniteLoopException loop) {
			cycleTicks = loop.getCycleTicks();
			reason = SimulationSummary.Reason.DEAD_LOOP;
			callback.onDeadLoopDetected(cycleTicks);
		} catch (Throwable error) {
			reason = SimulationSummary.Reason.ERROR;
			callback.onError(error);
		} finally {
			callback.onFinished(new SimulationSummary(handle.getId(), reason, tick, changes, cycleTicks));
		}
	}
}

