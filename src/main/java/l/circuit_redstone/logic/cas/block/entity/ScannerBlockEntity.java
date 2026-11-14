package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

import l.circuit_redstone.logic.cas.ModRegistry;

/**
 * 扫描器的方块实体：只负责"每 10 tick 请求一次重扫"。
 */
public class ScannerBlockEntity extends PollingCircuitBlockEntity {
	/** 周期性重扫间隔（tick）。 */
	public static final int SCAN_INTERVAL_TICKS = 10;

	protected ScannerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SCAN_INTERVAL_TICKS);
	}

	/** 方块实体工厂。 */
	public static ScannerBlockEntity create(BlockPos pos, BlockState state) {
		return new ScannerBlockEntity(ModRegistry.SCANNER_BLOCK_ENTITY, pos, state);
	}
}
