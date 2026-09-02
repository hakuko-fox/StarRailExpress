package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Requests a fresh server-side backpack snapshot before opening the screen. */
public record BackpackOpenRequestPayload() implements CustomPacketPayload {
    public static final Type<BackpackOpenRequestPayload> ID = new Type<>(SRE.id("backpack_open_request"));
    public static final StreamCodec<FriendlyByteBuf, BackpackOpenRequestPayload> CODEC =
            CustomPacketPayload.codec(BackpackOpenRequestPayload::write, BackpackOpenRequestPayload::new);

    private BackpackOpenRequestPayload(FriendlyByteBuf buffer) {
        this();
    }

    private void write(FriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
