package io.wifi.starrailexpress.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import io.wifi.starrailexpress.SRE;

import java.util.List;

public record VolunteerCommitC2SPacket(List<Integer> preferences) implements CustomPacketPayload {
    public static final Type<VolunteerCommitC2SPacket> TYPE = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "volunteer/commit"));

    public static final StreamCodec<ByteBuf, VolunteerCommitC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
            VolunteerCommitC2SPacket::preferences,
            VolunteerCommitC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}