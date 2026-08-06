package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SREHkVtuberGameMode extends SREMurderGameMode {

    public SREHkVtuberGameMode(ResourceLocation identifier) {
        super(identifier);
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

        List<SRERole> hkVtuberRoles = TMMRoles.ROLES.values().stream()
                .filter(role -> role.isFlag("hkvtuber"))
                .filter(role -> !role.isOtherModeRole())
                .filter(role -> !org.agmas.harpymodloader.SREDisableManager.isRoleDisabled(role))
                .collect(Collectors.toList());

        if (hkVtuberRoles.isEmpty()) {
            hkVtuberRoles = List.of(TMMRoles.CIVILIAN);
        }

        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int killerCount = 0;
        // 先處理陣營卡：盡量從 vtuber 職業池中配發符合陣營的職業，無法配發則退回卡片
        List<ServerPlayer> remaining = new ArrayList<>();
        for (ServerPlayer player : shuffled) {
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
            if (forcedType == null) {
                remaining.add(player);
                continue;
            }
            SRERole match = null;
            for (SRERole role : hkVtuberRoles) {
                if (FactionCardUtils.roleMatchesCard(role, forcedType)) {
                    match = role;
                    break;
                }
            }
            if (match != null) {
                gameWorldComponent.addRole(player, match, false);
                if (match.canUseKiller()) {
                    killerCount++;
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < GameConstants.getMoneyStart())
                        shop.setBalance(GameConstants.getMoneyStart());
                }
            } else {
                FactionCardUtils.refund(player, forcedType);
                remaining.add(player);
            }
        }

        // 剩餘玩家 round-robin
        for (int i = 0; i < remaining.size(); i++) {
            ServerPlayer player = remaining.get(i);
            SRERole role = hkVtuberRoles.get(i % hkVtuberRoles.size());
            gameWorldComponent.addRole(player, role, false);

            if (role.canUseKiller()) {
                killerCount++;
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                if (shop.balance < GameConstants.getMoneyStart())
                    shop.setBalance(GameConstants.getMoneyStart());
            }
        }

        gameWorldComponent.syncRoles();

        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            if (role != null) {
                ServerPlayNetworking.send(player,
                        new AnnounceWelcomePayload(role.getIdentifier().toString(), killerCount,
                                players.size() - killerCount));
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
            }
        }

        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.finalizeGame(serverWorld, gameWorldComponent);
    }
}
