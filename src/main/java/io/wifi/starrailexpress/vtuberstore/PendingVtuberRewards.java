package io.wifi.starrailexpress.vtuberstore;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PendingVtuberRewards extends SavedData {
    private static final String DATA_NAME = "sre_pending_vtuber_rewards";
    private final Map<UUID, LinkedHashMap<UUID, Reward>> byPlayer = new LinkedHashMap<>();

    record Reward(UUID playerId, UUID roundId, int baseAmount, boolean winner, boolean alive) {
    }

    static PendingVtuberRewards get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PendingVtuberRewards::new, PendingVtuberRewards::load, null), DATA_NAME);
    }

    static PendingVtuberRewards load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingVtuberRewards data = new PendingVtuberRewards();
        for (Tag value : tag.getList("Rewards", Tag.TAG_COMPOUND)) {
            if (value instanceof CompoundTag reward && reward.hasUUID("Player") && reward.hasUUID("Round")) {
                data.add(new Reward(reward.getUUID("Player"), reward.getUUID("Round"),
                        Math.max(0, reward.getInt("Amount")), reward.getBoolean("Winner"), reward.getBoolean("Alive")));
            }
        }
        data.setDirty(false);
        return data;
    }

    void add(Reward reward) {
        if (byPlayer.computeIfAbsent(reward.playerId(), ignored -> new LinkedHashMap<>())
                .putIfAbsent(reward.roundId(), reward) == null) {
            setDirty();
        }
    }

    boolean hasPending(UUID playerId) {
        return byPlayer.containsKey(playerId);
    }

    List<Reward> forPlayer(UUID playerId) {
        var rewards = byPlayer.get(playerId);
        return rewards == null ? List.of() : List.copyOf(rewards.values());
    }

    void acknowledge(Reward reward) {
        var rewards = byPlayer.get(reward.playerId());
        if (rewards != null && rewards.remove(reward.roundId()) != null) {
            if (rewards.isEmpty()) {
                byPlayer.remove(reward.playerId());
            }
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (var rewards : byPlayer.values()) {
            for (Reward reward : rewards.values()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", reward.playerId());
                entry.putUUID("Round", reward.roundId());
                entry.putInt("Amount", reward.baseAmount());
                entry.putBoolean("Winner", reward.winner());
                entry.putBoolean("Alive", reward.alive());
                entries.add(entry);
            }
        }
        tag.put("Rewards", entries);
        return tag;
    }
}
