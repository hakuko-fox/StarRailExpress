package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record StreamingSpectatorPayload(boolean active, UUID targetUuid, int cameraMode) implements CustomPacketPayload {
    public static final ResourceLocation PACKET_ID = SRE.id("streaming_spectator");
    public static final Type<StreamingSpectatorPayload> ID = new Type<>(PACKET_ID);
    public static final int CAMERA_NONE = 0;
    public static final int CAMERA_FIRST_PERSON = 1;
    public static final int CAMERA_THIRD_PERSON_BACK = 2;

    public static final StreamCodec<FriendlyByteBuf, StreamingSpectatorPayload> CODEC = StreamCodec.ofMember(
            StreamingSpectatorPayload::write,
            StreamingSpectatorPayload::read);

    public static StreamingSpectatorPayload stop() {
        return new StreamingSpectatorPayload(false, null, CAMERA_NONE);
    }

    public static StreamingSpectatorPayload waiting() {
        return new StreamingSpectatorPayload(true, null, CAMERA_NONE);
    }

    public static StreamingSpectatorPayload watch(UUID targetUuid, int cameraMode) {
        return new StreamingSpectatorPayload(true, targetUuid, cameraMode);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeBoolean(targetUuid != null);
        if (targetUuid != null) {
            buf.writeUUID(targetUuid);
        }
        buf.writeVarInt(cameraMode);
    }

    private static StreamingSpectatorPayload read(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        UUID targetUuid = buf.readBoolean() ? buf.readUUID() : null;
        int cameraMode = buf.readVarInt();
        return new StreamingSpectatorPayload(active, targetUuid, cameraMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
