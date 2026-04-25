package io.wifi.starrailexpress.content.vote.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VoteCastC2SPacket(int optionIndex) implements CustomPacketPayload {
    public static final Type<VoteCastC2SPacket> TYPE = new Type<>(SRE.id( "vote_cast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoteCastC2SPacket> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.optionIndex),
            buf -> new VoteCastC2SPacket(buf.readVarInt())
    );

    @Override
    public Type<VoteCastC2SPacket> type() { return TYPE; }
}