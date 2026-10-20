package io.wifi.starrailexpress.backpack;

import com.google.gson.Gson;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FactionCardReservationTest {
    @Test
    void everyCardRefundsOnceAndRestoresItsPreviousCooldownAfterReload() {
        for (FactionCardType type : FactionCardType.values()) {
            if (type == FactionCardType.NONE) continue;
            BackpackState state = BackpackState.createDefault();
            state.cards.put(type, 2);
            state.markUsed(type, 1_000L);
            assertTrue(state.reserveFactionCard(type, 3_000_000L));
            assertEquals(1, state.cards.get(type));
            assertEquals(3_000_000L, state.lastUsedAt(type));

            Gson gson = new Gson();
            state = gson.fromJson(gson.toJson(state), BackpackState.class).normalized();
            assertTrue(state.refundFactionCard(type));
            assertFalse(state.refundFactionCard(type));
            assertEquals(2, state.cards.get(type));
            assertEquals(1_000L, state.lastUsedAt(type));
            assertEquals(FactionCardType.NONE, state.pendingFactionCard);
        }
    }

    @Test
    void refundWithoutPreviousUseRemovesCooldownAndAllowsAnotherReservation() {
        BackpackState state = BackpackState.createDefault();
        state.cards.put(FactionCardType.KILLER, 1);
        assertTrue(state.reserveFactionCard(FactionCardType.KILLER, 10_000L));
        assertTrue(state.refundFactionCard(FactionCardType.KILLER));
        assertEquals(0L, state.lastUsedAt(FactionCardType.KILLER));
        assertTrue(state.reserveFactionCard(FactionCardType.KILLER, 10_001L));
    }

    @Test
    void consumedCardCannotBeRefundedOrReusedBySwitchingToRoleChoice() {
        BackpackState state = BackpackState.createDefault();
        state.cards.put(FactionCardType.NEUTRAL_FOR_KILLER, 1);
        assertTrue(state.reserveFactionCard(FactionCardType.NEUTRAL_FOR_KILLER, 10_000L));
        state.consumeFactionCard();
        assertFalse(state.refundFactionCard(FactionCardType.NEUTRAL_FOR_KILLER));
        assertFalse(state.reserveFactionCard(FactionCardType.NEUTRAL_FOR_KILLER, 20_000L));
        assertEquals(0, state.cards.get(FactionCardType.NEUTRAL_FOR_KILLER));
        assertEquals(10_000L, state.lastUsedAt(FactionCardType.NEUTRAL_FOR_KILLER));
    }

    @Test
    void outstandingReservationCannotBeOverwrittenOrRefundedAsAnotherType() {
        BackpackState state = BackpackState.createDefault();
        state.cards.put(FactionCardType.KILLER, 1);
        state.cards.put(FactionCardType.CIVILIAN, 1);
        assertTrue(state.reserveFactionCard(FactionCardType.KILLER, 10_000L));
        assertFalse(state.reserveFactionCard(FactionCardType.CIVILIAN, 10_000L));
        assertFalse(state.refundFactionCard(FactionCardType.CIVILIAN));
        assertEquals(FactionCardType.KILLER, state.pendingFactionCard);
        assertEquals(1, state.cards.get(FactionCardType.CIVILIAN));
    }

    @Test
    void legacyPendingCardCanBeRefundedWithoutLeavingItsActivationCooldown() {
        BackpackState state = new Gson().fromJson(
                "{\"cards\":{\"KILLER\":0},\"pendingFactionCard\":\"KILLER\","
                        + "\"cardLastUsedAt\":{\"KILLER\":10000}}", BackpackState.class).normalized();
        assertTrue(state.refundFactionCard(FactionCardType.KILLER));
        assertEquals(1, state.cards.get(FactionCardType.KILLER));
        assertEquals(0L, state.lastUsedAt(FactionCardType.KILLER));
    }
}
