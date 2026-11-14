package l.circuit_redstone.logic.cas.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import l.circuit_redstone.logic.cas.ModRegistry;
import l.circuit_redstone.logic.cas.block.ColorBlock;
import l.circuit_redstone.logic.cas.logic.ColorHex;

/**
 * 纯色方块的方块实体：保存 24 位 RGB 颜色。
 *
 * <p>颜色必须发给客户端才能着色，而 BlockState 里只有"占位变体"，所以这里显式实现
 * {@link #toUpdatePacket()} / {@link #toInitialChunkDataNbt()}，并提供
 * {@link #syncToClients()} 主动把新颜色推给看得到这个方块的玩家。
 */
public class ColorBlockEntity extends BlockEntity {
	private static final String NBT_COLOR = "color";

	private int color = ColorBlock.DEFAULT_COLOR;

	protected ColorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** 方块实体工厂。 */
	public static ColorBlockEntity create(BlockPos pos, BlockState state) {
		return new ColorBlockEntity(ModRegistry.COLOR_BLOCK_ENTITY, pos, state);
	}

	public int getColor() {
		return color;
	}

	public void setColor(int rgb) {
		this.color = rgb & ColorHex.RGB_MASK;
		markDirty();
	}

	/** 把当前颜色推给正在观察该区块的客户端（客户端着色读的就是这份数据）。 */
	public void syncToClients() {
		if (!(this.world instanceof ServerWorld serverWorld)) {
			return;
		}
		Packet<?> packet = BlockEntityUpdateS2CPacket.create(this);
		for (ServerPlayerEntity player : serverWorld.getChunkManager().threadedAnvilChunkStorage
				.getPlayersWatchingChunk(new ChunkPos(this.pos))) {
			player.networkHandler.sendPacket(packet);
		}
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		NbtCompound nbt = new NbtCompound();
		writeNbt(nbt);
		return nbt;
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		this.color = nbt.getInt(NBT_COLOR) & ColorHex.RGB_MASK;
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		nbt.putInt(NBT_COLOR, this.color);
	}
}
