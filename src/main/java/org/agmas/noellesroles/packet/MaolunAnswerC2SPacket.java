package org.agmas.noellesroles.packet;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record MaolunAnswerC2SPacket(UUID sessionId, int sequence, int choice) implements CustomPacketPayload {
    public static final Type<MaolunAnswerC2SPacket> ID = new Type<>(Noellesroles.id("maolun_answer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaolunAnswerC2SPacket> CODEC =
            StreamCodec.ofMember(MaolunAnswerC2SPacket::write, MaolunAnswerC2SPacket::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeVarInt(sequence);
        buffer.writeVarInt(choice);
    }

    private static MaolunAnswerC2SPacket read(RegistryFriendlyByteBuf buffer) {
        return new MaolunAnswerC2SPacket(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
