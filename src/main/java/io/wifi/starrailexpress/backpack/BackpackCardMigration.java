package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;

import java.util.Map;
import java.util.function.BooleanSupplier;

/** Retryable move: persist the destination before deleting the legacy counts. */
final class BackpackCardMigration {
    private BackpackCardMigration() {
    }

    static boolean migrate(BackpackState destination, Map<FactionCardType, Integer> source,
            BooleanSupplier persistDestination) {
        if (!destination.migrated) {
            for (FactionCardType type : FactionCardType.values()) {
                if (type != FactionCardType.NONE) {
                    long count = (long) destination.cards.getOrDefault(type, 0)
                            + Math.max(0, source.getOrDefault(type, 0));
                    destination.cards.put(type, (int) Math.min(Integer.MAX_VALUE, count));
                }
            }
            // Keep the in-memory marker on failure so a retry cannot merge twice.
            destination.migrated = true;
        }
        if (!persistDestination.getAsBoolean()) {
            return false;
        }
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                source.put(type, 0);
            }
        }
        return true;
    }
}
