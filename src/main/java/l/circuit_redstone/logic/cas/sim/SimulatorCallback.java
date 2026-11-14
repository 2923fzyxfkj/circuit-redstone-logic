package l.circuit_redstone.logic.cas.sim;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * 沙盒推演回调。所有回调都在<b>后台线程</b>触发；实现方负责把要发包的内容
 * 通过 {@code server.execute(...)} 切回主线程。
 */
public interface SimulatorCallback {
	/** 沙盒快照准备完成、推演即将开始。 */
	default void onStarted() {
	}

	/** 某个方块状态发生了变化。 */
	default void onStateChange(int tick, BlockPos pos, BlockState oldState, BlockState newState) {
	}

	/** 检测到周期性死循环。 */
	default void onDeadLoopDetected(int cycleTicks) {
	}

	/** 推演结束（正常/取消/超限/异常都会走到这里）。 */
	default void onFinished(SimulationSummary summary) {
	}

	/** 推演过程中抛出异常。 */
	default void onError(Throwable error) {
	}
}
