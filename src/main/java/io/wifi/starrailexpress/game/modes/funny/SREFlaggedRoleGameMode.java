package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
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
import java.util.List;
import java.util.LinkedHashMap;
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
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");

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
                .filter(role -> role.occupationRoles.stream().noneMatch(SREDisableManager::isRoleDisabled))
                .collect(Collectors.toList());
        // 保留此模式允许重复职业的规则，但先按配置生成各阵营的实际名额。
        RoleAssignmentPool killers = RoleAssignmentPool.createUnlimited("Flagged killers",
                role -> roles.contains(role) && PlayerRoleWeightManager.getRoleType(role) == 4);
        RoleAssignmentPool vigilantes = RoleAssignmentPool.createUnlimited("Flagged vigilantes",
                role -> roles.contains(role) && PlayerRoleWeightManager.getRoleType(role) == 5);
        RoleAssignmentPool neutrals = RoleAssignmentPool.createUnlimited("Flagged neutrals",
                role -> roles.contains(role) && (PlayerRoleWeightManager.getRoleType(role) == 2
                        || PlayerRoleWeightManager.getRoleType(role) == 3));
        RoleAssignmentPool civilians = RoleAssignmentPool.createUnlimited("Flagged civilians",
                role -> roles.contains(role) && PlayerRoleWeightManager.getRoleType(role) == 1);
        List<RoleInstance> roleInstances = getAllRoles(RoleCountManager.getKillerCount(players.size()),
                RoleCountManager.getVigilanteCount(players.size()), RoleCountManager.getNeutralCount(players.size()),
                players.size(), 0, killers, neutrals, vigilantes, civilians, true);
        Map<RoleInstance, Float> availableSlots = new LinkedHashMap<>();
        for (RoleInstance role : roleInstances)
            availableSlots.put(role, 1f);
        List<ServerPlayer> remaining = new ArrayList<>(players);
        Map<ServerPlayer, SRERole> assignments = new LinkedHashMap<>();
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
            assignments.put(player, slot == null ? TMMRoles.CIVILIAN : slot.role());
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
