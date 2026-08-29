/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SREConfig;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Persists explicit administrator skin grants and revocations without enabling
 * general game-to-database skin writes.
 */
public final class AdminSkinManagementService {
    private static final Gson GSON = new Gson();
    private static final String DATA_KEY = "skins";
    private static final String EQUIPPED_DATA_KEY = "equipped_skins";

    private AdminSkinManagementService() {
    }

    public enum Change {
        UNLOCK,
        LOCK
    }

    public static boolean isMysqlSyncConfigured() {
        return SREConfig.instance().itemSkinSyncServerEnabled
                && SREConfig.instance().mysqlPlayerSyncEnabled;
    }

    public static boolean isMysqlSyncAvailable() {
        return isMysqlSyncConfigured() && MysqlPlayerDataStore.isAvailable();
    }

    /**
     * Reads the authoritative skins record, merges one requested change, then
     * writes only when the record version still matches the version that was read.
     */
    public static CompletableFuture<Boolean> persistChange(ServerPlayer player, String itemType,
            Collection<String> skinNames, Change change) {
        long updatedAt = System.currentTimeMillis();
        String fallbackPayload = PlayerEconomyManager.createSkinDataSnapshot(player, updatedAt);
        Set<String> requestedSkins = new LinkedHashSet<>(skinNames);
        String playerName = player.getName().getString();

        return MysqlPlayerDataStore.loadBatchAsync(player.getUUID(), List.of(DATA_KEY, EQUIPPED_DATA_KEY))
                .thenCompose(records -> {
                    MysqlPlayerDataStore.SyncRecord skinsRecord = records.get(DATA_KEY);
                    long expectedRevision = skinsRecord == null ? 0L : skinsRecord.recordVersion();
                    String sourcePayload = skinsRecord == null ? fallbackPayload : skinsRecord.payload();
                    MergeResult merged = mergeChangeResult(
                            sourcePayload, itemType, requestedSkins, change, updatedAt);

                    Map<String, String> payloads = new LinkedHashMap<>();
                    Map<String, Long> expectedRevisions = new LinkedHashMap<>();
                    payloads.put(DATA_KEY, merged.payload());
                    expectedRevisions.put(DATA_KEY, expectedRevision);

                    if (merged.equippedReset()) {
                        MysqlPlayerDataStore.SyncRecord equippedRecord = records.get(EQUIPPED_DATA_KEY);
                        payloads.put(EQUIPPED_DATA_KEY, mergeEquippedPayload(
                                equippedRecord == null ? null : equippedRecord.payload(),
                                merged.equipped(), itemType, updatedAt, playerName));
                        expectedRevisions.put(EQUIPPED_DATA_KEY,
                                equippedRecord == null ? 0L : equippedRecord.recordVersion());
                    }
                    return MysqlPlayerDataStore.saveBatchAsyncIfVersions(
                            player.getUUID(),
                            payloads,
                            updatedAt,
                            expectedRevisions);
                });
    }

    static String mergeChange(String sourcePayload, String itemType, Collection<String> skinNames, Change change,
            long updatedAt) {
        return mergeChangeResult(sourcePayload, itemType, skinNames, change, updatedAt).payload();
    }

    private static MergeResult mergeChangeResult(String sourcePayload, String itemType,
            Collection<String> skinNames, Change change, long updatedAt) {
        if (sourcePayload == null || sourcePayload.isBlank()) {
            throw new IllegalArgumentException("The skins payload is empty");
        }
        if (skinNames == null || skinNames.isEmpty()) {
            throw new IllegalArgumentException("At least one skin is required");
        }

        JsonElement parsed = JsonParser.parseString(sourcePayload);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("The skins payload must be a JSON object");
        }
        JsonObject root = parsed.getAsJsonObject();
        boolean equippedReset = false;
        if (change == Change.UNLOCK) {
            JsonObject unlocked = getOrCreateObject(root, "unlocked");
            JsonObject itemSkins = getOrCreateObject(unlocked, itemType);
            skinNames.forEach(skinName -> itemSkins.addProperty(skinName, true));
        } else {
            equippedReset = lockSkins(root, itemType, Set.copyOf(skinNames));
        }
        root.addProperty("version", updatedAt);
        JsonObject equipped = getOptionalObject(root, "equipped");
        return new MergeResult(GSON.toJson(root), equippedReset,
                equipped == null ? new JsonObject() : equipped.deepCopy());
    }

    private static boolean lockSkins(JsonObject root, String itemType, Set<String> skinNames) {
        JsonObject unlocked = getOptionalObject(root, "unlocked");
        if (unlocked != null) {
            JsonObject itemSkins = getOptionalObject(unlocked, itemType);
            if (itemSkins != null) {
                skinNames.forEach(itemSkins::remove);
                if (itemSkins.size() == 0) {
                    unlocked.remove(itemType);
                }
            }
        }

        JsonObject equipped = getOptionalObject(root, "equipped");
        if (equipped == null || !equipped.has(itemType)) {
            return false;
        }
        JsonElement equippedSkin = equipped.get(itemType);
        if (!equippedSkin.isJsonPrimitive() || !equippedSkin.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("The equipped skin for '" + itemType + "' must be a string");
        }
        if (skinNames.contains(equippedSkin.getAsString())) {
            equipped.addProperty(itemType, "default");
            return true;
        }
        return false;
    }

    static String mergeEquippedPayload(String sourcePayload, JsonObject currentEquipped, String itemType,
            long updatedAt, String playerName) {
        JsonObject root;
        if (sourcePayload == null) {
            root = new JsonObject();
            root.add("equipped", currentEquipped.deepCopy());
        } else {
            if (sourcePayload.isBlank()) {
                throw new IllegalArgumentException("The equipped skins payload is empty");
            }
            JsonElement parsed = JsonParser.parseString(sourcePayload);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("The equipped skins payload must be a JSON object");
            }
            root = parsed.getAsJsonObject();
            getOrCreateObject(root, "equipped").addProperty(itemType, "default");
        }
        root.addProperty("updatedAt", updatedAt);
        root.addProperty("playerName", playerName);
        return GSON.toJson(root);
    }

    private static JsonObject getOrCreateObject(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            JsonObject created = new JsonObject();
            parent.add(key, created);
            return created;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("The skins payload field '" + key + "' must be a JSON object");
        }
        return value.getAsJsonObject();
    }

    private static JsonObject getOptionalObject(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("The skins payload field '" + key + "' must be a JSON object");
        }
        return value.getAsJsonObject();
    }

    private record MergeResult(String payload, boolean equippedReset, JsonObject equipped) {
    }
}
