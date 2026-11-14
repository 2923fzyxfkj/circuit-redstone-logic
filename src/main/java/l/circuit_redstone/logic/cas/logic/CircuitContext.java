package l.circuit_redstone.logic.cas.logic;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 电路求值时需要的"世界视图"抽象。
 *
 * <p>真实世界与沙盒（{@code CircuitSimulator} 的内存快照）都实现这个接口，
 * 于是导线的供电判定、逻辑门的端口读取等逻辑只有一份实现（{@link CircuitSignals}、
 * {@link l.circuit_redstone.logic.cas.block.CircuitBlock} 都只依赖这个接口）。
 *
 * <p>方向约定沿用原版：{@code directionFromReaderToEmitter} 由读取方指向发射方。
 */
public interface CircuitContext {
	/** 读取某个位置的状态；沙盒里查不到的位置返回空气。 */
	BlockState getBlockState(BlockPos pos);

	/**
	 * 原版方块在该位置向读取方提供的电平（完整读数，含实心方块的强电）。
	 * 只对"非本模组方块"有意义；本模组方块的电平由 {@link CircuitSignals} 按面判断。
	 */
	int getVanillaLevel(BlockPos emitterPos, Direction directionFromReaderToEmitter);

	/** 同 {@link #getVanillaLevel}，但只取弱电（导线网络判定外源用）。 */
	int getVanillaWeakLevel(BlockPos emitterPos, Direction directionFromReaderToEmitter);

	/** 光照等级（亮度传感器用）。 */
	int getLightLevel(BlockPos pos);

	/**
	 * 常量方块在该位置保存的值（扫描器取值用）。
	 *
	 * <p>常量值是方块实体数据、不在 BlockState 里，所以必须经世界视图读取：
	 * 真实世界读方块实体，调试沙盒读采集快照。
	 *
	 * @return 该位置不是常量方块时返回 {@link SignalProtocol#MIN_LEVEL}
	 */
	int getConstantValue(BlockPos pos);
}
