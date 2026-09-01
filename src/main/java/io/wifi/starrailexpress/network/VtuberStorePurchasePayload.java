/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VtuberStorePurchasePayload(String productId) implements CustomPacketPayload {
    public static final Type<VtuberStorePurchasePayload> ID = new Type<>(SRE.id("vtuber_store_purchase"));
    public static final StreamCodec<FriendlyByteBuf, VtuberStorePurchasePayload> CODEC =
            CustomPacketPayload.codec(VtuberStorePurchasePayload::write, VtuberStorePurchasePayload::new);

    private VtuberStorePurchasePayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(192));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(productId, 192);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
