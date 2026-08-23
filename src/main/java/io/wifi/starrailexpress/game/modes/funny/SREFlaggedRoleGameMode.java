package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
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
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                // GameInitializeEvent already resolves map-specific roles into ROLE_MAX.
                // Respect that result here too, otherwise flagged modes can draw a role
                // on a map where its special-map category is disabled (for example Zora).
                .filter(role -> !role.isSpecialMapRole()
                        || Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 0) > 0)
                // Companion roles are inserted by expandWithCompanionRoles; drawing them
                // directly can split a required pair (for example Luna/Yoru).
                .filter(role -> role.occupationedRoles.isEmpty())
                .collect(Collectors.toList());
        if (roles.isEmpty())
            roles = List.of(TMMRoles.CIVILIAN);
        else
            Collections.shuffle(roles);

        List<ServerPlayer> remaining = new ArrayList<>();
        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        int killerCount = 0;

        List<ServerPlayer> assignedPlayers = new ArrayList<>();
        List<SRERole> assignedRoles = new ArrayList<>();
        for (ServerPlayer player : shuffled) {
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
            if (forcedType == null) {
                remaining.add(player);
                continue;
            }
            SRERole match = roles.stream()
                    .filter(role -> FactionCardUtils.roleMatchesCard(role, forcedType))
                    .findFirst().orElse(null);
            if (match == null) {
                FactionCardUtils.refund(player, forcedType);
                remaining.add(player);
                continue;
            }
            assignedPlayers.add(player);
            assignedRoles.add(match);
        }

        for (int i = 0; i < remaining.size(); i++) {
            ServerPlayer player = remaining.get(i);
            SRERole role = roles.get(i % roles.size());
            assignedPlayers.add(player);
            assignedRoles.add(role);
        }

        List<RoleInstance> roleInstances = assignedRoles.stream()
                .map(role -> new RoleInstance(java.util.UUID.randomUUID(), role))
                .toList();
        List<RoleInstance> expandedRoles = RoleAssignmentManager.expandWithCompanionRoles(roleInstances);
        for (int i = 0; i < assignedPlayers.size() && i < expandedRoles.size(); i++) {
            SRERole role = expandedRoles.get(i).role();
            gameWorldComponent.addRole(assignedPlayers.get(i), role, false);
            killerCount += giveKillerStartingMoney(assignedPlayers.get(i), role) ? 1 : 0;
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
