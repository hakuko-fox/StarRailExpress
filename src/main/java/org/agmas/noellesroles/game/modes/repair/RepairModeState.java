package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.packet.RepairCoinRewardS2CPacket;
import org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Map;
import java.util.WeakHashMap;

public final class RepairModeState {
    public static final String ESCAPED_TAG = "noellesroles_repair_escaped";
    public static final String NEUTRAL_WIN_TAG = "noellesroles_repair_neutral_win";
    public static final int REQUIRED_REPAIRED_STATIONS = 5;
    public static final int REPAIR_STATION_MAX_PROGRESS = 100;
    public static final int TRIAL_EXECUTION_TICKS = 20 * 75;
    public static final float REVIVE_HEALTH = 8.0F;

    private static final Map<ServerLevel, Integer> COMPLETED_STATIONS = new WeakHashMap<>();

    private RepairModeState() {
    }

    public static void reset(ServerLevel level) {
        COMPLETED_STATIONS.put(level, 0);
        level.players().forEach(player -> {
            player.removeTag(ESCAPED_TAG);
            player.removeTag(NEUTRAL_WIN_TAG);
            var component = ModComponents.REPAIR_ROLES.get(player);
            component.downed = false;
            component.carriedBy = null;
            component.carrying = null;
            component.carryBlockedTicks = 0;
            component.trialStand = org.agmas.noellesroles.component.RepairRolePlayerComponent.BlockPosTag.NONE;
            component.currentEventKey = "";
            component.currentEventRewardKey = "";
            component.currentEventTicks = 0;
            component.currentEventDanger = 0;
            component.sync();
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
                awardCoins(player, 35, "repair_coin_source.station");
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

    public static void awardCoins(ServerPlayer player, int amount, String sourceKey) {
        SREPlayerShopComponent.KEY.get(player).addToBalance(amount);
        ServerPlayNetworking.send(player, new RepairCoinRewardS2CPacket(amount, sourceKey));
    }

    public static boolean isHunter(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        var role = ModComponents.REPAIR_ROLES.get(player).activeRole;
        return RepairRoleDefinition.byId(role).map(def -> def.faction == RepairRoleDefinition.Faction.HUNTER)
                .orElse(game != null && game.isRole(player, ModRoles.REPAIR_HUNTER));
    }

    public static boolean isNonHunterRepairPlayer(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRunning()) {
            return false;
        }
        var component = ModComponents.REPAIR_ROLES.get(player);
        return RepairRoleDefinition.byId(component.activeRole)
                .map(def -> def.faction != RepairRoleDefinition.Faction.HUNTER)
                .orElse(game.isRole(player, ModRoles.REPAIR_SURVIVOR) || game.isRole(player, ModRoles.REPAIR_NEUTRAL));
    }

    public static boolean downPlayer(ServerPlayer player) {
        if (!isNonHunterRepairPlayer(player) || GameUtils.isPlayerEliminated(player)) {
            return false;
        }
        var component = ModComponents.REPAIR_ROLES.get(player);
        component.downed = true;
        component.carriedBy = null;
        component.trialStand = org.agmas.noellesroles.component.RepairRolePlayerComponent.BlockPosTag.NONE;
        player.setHealth(1.0F);
        player.setPose(Pose.SWIMMING);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 8, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 3, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 10, 0, false, false, true));
        component.sync();
        broadcastCombatFeedback((ServerLevel) player.level(), RepairCombatFeedbackS2CPacket.DOWNED, player, player.getX(),
                player.getY() + 0.8D, player.getZ(), 24.0D);
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.downed").withStyle(ChatFormatting.DARK_RED), false);
        return true;
    }

    public static void revivePlayer(ServerPlayer medic, ServerPlayer target) {
        var component = ModComponents.REPAIR_ROLES.get(target);
        component.downed = false;
        component.carriedBy = null;
        component.trialStand = org.agmas.noellesroles.component.RepairRolePlayerComponent.BlockPosTag.NONE;
        target.setHealth(Math.min(target.getMaxHealth(), REVIVE_HEALTH));
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.removeEffect(MobEffects.WEAKNESS);
        target.removeEffect(MobEffects.DARKNESS);
        component.sync();
        awardCoins(medic, 40, "repair_coin_source.revive");
        broadcastCombatFeedback((ServerLevel) target.level(), RepairCombatFeedbackS2CPacket.REVIVED, target, target.getX(),
                target.getY() + 0.8D, target.getZ(), 24.0D);
        target.displayClientMessage(Component.translatable("message.noellesroles.repair.revived").withStyle(ChatFormatting.GREEN), false);
    }

    public static void releaseCarried(ServerPlayer hunter) {
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        if (hunterComponent.carrying == null || !(hunter.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getPlayerByUUID(hunterComponent.carrying) instanceof ServerPlayer carried) {
            var carriedComponent = ModComponents.REPAIR_ROLES.get(carried);
            carriedComponent.carriedBy = null;
            carried.teleportTo(hunter.getX(), hunter.getY(), hunter.getZ());
            carriedComponent.sync();
        }
        hunterComponent.carrying = null;
        hunterComponent.sync();
    }

    public static void broadcastCombatFeedback(ServerLevel level, int kind, net.minecraft.world.entity.Entity entity, double x,
            double y, double z, double radius) {
        double radiusSqr = radius * radius;
        RepairCombatFeedbackS2CPacket packet = new RepairCombatFeedbackS2CPacket(kind, entity.getId(), x, y, z);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= radiusSqr) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    public static void blockHunterCarry(ServerPlayer hunter, int ticks) {
        releaseCarried(hunter);
        var component = ModComponents.REPAIR_ROLES.get(hunter);
        component.carryBlockedTicks = Math.max(component.carryBlockedTicks, ticks);
        component.sync();
    }
}
