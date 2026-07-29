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

public class SREVtuberGameMode extends SREMurderGameMode {

    public SREVtuberGameMode(ResourceLocation identifier) {
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

        List<SRERole> vtuberRoles = TMMRoles.ROLES.values().stream()
                .filter(role -> role.isFlag("vtuber"))
                .filter(role -> !role.isOtherModeRole())
                .collect(Collectors.toList());

        if (vtuberRoles.isEmpty()) {
            vtuberRoles = List.of(TMMRoles.CIVILIAN);
        }

        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int killerCount = 0;
        for (int i = 0; i < shuffled.size(); i++) {
            ServerPlayer player = shuffled.get(i);
            SRERole role = vtuberRoles.get(i % vtuberRoles.size());
            gameWorldComponent.addRole(player, role, false);

            if (role.canUseKiller()) {
                killerCount++;
                SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
                if (playerShopComponent.balance < GameConstants.getMoneyStart())
                    playerShopComponent.setBalance(GameConstants.getMoneyStart());
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
