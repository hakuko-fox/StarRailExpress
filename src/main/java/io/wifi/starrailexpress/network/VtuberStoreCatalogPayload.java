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

public record VtuberStoreCatalogPayload(String json) implements CustomPacketPayload {
    public static final Type<VtuberStoreCatalogPayload> ID = new Type<>(SRE.id("vtuber_store_catalog"));
    public static final StreamCodec<FriendlyByteBuf, VtuberStoreCatalogPayload> CODEC =
            CustomPacketPayload.codec(VtuberStoreCatalogPayload::write, VtuberStoreCatalogPayload::new);

    private VtuberStoreCatalogPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(1_048_576));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(json, 1_048_576);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
