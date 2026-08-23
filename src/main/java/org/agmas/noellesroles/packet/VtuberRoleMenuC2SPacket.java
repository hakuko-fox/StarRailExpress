package org.agmas.noellesroles.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record VtuberRoleMenuC2SPacket(UUID first, UUID second) implements CustomPacketPayload {
    public static final Type<VtuberRoleMenuC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "vtuber_role_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VtuberRoleMenuC2SPacket> CODEC =
            StreamCodec.ofMember(VtuberRoleMenuC2SPacket::write, VtuberRoleMenuC2SPacket::read);

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(first);
        buffer.writeBoolean(second != null);
        if (second != null) {
            buffer.writeUUID(second);
        }
    }

    private static VtuberRoleMenuC2SPacket read(FriendlyByteBuf buffer) {
        UUID first = buffer.readUUID();
        UUID second = buffer.readBoolean() ? buffer.readUUID() : null;
        return new VtuberRoleMenuC2SPacket(first, second);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
