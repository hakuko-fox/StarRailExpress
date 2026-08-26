/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.exmo.sre.nametag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-local nametag persistence. This file is independent of a map save,
 * so unlocked titles survive map/world replacement on the same server.
 */
public final class NameTagDataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("starrailexpress.nametag.data.json");
    private static final Path TEMP_FILE = DATA_FILE.resolveSibling(DATA_FILE.getFileName() + ".tmp");

    private static RootData rootData;

    private NameTagDataStore() {
    }

    public static synchronized void restore(ServerPlayer player, NameTagInventoryComponent component) {
        ensureLoaded();
        PlayerData data = rootData.players.get(player.getUUID().toString());
        if (data != null) {
            component.mergeLocalData(data);
        }
        save(player, component);
    }

    public static synchronized void save(ServerPlayer player, NameTagInventoryComponent component) {
        ensureLoaded();
        PlayerData data = new PlayerData();
        data.playerName = player.getGameProfile().getName();
        data.nameTags = new ArrayList<>(component.nameTags);
        data.currentNameTag = component.CurrentNameTag;
        data.killerWinStreak = component.getKillerWinStreak();
        data.policeWinStreak = component.getPoliceWinStreak();
        data.neutralWinStreak = component.getNeutralWinStreak();
        data.lossStreak = component.getLossStreak();
        data.firstDeathStreak = component.getFirstDeathStreak();
        data.updatedAt = System.currentTimeMillis();
        rootData.players.put(player.getUUID().toString(), data);
        writeFile();
    }

    public static synchronized boolean addOfflineNameTag(UUID playerUuid, String playerName, String nameTag) {
        ensureLoaded();
        PlayerData data = rootData.players.computeIfAbsent(playerUuid.toString(), ignored -> new PlayerData());
        if (data.nameTags == null) {
            data.nameTags = new ArrayList<>();
        }
        if (data.nameTags.contains(nameTag)) {
            return false;
        }
        data.playerName = playerName;
        data.nameTags.add(nameTag);
        data.updatedAt = System.currentTimeMillis();
        writeFile();
        return true;
    }

    public static synchronized Optional<StoredPlayer> findStoredPlayer(String playerName) {
        ensureLoaded();
        for (Map.Entry<String, PlayerData> entry : rootData.players.entrySet()) {
            PlayerData data = entry.getValue();
            if (data != null && data.playerName != null && data.playerName.equalsIgnoreCase(playerName)) {
                try {
                    return Optional.of(new StoredPlayer(UUID.fromString(entry.getKey()), data.playerName));
                } catch (IllegalArgumentException ignored) {
                    SRE.LOGGER.warn("Ignoring invalid player UUID {} in local nametag data", entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    public record StoredPlayer(UUID uuid, String playerName) {
    }

    private static void ensureLoaded() {
        if (rootData != null) {
            return;
        }
        rootData = new RootData();
        if (!Files.isRegularFile(DATA_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
            RootData loaded = GSON.fromJson(reader, RootData.class);
            if (loaded != null) {
                rootData = loaded;
                if (rootData.players == null) {
                    rootData.players = new LinkedHashMap<>();
                }
            }
        } catch (Exception exception) {
            SRE.LOGGER.error("Failed to load local nametag data from {}", DATA_FILE, exception);
        }
    }

    private static void writeFile() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(TEMP_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(rootData, writer);
            }
            try {
                Files.move(TEMP_FILE, DATA_FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(TEMP_FILE, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SRE.LOGGER.error("Failed to save local nametag data to {}", DATA_FILE, exception);
        }
    }

    private static final class RootData {
        int version = 1;
        Map<String, PlayerData> players = new LinkedHashMap<>();
    }

    static final class PlayerData {
        String playerName = "";
        ArrayList<String> nameTags = new ArrayList<>();
        String currentNameTag = "";
        int killerWinStreak;
        int policeWinStreak;
        int neutralWinStreak;
        int lossStreak;
        int firstDeathStreak;
        long updatedAt;
    }
}
