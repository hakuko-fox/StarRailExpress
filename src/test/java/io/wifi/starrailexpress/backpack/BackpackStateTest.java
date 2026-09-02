package io.wifi.starrailexpress.backpack;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackStateTest {
    private final Gson gson = new Gson();

    @Test
    void oldJsonNormalizesNewStoreFields() {
        BackpackState state = gson.fromJson("{\"cards\":null,\"migrated\":true}", BackpackState.class)
                .normalized();

        assertEquals(0, state.vtuberCoins);
        assertNotNull(state.purchasedSkins);
        assertTrue(state.purchasedSkins.isEmpty());
        assertEquals("", state.lastVtuberCoinRoundId);
        assertEquals(0, state.roleChoiceCards);
        assertEquals("", state.pendingRoleId);
        assertEquals(io.wifi.starrailexpress.progression.ProgressionState.FactionCardType.NONE,
                state.pendingFactionCard);
        assertTrue(state.migrated);
    }

    @Test
    void normalizationRejectsNegativeCoinsAndInvalidEntitlements() {
        BackpackState state = BackpackState.createDefault();
        state.vtuberCoins = -5;
        state.purchasedSkins = new java.util.HashSet<>();
        state.purchasedSkins.add(null);
        state.purchasedSkins.add("");
        state.purchasedSkins.add("skin:knife:signal_fang");

        state.normalized();

        assertEquals(0, state.vtuberCoins);
        assertEquals(Set.of("skin:knife:signal_fang"), state.purchasedSkins);
    }

    @Test
    void copyKeepsStoreStateWithoutSharingEntitlementSet() {
        BackpackState source = BackpackState.createDefault();
        source.vtuberCoins = 12;
        source.purchasedSkins.add("skin:bat:bamboo");
        source.lastVtuberCoinRoundId = "round-1";
        source.roleChoiceCards = 3;
        source.pendingRoleId = "sre:doctor";
        BackpackState target = BackpackState.createDefault();

        target.copyFrom(source);
        source.purchasedSkins.clear();

        assertEquals(12, target.vtuberCoins);
        assertEquals(Set.of("skin:bat:bamboo"), target.purchasedSkins);
        assertEquals("round-1", target.lastVtuberCoinRoundId);
        assertEquals(3, target.roleChoiceCards);
        assertEquals("sre:doctor", target.pendingRoleId);
    }

    @Test
    void pendingRoleAndFactionAreMutuallyExclusiveAfterNormalization() {
        BackpackState state = BackpackState.createDefault();
        state.pendingRoleId = "noellesroles:doctor";
        state.pendingFactionCard = io.wifi.starrailexpress.progression.ProgressionState.FactionCardType.KILLER;

        state.normalized();

        assertEquals(io.wifi.starrailexpress.progression.ProgressionState.FactionCardType.NONE,
                state.pendingFactionCard);
    }

    @Test
    void roleChoiceResolverHonorsRemainingCapacityAndRefundsLosers() {
        java.util.UUID first = java.util.UUID.randomUUID();
        java.util.UUID second = java.util.UUID.randomUUID();
        var requests = java.util.List.of(
                new BackpackRoleChoiceResolver.Request(first, "sre:role"),
                new BackpackRoleChoiceResolver.Request(second, "sre:role"));

        var result = BackpackRoleChoiceResolver.resolve(requests,
                java.util.Map.of("sre:role", 1), java.util.Map.of(), new java.util.Random(7));

        assertEquals(1, result.winners().size());
        assertEquals(1, result.losers().size());
        assertTrue(result.winners().containsKey(first) || result.winners().containsKey(second));
    }
}
