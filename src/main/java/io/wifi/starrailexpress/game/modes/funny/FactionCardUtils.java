package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

import java.util.List;

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
        int rt = PlayerRoleWeightManager.getRoleType(role);
        if (forcedType == 1) {
            return rt == 1 || rt == 5; // 平民卡 → 平民 / 義警
        }
        return rt == forcedType;
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
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(p.getUUID());
            if (forcedType != null) {
                refund(p, forcedType);
            }
        }
    }
}

