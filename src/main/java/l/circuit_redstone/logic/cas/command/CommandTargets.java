package l.circuit_redstone.logic.cas.command;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * 指令共用的"目标方块"解析：准星瞄准的方块位置。
 *
 * <p>{@code /circuit-set} 与 {@code /circuit-read} 都要用同一个准星判定，
 * 所以只在这里实现一次（搜索距离仍用原版规则：生存 4.5 格、创造 5 格）。
 */
final class CommandTargets {
	private CommandTargets() {
	}

	/**
	 * 准星指向的方块位置。
	 *
	 * @return 没指到方块时光标位置返回 {@code null}
	 */
	static BlockPos aimedBlock(ServerPlayerEntity player) {
		HitResult hit = player.raycast(PlayerEntity.getReachDistance(player.isCreative()), 0.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return blockHit.getBlockPos();
	}
}
