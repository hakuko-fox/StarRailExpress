/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.game.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent cross-round modifier rotation progress for a server world. */
public final class ModifierRotationSavedData extends SavedData {
    private static final String DATA_NAME = "sre_modifier_rotation";

    private boolean enabled = true;
    private long currentRound = 0L;
    private final Map<String, ModifierTrack> tracks = new LinkedHashMap<>();

    public static ModifierRotationSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static ModifierRotationSavedData get(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(ModifierRotationSavedData::new, ModifierRotationSavedData::load, null),
                DATA_NAME);
    }

    public static ModifierRotationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ModifierRotationSavedData data = new ModifierRotationSavedData();
        data.enabled = !tag.contains("Enabled", Tag.TAG_BYTE) || tag.getBoolean("Enabled");
        data.currentRound = tag.getLong("CurrentRound");
        for (Tag entryTag : tag.getList("Modifiers", Tag.TAG_COMPOUND)) {
            if (entryTag instanceof CompoundTag compound) {
                String id = compound.getString("Id");
                if (id.isBlank())
                    continue;
                int played = compound.getInt("Played");
                long lastRound = compound.getLong("LastRound");
                data.tracks.put(id, new ModifierTrack(played, lastRound));
            }
        }
        return data;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled)
            return;
        this.enabled = enabled;
        setDirty(true);
    }

    public long getCurrentRound() {
        return currentRound;
    }

    public void advanceRound() {
        currentRound++;
        setDirty(true);
    }

    public ModifierTrack getTrack(String modifierId) {
        return tracks.computeIfAbsent(modifierId, key -> new ModifierTrack(0, 0L));
    }

    public ModifierTrack getTrackOrNull(String modifierId) {
        return tracks.get(modifierId);
    }

    public void markPlayed(String modifierId) {
        ModifierTrack track = getTrack(modifierId);
        track.playedCount++;
        track.lastPlayedRound = currentRound;
        setDirty(true);
    }

    /** Reset progress without changing whether rotation is enabled. */
    public void resetAll() {
        tracks.clear();
        currentRound = 0L;
        setDirty(true);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("Enabled", enabled);
        tag.putLong("CurrentRound", currentRound);
        ListTag list = new ListTag();
        for (Map.Entry<String, ModifierTrack> entry : tracks.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putString("Id", entry.getKey());
            compound.putInt("Played", entry.getValue().playedCount);
            compound.putLong("LastRound", entry.getValue().lastPlayedRound);
            list.add(compound);
        }
        tag.put("Modifiers", list);
        return tag;
    }

    public static final class ModifierTrack {
        public int playedCount;
        public long lastPlayedRound;

        public ModifierTrack(int playedCount, long lastPlayedRound) {
            this.playedCount = playedCount;
            this.lastPlayedRound = lastPlayedRound;
        }
    }
}
