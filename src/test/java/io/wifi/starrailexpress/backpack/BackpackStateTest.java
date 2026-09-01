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
        BackpackState target = BackpackState.createDefault();

        target.copyFrom(source);
        source.purchasedSkins.clear();

        assertEquals(12, target.vtuberCoins);
        assertEquals(Set.of("skin:bat:bamboo"), target.purchasedSkins);
        assertEquals("round-1", target.lastVtuberCoinRoundId);
    }
}
