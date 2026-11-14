package l.circuit_redstone.logic.cas.sim;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次后台模拟任务的句柄（模拟 ID + 取消标记）。
 */
public final class SimulationHandle {
	private final UUID id;
	private final UUID ownerId;
	private final AtomicBoolean cancelled = new AtomicBoolean(false);

	SimulationHandle(UUID id, UUID ownerId) {
		this.id = id;
		this.ownerId = ownerId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	/** 请求取消；后台线程在下一个 tick 边界退出。 */
	public void cancel() {
		cancelled.set(true);
	}

	public boolean isCancelled() {
		return cancelled.get();
	}
}
