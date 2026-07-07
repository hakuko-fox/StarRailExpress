package net.exmo.sre.meeting.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/** C2S：会议中开始 / 结束发言（按键或 GUI 触发）。 */
public record MeetingSpeakC2SPayload(boolean speaking) implements CustomPacketPayload {

    public static final Type<MeetingSpeakC2SPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "meeting_speak"));

    public static final StreamCodec<FriendlyByteBuf, MeetingSpeakC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MeetingSpeakC2SPayload::speaking,
            MeetingSpeakC2SPayload::new);

    @Override
    public Type<MeetingSpeakC2SPayload> type() {
        return ID;
    }
}
