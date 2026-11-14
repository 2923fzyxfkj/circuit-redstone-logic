package l.circuit_redstone.logic.cas.logic;

import java.util.List;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 导线网络的供电判定规则（真实世界与调试沙盒共用同一份实现）。
 *
 * <p>规则：整张网络是"二进制、不衰减"的电气节点——只要网络外存在任何一个信号源，
 * 或者本 tick 注入了调试脉冲，整张网络就是高电平；否则低电平。
 */
public final class WireRule {
	private WireRule() {
	}

	/**
	 * 网络边界上的一个候选信号源。
	 *
	 * @param pos                    发射方位置
	 * @param directionFromReader    由网络侧指向发射方的方向
	 */
	public record Port(BlockPos pos, Direction directionFromReader) {
	}

	/** 如何读取某个候选源的电平（真实世界走世界查询，沙盒走快照）。 */
	@FunctionalInterface
	public interface SourceReader {
		int level(BlockPos emitterPos, Direction directionFromReaderToEmitter);
	}

	/** 是否至少存在一个电平 &gt; 0 的外部源。 */
	public static boolean hasAnySource(List<Port> ports, SourceReader reader) {
		for (Port port : ports) {
			if (reader.level(port.pos(), port.directionFromReader()) > SignalProtocol.MIN_LEVEL) {
				return true;
			}
		}
		return false;
	}

	/** 网络是否带电；{@code injectedPulse} 为调试脉冲（最高优先级）。 */
	public static boolean isPowered(List<Port> ports, SourceReader reader, boolean injectedPulse) {
		return injectedPulse || hasAnySource(ports, reader);
	}
}
