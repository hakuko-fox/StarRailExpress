package io.wifi.starrailexpress.backpack;

import com.google.gson.Gson;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BackpackCardMigrationTest {
    private Map<FactionCardType, Integer> legacyCards() {
        var cards = new EnumMap<FactionCardType, Integer>(FactionCardType.class);
        cards.put(FactionCardType.KILLER, 3);
        cards.put(FactionCardType.NEUTRAL_FOR_KILLER, 2);
        return cards;
    }

    @Test
    void failedDestinationWritePreservesSourceAndRetryDoesNotDoubleMerge() {
        BackpackState destination = BackpackState.createDefault();
        destination.cards.put(FactionCardType.KILLER, 1);
        var source = legacyCards();
        assertFalse(BackpackCardMigration.migrate(destination, source, () -> false));
        assertEquals(3, source.get(FactionCardType.KILLER));
        assertEquals(4, destination.cards.get(FactionCardType.KILLER));
        assertFalse(BackpackCardMigration.migrate(destination, source, () -> false));
        assertEquals(4, destination.cards.get(FactionCardType.KILLER));

        assertTrue(BackpackCardMigration.migrate(destination, source, () -> {
            assertEquals(3, source.get(FactionCardType.KILLER));
            return true;
        }));
        assertEquals(0, source.get(FactionCardType.KILLER));
        assertEquals(4, destination.cards.get(FactionCardType.KILLER));
        assertEquals(2, destination.cards.get(FactionCardType.NEUTRAL_FOR_KILLER));
    }

    @Test
    void restartBeforeDestinationPersistedCanRecoverFromUnclearedSource() {
        Gson gson = new Gson();
        BackpackState destination = BackpackState.createDefault();
        String originalRecord = gson.toJson(destination);
        var source = legacyCards();
        assertFalse(BackpackCardMigration.migrate(destination, source, () -> false));
        BackpackState reloaded = gson.fromJson(originalRecord, BackpackState.class).normalized();
        assertTrue(BackpackCardMigration.migrate(reloaded, source, () -> true));
        assertEquals(3, reloaded.cards.get(FactionCardType.KILLER));
        assertEquals(0, source.get(FactionCardType.KILLER));
    }

    @Test
    void restartAfterDestinationWriteButBeforeSourceClearDoesNotDuplicateCards() {
        Gson gson = new Gson();
        BackpackState destination = BackpackState.createDefault();
        var source = legacyCards();
        AtomicReference<String> saved = new AtomicReference<>();
        assertThrows(IllegalStateException.class, () -> BackpackCardMigration.migrate(destination, source, () -> {
            saved.set(gson.toJson(destination));
            throw new IllegalStateException("simulated interruption after destination commit");
        }));
        BackpackState reloaded = gson.fromJson(saved.get(), BackpackState.class).normalized();
        assertTrue(BackpackCardMigration.migrate(reloaded, source, () -> true));
        assertEquals(3, reloaded.cards.get(FactionCardType.KILLER));
        assertEquals(0, source.get(FactionCardType.KILLER));
    }
}
