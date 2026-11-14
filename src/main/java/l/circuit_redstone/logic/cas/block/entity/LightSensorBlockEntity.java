package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.ModRegistry;

/**
 * 亮度传感器的方块实体：只负责"每 10 tick 请求一次重算"。
 *
 * <p>光照变化不会产生相邻更新，所以必须轮询；10 tick（0.5 秒）内跟上光照变化。
 */
public class LightSensorBlockEntity extends PollingCircuitBlockEntity {
	/** 光照轮询间隔（tick）。 */
	public static final int POLL_INTERVAL_TICKS = 10;

	protected LightSensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, POLL_INTERVAL_TICKS);
	}

	/** 方块实体工厂。 */
	public static LightSensorBlockEntity create(BlockPos pos, BlockState state) {
		return new LightSensorBlockEntity(ModRegistry.LIGHT_SENSOR_BLOCK_ENTITY, pos, state);
	}
}
