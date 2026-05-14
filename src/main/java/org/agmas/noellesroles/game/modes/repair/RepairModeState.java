package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;

import java.util.Map;
import java.util.WeakHashMap;

public final class RepairModeState {
    public static final String ESCAPED_TAG = "noellesroles_repair_escaped";
    public static final String NEUTRAL_WIN_TAG = "noellesroles_repair_neutral_win";
    public static final int REQUIRED_REPAIRED_STATIONS = 5;
    public static final int REPAIR_STATION_MAX_PROGRESS = 100;

    private static final Map<ServerLevel, Integer> COMPLETED_STATIONS = new WeakHashMap<>();

    private RepairModeState() {
    }

    public static void reset(ServerLevel level) {
        COMPLETED_STATIONS.put(level, 0);
        level.players().forEach(player -> {
            player.removeTag(ESCAPED_TAG);
            player.removeTag(NEUTRAL_WIN_TAG);
        });
    }

    public static void stationCompleted(ServerLevel level) {
        COMPLETED_STATIONS.put(level, getCompletedStationCount(level) + 1);
    }

    public static void stationCompleted(ServerLevel level, BlockPos pos) {
        stationCompleted(level);
        RepairGameplayEffects.burst(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 2);
        for (ServerPlayer player : level.players()) {
            if (RepairGameplayEffects.isSurvivor(player)) {
                SREPlayerShopComponent.KEY.get(player).addToBalance(35);
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.team_station_reward", 35)
                        .withStyle(ChatFormatting.GOLD), true);
            } else if (RepairGameplayEffects.isHunter(player)) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 12, 0, false, true, true));
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.hunter_frenzy")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    public static int getCompletedStationCount(ServerLevel level) {
        return COMPLETED_STATIONS.getOrDefault(level, 0);
    }

    public static boolean areExitGatesPowered(ServerLevel level) {
        return getCompletedStationCount(level) >= REQUIRED_REPAIRED_STATIONS;
    }

    public static void addNeutralTaskProgress(ServerPlayer player, String roleId, int amount, int needed) {
        var component = ModComponents.REPAIR_ROLES.get(player);
        if (!roleId.equals(component.activeRole) || component.neutralTaskCompleted) {
            return;
        }
        component.neutralTaskProgress = Math.min(needed, component.neutralTaskProgress + amount);
        if (component.neutralTaskProgress >= needed) {
            component.neutralTaskCompleted = true;
            player.addTag(NEUTRAL_WIN_TAG);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.neutral_complete")
                    .withStyle(ChatFormatting.GOLD), false);
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.neutral_progress",
                    component.neutralTaskProgress, needed).withStyle(ChatFormatting.YELLOW), true);
        }
        component.sync();
    }
}
