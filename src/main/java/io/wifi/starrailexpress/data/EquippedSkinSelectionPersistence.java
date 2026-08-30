/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/** Resolves the equipped-skin snapshot used when a player logs in. */
public final class EquippedSkinSelectionPersistence {
    private EquippedSkinSelectionPersistence() {
    }

    public static Map<String, String> resolve(Map<String, String> localEquipped,
            Map<String, String> legacyRemoteEquipped, String equippedPartitionPayload) {
        Map<String, String> source = legacyRemoteEquipped != null ? legacyRemoteEquipped : localEquipped;
        Map<String, String> fallback = source == null ? new HashMap<>() : new HashMap<>(source);
        if (equippedPartitionPayload == null || equippedPartitionPayload.isBlank()) {
            return fallback;
        }

        try {
            JsonElement root = JsonParser.parseString(equippedPartitionPayload);
            if (!root.isJsonObject()) {
                return fallback;
            }
            JsonElement equippedElement = root.getAsJsonObject().get("equipped");
            if (equippedElement == null || !equippedElement.isJsonObject()) {
                return fallback;
            }

            Map<String, String> dedicated = new HashMap<>();
            JsonObject equipped = equippedElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : equipped.entrySet()) {
                if (entry.getKey().isBlank() || !entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) {
                    continue;
                }
                String skinName = entry.getValue().getAsString();
                dedicated.put(entry.getKey(), skinName.isBlank() ? "default" : skinName);
            }
            return dedicated;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
