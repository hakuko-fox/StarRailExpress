package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.network.packet.RoleRotationSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class SRERoleRotationSingleSelectHideGameMode extends SRERoleRotationSingleSelectGameMode {

    public SRERoleRotationSingleSelectHideGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    protected void broadcastSync(ServerLevel world) {
        List<UUID> orderList = new ArrayList<>();
        for (int i = 1; i <= draftState.totalPlayers; i++) {
            for (Map.Entry<UUID, Integer> entry : draftState.playerOrder.entrySet()) {
                if (entry.getValue() == i) {
                    orderList.add(entry.getKey());
                    break;
                }
            }
        }

        Map<UUID, String> allSelectedRoles = draftState.getSelectedRolesAsStrings();
        Map<UUID, List<String>> allRoundCandidates = draftState.getRoundCandidatesAsStrings();

        for (ServerPlayer p : world.players()) {
            UUID playerUuid = p.getUUID();

            Map<UUID, String> filteredSelected = new HashMap<>();
            if (allSelectedRoles.containsKey(playerUuid)) {
                filteredSelected.put(playerUuid, allSelectedRoles.get(playerUuid));
            }

            Map<UUID, List<String>> filteredCandidates = new HashMap<>();
            if (allRoundCandidates.containsKey(playerUuid)) {
                filteredCandidates.put(playerUuid, allRoundCandidates.get(playerUuid));
            }

            RoleRotationSyncS2CPacket packet = new RoleRotationSyncS2CPacket(
                    draftState.isSelecting,
                    draftState.currentRotationIndex,
                    draftState.totalPlayers,
                    draftState.confirmCountdown,
                    draftState.perPlayerTimeLimit,
                    draftState.roundStartTime,
                    orderList,
                    filteredSelected,
                    draftState.randomChoosers,
                    filteredCandidates);
            ServerPlayNetworking.send(p, packet);
        }
    }
}
