package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * 陣營卡（faction card）配發輔助，供各遊戲模式共用。
 * 卡片型別與 roleType 的對應（{@code FactionCardType.getTypeRoleId()}）：
 * 1=平民 / 2=中立 / 3=中立偏殺 / 4=殺手。平民卡同時涵蓋義警（roleType 5）。
 */
public final class FactionCardUtils {

    private FactionCardUtils() {
    }

    /**
     * 判斷職業是否符合陣營卡指定的陣營。
     */
    public static boolean roleMatchesCard(SRERole role, int forcedType) {
        return cardMatchesRoleType(forcedType, PlayerRoleWeightManager.getRoleType(role));
    }

    /** 依實際剩餘名額先處理陣營卡，再處理其他強制陣營；失敗者留在一般分配名單。 */
    public static <P, S> void assignForcedTeams(List<P> unassignedPlayers,
            Function<P, UUID> playerId, Map<UUID, ForceTeamInfo> requests,
            Map<S, Float> availableSlots, ToIntFunction<S> slotType,
            Function<Map<S, Float>, S> pickSlot, BiConsumer<P, S> onAssigned,
            BiConsumer<P, ForceTeamInfo> onFailed) {
        List<Map.Entry<UUID, ForceTeamInfo>> ordered = orderedRequests(requests);
        for (Map.Entry<UUID, ForceTeamInfo> request : ordered) {
            P player = unassignedPlayers.stream().filter(p -> playerId.apply(p).equals(request.getKey()))
                    .findFirst().orElse(null);
            if (player == null) {
                continue;
            }
            ForceTeamInfo forced = request.getValue();
            Map<S, Float> matching = new java.util.LinkedHashMap<>();
            for (Map.Entry<S, Float> slot : availableSlots.entrySet()) {
                int type = slotType.applyAsInt(slot.getKey());
                if (forced.type() == ForceTeamType.CARD
                        ? cardMatchesRoleType(forced.roleType(), type)
                        : forced.roleType() == type) {
                    matching.put(slot.getKey(), slot.getValue());
                }
            }
            S selected = matching.isEmpty() ? null : pickSlot.apply(matching);
            if (selected == null) {
                if (forced.type() == ForceTeamType.CARD) {
                    requests.remove(request.getKey());
                }
                onFailed.accept(player, forced);
                continue;
            }
            availableSlots.remove(selected);
            unassignedPlayers.remove(player);
            onAssigned.accept(player, selected);
        }
    }

    public static List<Map.Entry<UUID, ForceTeamInfo>> orderedRequests(Map<UUID, ForceTeamInfo> requests) {
        List<Map.Entry<UUID, ForceTeamInfo>> ordered = new ArrayList<>(requests.entrySet());
        ordered.sort((left, right) -> Boolean.compare(
                right.getValue().type() == ForceTeamType.CARD,
                left.getValue().type() == ForceTeamType.CARD));
        return ordered;
    }

    public static boolean cardMatchesRoleType(int cardType, int roleType) {
        return cardType == 1 ? roleType == 1 || roleType == 5 : cardType == roleType;
    }

    /** 無法配發符合陣營的職業時，退還一張卡片並通知玩家。 */
    public static void refund(ServerPlayer player, int forcedType) {
        FactionCardType cardType = FactionCardType.fromRoleType(forcedType);
        if (cardType != FactionCardType.NONE) {
            ProgressionDataManager.addFactionCard(player, cardType, 1);
            player.displayClientMessage(
                    Component.translatable("message.sre.role_rotation.card_limit")
                            .withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * 退還所有玩家已使用但未被處理的陣營卡。
     * 供不支援陣營卡配發的模式（或配發失敗的兜底）於開局呼叫，避免卡片被吞掉。
     */
    public static void refundAll(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            ForceTeamInfo forced = PlayerRoleWeightManager.ForcePlayerTeam.get(p.getUUID());
            if (forced != null && forced.type() == ForceTeamType.CARD) {
                refund(p, forced.roleType());
            }
        }
    }
}

