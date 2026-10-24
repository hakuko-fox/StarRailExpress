package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardSettlementTest {
    @Test
    void everyFactionOnlyConsumesMatchingRolesAndRefundsOthersOnce() {
        for (FactionCardType card : FactionCardType.values()) {
            if (card == FactionCardType.NONE) continue;
            for (int actualType = -1; actualType <= 5; actualType++) {
                BackpackState state = BackpackState.createDefault();
                state.cards.put(card, 1);
                state.markUsed(card, 100L);
                assertTrue(state.reserveFactionCard(card, 200L));
                boolean matches = actualType == card.getTypeRoleId()
                        || card == FactionCardType.CIVILIAN && actualType == 5;
                assertEquals(matches ? CardSettlement.Result.SUCCESS : CardSettlement.Result.FACTION_REFUNDED,
                        CardSettlement.settle(state, actualType < 0 ? null : "test:role", actualType));
                assertEquals(matches ? 0 : 1, state.cards.get(card));
                assertEquals(matches ? 200L : 100L, state.lastUsedAt(card));
                assertEquals(CardSettlement.Result.NONE, CardSettlement.settle(state, "test:role", actualType));
                assertEquals(matches ? 0 : 1, state.cards.get(card));
            }
        }
    }

    @Test
    void roleChoiceRequiresExactIdEvenWithinSameFaction() {
        for (String actual : new String[] {"test:chosen", "test:other", null}) {
            BackpackState state = BackpackState.createDefault();
            state.roleChoiceCards = 0;
            state.pendingRoleId = "test:chosen";
            boolean matches = "test:chosen".equals(actual);
            assertEquals(matches ? CardSettlement.Result.SUCCESS : CardSettlement.Result.ROLE_REFUNDED,
                    CardSettlement.settle(state, actual, 4));
            assertEquals(matches ? 0 : 1, state.roleChoiceCards);
            assertEquals("", state.pendingRoleId);
            assertEquals(CardSettlement.Result.NONE, CardSettlement.settle(state, actual, 4));
            assertEquals(matches ? 0 : 1, state.roleChoiceCards);
        }
    }

    @Test
    void absentRoleNeverConsumesCardEvenIfTypeMatches() {
        BackpackState state = BackpackState.createDefault();
        state.cards.put(FactionCardType.KILLER, 1);
        state.reserveFactionCard(FactionCardType.KILLER, 200L);
        assertEquals(CardSettlement.Result.FACTION_REFUNDED, CardSettlement.settle(state, null, 4));
        assertEquals(1, state.cards.get(FactionCardType.KILLER));
        assertEquals(0L, state.lastUsedAt(FactionCardType.KILLER));
    }
}
