package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.game.modes.funny.FactionCardUtils;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;

/** Settle reservations against the role actually assigned, never the requested role. */
final class CardSettlement {
    enum Result { NONE, SUCCESS, FACTION_REFUNDED, ROLE_REFUNDED }

    static Result settle(BackpackState state, String actualRoleId, int actualRoleType) {
        if (!state.pendingRoleId.isBlank()) {
            boolean matches = state.pendingRoleId.equals(actualRoleId);
            state.pendingRoleId = "";
            if (!matches) {
                state.roleChoiceCards = (int) Math.min(Integer.MAX_VALUE, (long) state.roleChoiceCards + 1);
            }
            return matches ? Result.SUCCESS : Result.ROLE_REFUNDED;
        }
        FactionCardType card = state.pendingFactionCard;
        if (card == FactionCardType.NONE) return Result.NONE;
        if (actualRoleId != null && FactionCardUtils.cardMatchesRoleType(card.getTypeRoleId(), actualRoleType)) {
            state.consumeFactionCard();
            return Result.SUCCESS;
        }
        state.refundFactionCard(card);
        return Result.FACTION_REFUNDED;
    }

    private CardSettlement() {}
}
