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

/**
 * 隐形告示牌的方块实体：只保存一段文字。
 *
 * <p>长度为 0 表示"还没写"——客户端渲染器会显示斜体灰色的占位提示，
 * 而不是什么都不画（否则玩家根本找不到自己放下的告示牌）。
 *
 * <p>文字是<b>方块实体数据</b>，不在 BlockState 里，所以必须显式实现
 * {@link #toUpdatePacket()} / {@link #toInitialChunkDataNbt()} 才能到达客户端：
 * 缺了前者，{@code /circuit-set} 之后客户端仍然读不到文字；缺了后者，区块加载
 * （也就是重进世界）时客户端拿到的还是空文本，等于永远只显示占位提示。
 */
public class HiddenSignBlockEntity extends BlockEntity {
	private static final String NBT_TEXT = "text";
	/** 文本长度上限，与字符串方块一致（单条聊天消息长度）。 */
	public static final int MAX_TEXT_LENGTH = 256;

	private String text = "";

	protected HiddenSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** 方块实体工厂。 */
	public static HiddenSignBlockEntity create(BlockPos pos, BlockState state) {
		return new HiddenSignBlockEntity(ModRegistry.HIDDEN_SIGN_BLOCK_ENTITY, pos, state);
	}

	public String getText() {
		return text;
	}

	/** 写入文字（超长截断）；{@code markDirty} 让存档记住它。 */
	public void setText(String text) {
		String value = text == null ? "" : text;
		this.text = value.length() > MAX_TEXT_LENGTH ? value.substring(0, MAX_TEXT_LENGTH) : value;
		markDirty();
	}

	/** 把当前文字推给正在观察该区块的客户端（渲染器每帧读的就是这份数据）。 */
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
		setText(nbt.getString(NBT_TEXT));
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		nbt.putString(NBT_TEXT, this.text);
	}
}
