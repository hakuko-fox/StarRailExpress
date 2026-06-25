package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record MapIntroSyncPayload(
        List<MapJson> maps,
        List<String> bagMaps,
        List<String> policeMaps,
        List<String> underwaterMaps,
        List<String> airMaps) implements CustomPacketPayload {
    public static final Type<MapIntroSyncPayload> ID = new Type<>(SRE.id("map_intro_sync"));
    public static final StreamCodec<FriendlyByteBuf, MapIntroSyncPayload> CODEC =
            CustomPacketPayload.codec(MapIntroSyncPayload::write, MapIntroSyncPayload::new);

    public record MapJson(String id, String json) {
        private static MapJson read(FriendlyByteBuf buffer) {
            return new MapJson(buffer.readUtf(256), buffer.readUtf(1_048_576));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 256);
            buffer.writeUtf(json, 1_048_576);
        }
    }

    private MapIntroSyncPayload(FriendlyByteBuf buffer) {
        this(readMaps(buffer), readStrings(buffer), readStrings(buffer), readStrings(buffer), readStrings(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(maps.size());
        for (MapJson map : maps) {
            map.write(buffer);
        }
        writeStrings(buffer, bagMaps);
        writeStrings(buffer, policeMaps);
        writeStrings(buffer, underwaterMaps);
        writeStrings(buffer, airMaps);
    }

    private static List<MapJson> readMaps(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<MapJson> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(MapJson.read(buffer));
        }
        return result;
    }

    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(buffer.readUtf(256));
        }
        return result;
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) {
            buffer.writeUtf(value, 256);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
