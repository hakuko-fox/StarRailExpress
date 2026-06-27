package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 阿蒙背包选目标包：客户端在背包界面点选一名成熟宿主，请求将其锁定为待夺舍目标（进入「操纵」）。
 * 锁定后，阿蒙再次按 G 即夺取该目标全部物品并杀死之（见 AmonPlayerComponent#usurpLockedTarget）。
 */
public record AmonSelectTargetC2SPacket(UUID player) implements CustomPacketPayload {
    public static final ResourceLocation AMON_SELECT_TARGET_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "amon_select_target");
    public static final CustomPacketPayload.Type<AmonSelectTargetC2SPacket> ID =
            new CustomPacketPayload.Type<>(AMON_SELECT_TARGET_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, AmonSelectTargetC2SPacket> CODEC;

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
    }

    public static AmonSelectTargetC2SPacket read(FriendlyByteBuf buf) {
        return new AmonSelectTargetC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(AmonSelectTargetC2SPacket::write, AmonSelectTargetC2SPacket::read);
    }
}
