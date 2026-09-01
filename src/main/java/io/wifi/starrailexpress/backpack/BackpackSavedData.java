/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.backpack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Local world fallback for backpack data when MySQL player sync is disabled or unavailable. */
public final class BackpackSavedData extends SavedData {
    private static final String DATA_NAME = "starrailexpress_backpacks";
    private final Map<UUID, String> payloads = new LinkedHashMap<>();

    public static BackpackSavedData get(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(BackpackSavedData::new, BackpackSavedData::load, null), DATA_NAME);
    }

    public String get(UUID playerUuid) {
        return payloads.get(playerUuid);
    }

    public void put(UUID playerUuid, String json) {
        if (playerUuid == null || json == null || json.isBlank()) {
            return;
        }
        payloads.put(playerUuid, json);
        setDirty(true);
    }

    public static BackpackSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        BackpackSavedData data = new BackpackSavedData();
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            if (!players.contains(key, Tag.TAG_STRING)) {
                continue;
            }
            try {
                data.payloads.put(UUID.fromString(key), players.getString(key));
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid legacy/corrupt keys without discarding other players.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag players = new CompoundTag();
        payloads.forEach((uuid, json) -> players.putString(uuid.toString(), json));
        tag.put("Players", players);
        return tag;
    }
}
