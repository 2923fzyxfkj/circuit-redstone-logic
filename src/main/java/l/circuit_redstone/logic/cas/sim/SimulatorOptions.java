package l.circuit_redstone.logic.cas.sim;

/**
 * 沙盒推演参数（全部是硬性上限，防止把服务器资源耗尽）。
 *
 * @param maxTicks        最大推演 tick 数
 * @param maxChangeReport 最多上报多少条状态变化（其余只计数，避免刷屏/刷内存）
 * @param maxWallMillis   单次推演的最大墙钟时间
 * @param detectDeadLoop  是否启用"全局状态重复"检测
 * @param maxNodes        快照最多多少个位置
 */
public record SimulatorOptions(int maxTicks, int maxChangeReport, long maxWallMillis, boolean detectDeadLoop,
		int maxNodes) {
	/** 普通脉冲传播测试。 */
	public static SimulatorOptions forPropagation() {
		return new SimulatorOptions(10000, 100, 5000L, false, CircuitSimulator.MAX_NODES);
	}

	/** 死循环探测。 */
	public static SimulatorOptions forDeadLoopProbe() {
		return new SimulatorOptions(10000, 40, 5000L, true, CircuitSimulator.MAX_NODES);
	}
}
