package org.agmas.noellesroles.cca;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * C4背部组件 - 管理玩家身上的C4炸药
 */
public class C4BackComponent implements AutoSyncedComponent {
    public static final ComponentKey<C4BackComponent> KEY = ComponentRegistry.getOrCreate(
        Identifier.of(Noellesroles.MOD_ID, "c4_back"),
        C4BackComponent.class
    );

    private final World world;
    /** 玩家UUID -> C4引爆时刻的世界tick */
    private final Map<UUID, Long> carriers = new LinkedHashMap<>();
    private final Map<UUID, Long> readOnlyCarriers = Collections.unmodifiableMap(carriers);
    private final Map<UUID, Long> plantedAt = new LinkedHashMap<>();

    // 默认配置：首次beep延迟3秒，fuse时间15秒
    private static final int C4_FIRST_BEEP_SECONDS = 3;
    private static final int C4_FUSE_SECONDS = 15;

    public C4BackComponent(World world) {
        this.world = world;
    }

    public boolean hasC4(UUID uuid) {
        return uuid != null && carriers.containsKey(uuid);
    }

    public Map<UUID, Long> getCarriers() {
        return readOnlyCarriers;
    }

    /** 附加C4到玩家（使用默认fuse时间）。如果已附加则返回false */
    public boolean addC4(UUID uuid) {
        return addC4(uuid, C4_FUSE_SECONDS * 20L);
    }

    /**
     * 附加C4到玩家，指定fuse长度（tick）
     */
    public boolean addC4(UUID uuid, long fuseTicks) {
        if (uuid == null || carriers.containsKey(uuid)) return false;
        long firstBeepDelayTicks = Math.max(0L, (long) C4_FIRST_BEEP_SECONDS * 20L);
        long detonationAt = world.getTime() + firstBeepDelayTicks + Math.max(1L, fuseTicks);
        carriers.put(uuid, detonationAt);
        plantedAt.put(uuid, world.getTime());
        KEY.sync(this.world);
        return true;
    }

    public boolean removeC4(UUID uuid) {
        if (uuid == null || carriers.remove(uuid) == null) return false;
        plantedAt.remove(uuid);
        KEY.sync(this.world);
        return true;
    }

    public boolean clearAll() {
        if (carriers.isEmpty()) return false;
        carriers.clear();
        plantedAt.clear();
        KEY.sync(this.world);
        return true;
    }

    /** 距离引爆的剩余tick数。-1表示未附加 */
    public long ticksUntilDetonation(UUID uuid) {
        Long t = carriers.get(uuid);
        if (t == null) return -1L;
        return Math.max(0L, t - world.getTime());
    }

    public long ticksSincePlant(UUID uuid) {
        Long t = plantedAt.get(uuid);
        if (t == null) return Long.MAX_VALUE;
        return Math.max(0L, world.getTime() - t);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        carriers.clear();
        plantedAt.clear();
        NbtList list = tag.getList("carriers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            String uuidStr = entry.getString("uuid");
            long detonationAt = entry.getLong("detonation_tick");
            long plantedTick = entry.contains("planted_tick")
                ? entry.getLong("planted_tick")
                : detonationAt - ((long) C4_FIRST_BEEP_SECONDS + (long) C4_FUSE_SECONDS) * 20L;
            if (uuidStr == null || uuidStr.isEmpty()) continue;
            try {
                UUID uuid = UUID.fromString(uuidStr);
                carriers.put(uuid, detonationAt);
                plantedAt.put(uuid, plantedTick);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, Long> e : carriers.entrySet()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("uuid", e.getKey().toString());
            entry.putLong("detonation_tick", e.getValue());
            entry.putLong("planted_tick", plantedAt.getOrDefault(e.getKey(), world.getTime()));
            list.add(entry);
        }
        tag.put("carriers", list);
    }
}
