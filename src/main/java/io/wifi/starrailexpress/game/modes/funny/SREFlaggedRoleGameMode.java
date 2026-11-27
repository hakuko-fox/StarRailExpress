package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
import io.wifi.starrailexpress.backpack.BackpackRoleChoiceResolver;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.RoleWeightedUtil;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract class SREFlaggedRoleGameMode extends SREMurderGameMode {
    private final String roleFlag;

    protected SREFlaggedRoleGameMode(ResourceLocation identifier, String roleFlag) {
        super(identifier);
        this.roleFlag = roleFlag;
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        Harpymodloader.refreshRoles();
        gameWorldComponent.clearRoleMap();

        List<SRERole> roles = TMMRoles.ROLES.values().stream()
                .filter(role -> role.isFlag(roleFlag))
                .filter(role -> !role.isOtherModeRole())
                .filter(role -> !SREDisableManager.isRoleDisabled(role))
                // GameInitializeEvent already resolves map-specific roles into ROLE_MAX.
                // Respect that result here too, otherwise flagged modes can draw a role
                // on a map where its special-map category is disabled (for example Zora).
                .filter(role -> role.getSpecialMapRole() == MapSpecialFeatures.ALL
                        || Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 0) > 0)
                // Companion roles are inserted by expandWithCompanionRoles; drawing them
                // directly can split a required pair (for example Luna/Yoru).
                .filter(role -> role.occupationedRoles.isEmpty())
                .filter(role -> role.occupationRoles.stream().allMatch(companion -> companion.isFlag(roleFlag)
                        && !companion.isOtherModeRole() && !SREDisableManager.isRoleDisabled(companion)
                        && (companion.getSpecialMapRole() == MapSpecialFeatures.ALL
                                || Harpymodloader.ROLE_MAX.getOrDefault(companion.identifier(), 0) > 0)))
                .collect(Collectors.toList());
        // 保留此模式允许重复职业的规则，但先按配置生成各阵营的实际名额。
        RoleAssignmentPool killers = RoleAssignmentPool.createPreferDistinct("Flagged killers",
                role -> roles.contains(role) && PlayerRoleWeightManager.getRoleType(role) == 4);
        RoleAssignmentPool vigilantes = RoleAssignmentPool.createPreferDistinct("Flagged vigilantes",
                role -> roles.contains(role) && PlayerRoleWeightManager.getRoleType(role) == 5);
        RoleAssignmentPool neutrals = RoleAssignmentPool.createPreferDistinct("Flagged neutrals",
                role -> roles.contains(role) && (PlayerRoleWeightManager.getRoleType(role) == 2
                        || PlayerRoleWeightManager.getRoleType(role) == 3));
        RoleAssignmentPool civilians = RoleAssignmentPool.createPreferDistinct("Flagged civilians",
                role -> roles.contains(role) && PlayerRoleWeightManager.getRoleType(role) == 1);
        List<ServerPlayer> remaining = new ArrayList<>();
        Map<ServerPlayer, SRERole> assignments = new LinkedHashMap<>();
        Map<Integer, Integer> reserved = new HashMap<>();
        for (ServerPlayer player : players) {
            SRERole forced = Harpymodloader.FORCED_MODDED_ROLE.get(player.getUUID());
            if (forced == null) {
                remaining.add(player);
                continue;
            }
            assignments.put(player, forced);
            int faction = BackpackRoleChoiceResolver.faction(forced.getRoleType());
            reserved.merge(faction, Math.max(1, forced.getOccupiedRoleCount()), Integer::sum);
        }
        int total = players.size();
        Map<Integer, Integer> factionSlots = Map.of(
                4, Math.max(0, RoleCountManager.getKillerCount(total) - reserved.getOrDefault(4, 0)),
                5, Math.max(0, RoleCountManager.getVigilanteCount(total) - reserved.getOrDefault(5, 0)),
                2, Math.max(0, RoleCountManager.getNeutralCount(total) - reserved.getOrDefault(2, 0)));
        List<RoleInstance> roleInstances = getAllRoles(
                factionSlots.get(4), factionSlots.get(5), factionSlots.get(2),
                total, assignments.size(), killers, neutrals, vigilantes, civilians, true,
                new ArrayList<>(assignments.values()), 10, Map.of(), roles::contains);
        Map<Integer, Integer> occupiedSlots = new LinkedHashMap<>();
        for (RoleInstance instance : roleInstances) {
            SRERole role = instance.role();
            int faction = BackpackRoleChoiceResolver.faction(PlayerRoleWeightManager.getRoleType(role));
            occupiedSlots.merge(faction, Math.max(0, role.getOccupiedRoleCount()), Integer::sum);
        }
        if (roleInstances.size() != remaining.size()
                || factionSlots.entrySet().stream().anyMatch(entry ->
                        !entry.getValue().equals(occupiedSlots.getOrDefault(entry.getKey(), 0)))
                || roleInstances.stream().anyMatch(instance -> !instance.role().isFlag(roleFlag)
                        || SREDisableManager.isRoleDisabled(instance.role()))) {
            return;
        }
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");
        Map<RoleInstance, Float> availableSlots = new LinkedHashMap<>();
        for (RoleInstance role : roleInstances)
            availableSlots.put(role, 1f);
        FactionCardUtils.assignForcedTeams(remaining, ServerPlayer::getUUID,
                PlayerRoleWeightManager.ForcePlayerTeam, availableSlots,
                slot -> PlayerRoleWeightManager.getRoleType(slot.role()),
                slots -> new RoleWeightedUtil(slots).selectRandomKeyBasedOnWeightsAndRemoved(),
                (player, slot) -> assignments.put(player, slot.role()),
                (player, forced) -> {
                    if (forced.type() == ForceTeamType.CARD)
                        FactionCardUtils.refund(player, forced.roleType());
                });
        Collections.shuffle(remaining);
        RoleWeightedUtil selector = new RoleWeightedUtil(availableSlots);
        for (ServerPlayer player : remaining) {
            RoleInstance slot = selector.selectRandomKeyBasedOnWeightsAndRemoved();
            assignments.put(player, slot.role());
        }
        int killerCount = 0;
        for (Map.Entry<ServerPlayer, SRERole> assignment : assignments.entrySet()) {
            gameWorldComponent.addRole(assignment.getKey(), assignment.getValue(), false);
            killerCount += giveKillerStartingMoney(assignment.getKey(), assignment.getValue()) ? 1 : 0;
        }

        gameWorldComponent.syncRoles();
        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            if (role != null) {
                ServerPlayNetworking.send(player,
                        new AnnounceWelcomePayload(role.getIdentifier().toString(), killerCount,
                                players.size() - killerCount));
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
            }
        }

        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    private boolean giveKillerStartingMoney(ServerPlayer player, SRERole role) {
        if (!role.canUseKiller())
            return false;
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < GameConstants.getMoneyStart())
            shop.setBalance(GameConstants.getMoneyStart());
        return true;
    }
}
