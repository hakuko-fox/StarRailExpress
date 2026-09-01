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

public record VtuberStorePurchaseResultPayload(boolean success, String productId,
        String messageKey, int balance, int cardCount) implements CustomPacketPayload {
    public static final Type<VtuberStorePurchaseResultPayload> ID =
            new Type<>(SRE.id("vtuber_store_purchase_result"));
    public static final StreamCodec<FriendlyByteBuf, VtuberStorePurchaseResultPayload> CODEC =
            CustomPacketPayload.codec(VtuberStorePurchaseResultPayload::write,
                    VtuberStorePurchaseResultPayload::new);

    private VtuberStorePurchaseResultPayload(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readUtf(192), buffer.readUtf(192),
                buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(success);
        buffer.writeUtf(productId, 192);
        buffer.writeUtf(messageKey, 192);
        buffer.writeVarInt(balance);
        buffer.writeVarInt(cardCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
