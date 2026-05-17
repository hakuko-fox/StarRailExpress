package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class RepairMapRuntimeConfig {
    private RepairMapRuntimeConfig() {
    }

    public static Optional<MapConfig.MapEntry> currentMap(ServerLevel level) {
        for (MapConfig.MapEntry entry : ServerMapConfig.cache_maps) {
            if (entry != null && entry.repair != null) {
                return Optional.of(entry);
            }
        }
        ServerMapConfig config = ServerMapConfig.getInstance(level);
        if (config.getMaps() == null) {
            return Optional.empty();
        }
        return config.getMaps().stream()
                .filter(entry -> entry != null && entry.repair != null)
                .findFirst();
    }

    public static Optional<MapConfig.RepairConfig> current(ServerLevel level) {
        return currentMap(level).map(entry -> entry.repair);
    }
}
