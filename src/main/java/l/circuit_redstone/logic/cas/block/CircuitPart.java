package l.circuit_redstone.logic.cas.block;

/**
 * "本模组的电路方块"标记接口。
 *
 * <p>用途是把"哪些方块属于电路"这件事集中到一处判断，而不是到处写
 * {@code instanceof CircuitBlock || instanceof WireBlock || ...}：
 * 调试指令的"全量快照"、孤儿方块实体的自愈都只认这个接口。
 */
public interface CircuitPart {
}
