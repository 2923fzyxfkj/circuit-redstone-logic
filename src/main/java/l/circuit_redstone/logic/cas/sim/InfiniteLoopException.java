package l.circuit_redstone.logic.cas.sim;

/**
 * 沙盒推演检测到"全局状态重复"时抛出，用于立即终止推演。
 */
public class InfiniteLoopException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final int cycleTicks;

	public InfiniteLoopException(int cycleTicks) {
		super("dead loop detected, cycle=" + cycleTicks + " ticks");
		this.cycleTicks = cycleTicks;
	}

	/** 周期长度（tick）。 */
	public int getCycleTicks() {
		return cycleTicks;
	}
}
