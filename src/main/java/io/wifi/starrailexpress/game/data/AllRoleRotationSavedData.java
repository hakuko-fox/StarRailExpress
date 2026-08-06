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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全職業輪跑模式的伺服器全局進度資料。
 * 記錄每個職業的被遊玩次數與上次被遊玩的回合，用於決定佇列順序
 * （從未玩過優先 → 最久未玩優先）。資料儲存於世界 data/ 目錄，跨重啟保留。
 */
public final class AllRoleRotationSavedData extends SavedData {
    private static final String DATA_NAME = "sre_all_role_rotation";

    private long currentRound = 0L;
    private final Map<String, RoleTrack> tracks = new LinkedHashMap<>();

    public static AllRoleRotationSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static AllRoleRotationSavedData get(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(AllRoleRotationSavedData::new, AllRoleRotationSavedData::load, null),
                DATA_NAME);
    }

    public static AllRoleRotationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        AllRoleRotationSavedData data = new AllRoleRotationSavedData();
        data.currentRound = tag.getLong("CurrentRound");
        for (Tag entryTag : tag.getList("Roles", Tag.TAG_COMPOUND)) {
            if (entryTag instanceof CompoundTag compound) {
                String id = compound.getString("Id");
                if (id.isBlank())
                    continue;
                int played = compound.getInt("Played");
                long lastRound = compound.getLong("LastRound");
                data.tracks.put(id, new RoleTrack(played, lastRound));
            }
        }
        return data;
    }

    public long getCurrentRound() {
        return currentRound;
    }

    /** 結束本局、進入下一回合（在標記完本局被遊玩的職業後呼叫）。 */
    public void advanceRound() {
        currentRound++;
        setDirty(true);
    }

    public RoleTrack getTrack(String roleId) {
        return tracks.computeIfAbsent(roleId, k -> new RoleTrack(0, 0L));
    }

    /** 唯讀查詢：不會建立條目（用於佇列選擇與進度顯示，避免污染存檔）。 */
    public RoleTrack getTrackOrNull(String roleId) {
        return tracks.get(roleId);
    }

    /** 標記某職業於本回合被遊玩一次。 */
    public void markPlayed(String roleId) {
        RoleTrack t = getTrack(roleId);
        t.playedCount++;
        t.lastPlayedRound = currentRound;
        setDirty(true);
    }

    /** 清空全部進度（管理員重置）。 */
    public void resetAll() {
        tracks.clear();
        currentRound = 0L;
        setDirty(true);
    }

    public Map<String, RoleTrack> snapshot() {
        return Map.copyOf(tracks);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("CurrentRound", currentRound);
        ListTag list = new ListTag();
        for (Map.Entry<String, RoleTrack> entry : tracks.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putString("Id", entry.getKey());
            compound.putInt("Played", entry.getValue().playedCount);
            compound.putLong("LastRound", entry.getValue().lastPlayedRound);
            list.add(compound);
        }
        tag.put("Roles", list);
        return tag;
    }

    /** 單一職業的輪跑追蹤資料。 */
    public static final class RoleTrack {
        public int playedCount;
        public long lastPlayedRound;

        public RoleTrack(int playedCount, long lastPlayedRound) {
            this.playedCount = playedCount;
            this.lastPlayedRound = lastPlayedRound;
        }
    }
}
