package l.circuit_redstone.logic.cas.sim;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import l.circuit_redstone.logic.cas.CircuitRedstone;

/**
 * 异步模拟任务管理器：负责线程池、模拟 ID、取消以及进程退出时的清理。
 *
 * <p>主线程只做"采集快照 + 投递任务 + 接收结果发包"，绝不在主线程里阻塞等待推演。
 */
public final class SimulationManager {
	/** 同时运行的模拟上限（防止反复敲指令把 CPU 占满）。 */
	private static final int MAX_JOBS = 4;
	/** 后台推演线程数（沙盒推演是纯 CPU 计算，串行更稳）。 */
	private static final int POOL_SIZE = 2;

	private static final Map<UUID, SimulationHandle> JOBS = new ConcurrentHashMap<>();
	private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
	private static ExecutorService pool;
	private static boolean initialized;

	private SimulationManager() {
	}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		pool = Executors.newFixedThreadPool(POOL_SIZE, runnable -> {
			Thread thread = new Thread(runnable, "circuit-redstone_logic-sim-" + THREAD_COUNTER.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> shutdown());
	}

	/**
	 * 投递一个后台模拟任务。
	 *
	 * @return 任务句柄；并发已满时返回 null
	 */
	public static SimulationHandle submit(UUID ownerId, Consumer<SimulationHandle> work) {
		if (pool == null || JOBS.size() >= MAX_JOBS) {
			return null;
		}
		SimulationHandle handle = new SimulationHandle(UUID.randomUUID(), ownerId);
		JOBS.put(handle.getId(), handle);
		pool.execute(() -> {
			try {
				work.accept(handle);
			} catch (Throwable error) {
				CircuitRedstone.LOGGER.error("[{}] simulation {} crashed", CircuitRedstone.MOD_ID, handle.getId(), error);
			} finally {
				JOBS.remove(handle.getId());
			}
		});
		return handle;
	}

	/** 按 ID 取消；不存在返回 false。 */
	public static boolean stop(UUID id) {
		SimulationHandle handle = JOBS.get(id);
		if (handle == null) {
			return false;
		}
		handle.cancel();
		return true;
	}

	/** 取消某个玩家的全部模拟，返回被取消的数量。 */
	public static int stopAll(UUID ownerId) {
		int count = 0;
		for (SimulationHandle handle : JOBS.values()) {
			if (handle.getOwnerId().equals(ownerId)) {
				handle.cancel();
				count++;
			}
		}
		return count;
	}

	/** 当前正在运行的任务数（诊断用）。 */
	public static int runningJobs() {
		return JOBS.size();
	}

	public static int maxJobs() {
		return MAX_JOBS;
	}

	private static void shutdown() {
		for (SimulationHandle handle : JOBS.values()) {
			handle.cancel();
		}
		JOBS.clear();
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
		initialized = false;
	}
}
