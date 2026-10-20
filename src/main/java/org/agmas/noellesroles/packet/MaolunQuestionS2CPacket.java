package org.agmas.noellesroles.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** status: 0 = question, 1 = success, 2 = failure/closed. No answer key is sent. */
public record MaolunQuestionS2CPacket(UUID sessionId, int sequence, long deadline, int progress,
        int remainingMistakes, int status, String question, List<String> options) implements CustomPacketPayload {
    public static final Type<MaolunQuestionS2CPacket> ID = new Type<>(Noellesroles.id("maolun_question"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaolunQuestionS2CPacket> CODEC =
            StreamCodec.ofMember(MaolunQuestionS2CPacket::write, MaolunQuestionS2CPacket::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeVarInt(sequence);
        buffer.writeLong(deadline);
        buffer.writeVarInt(progress);
        buffer.writeVarInt(remainingMistakes);
        buffer.writeVarInt(status);
        buffer.writeUtf(question, 1024);
        buffer.writeVarInt(options.size());
        for (String option : options) buffer.writeUtf(option, 256);
    }

    private static MaolunQuestionS2CPacket read(RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        int sequence = buffer.readVarInt();
        long deadline = buffer.readLong();
        int progress = buffer.readVarInt();
        int mistakes = buffer.readVarInt();
        int status = buffer.readVarInt();
        String question = buffer.readUtf(1024);
        int size = buffer.readVarInt();
        if (size < 0 || size > 4) throw new IllegalArgumentException("Invalid math option count");
        List<String> options = new ArrayList<>();
        for (int i = 0; i < size; i++) options.add(buffer.readUtf(256));
        return new MaolunQuestionS2CPacket(id, sequence, deadline, progress, mistakes, status,
                question, List.copyOf(options));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
