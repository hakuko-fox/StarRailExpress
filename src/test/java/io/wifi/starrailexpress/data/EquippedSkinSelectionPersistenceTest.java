package io.wifi.starrailexpress.data;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquippedSkinSelectionPersistenceTest {

    @Test
    void dedicatedEquippedPartitionRestoresSelectionAfterLogin() {
        Map<String, String> resolved = EquippedSkinSelectionPersistence.resolve(
                Map.of("knife", "ruby"),
                Map.of("knife", "default"),
                "{\"equipped\":{\"knife\":\"ruby\",\"hat\":\"conductor\"}}");

        assertEquals("ruby", resolved.get("knife"));
        assertEquals("conductor", resolved.get("hat"));
    }

    @Test
    void localSelectionSurvivesWhenRemoteSyncIsUnavailable() {
        Map<String, String> resolved = EquippedSkinSelectionPersistence.resolve(
                Map.of("knife", "ruby"), null, null);

        assertEquals("ruby", resolved.get("knife"));
    }

    @Test
    void legacyRemoteSelectionIsUsedWhenDedicatedPartitionIsMissing() {
        Map<String, String> resolved = EquippedSkinSelectionPersistence.resolve(
                Map.of("knife", "local"), Map.of("knife", "legacy"), null);

        assertEquals("legacy", resolved.get("knife"));
    }

    @Test
    void malformedDedicatedPartitionFallsBackWithoutClearingSelection() {
        Map<String, String> resolved = EquippedSkinSelectionPersistence.resolve(
                Map.of("knife", "local"), Map.of("knife", "legacy"), "{not-json");

        assertEquals("legacy", resolved.get("knife"));
    }

    @Test
    void emptyDedicatedSnapshotClearsLegacySelections() {
        Map<String, String> resolved = EquippedSkinSelectionPersistence.resolve(
                Map.of("knife", "local"), Map.of("knife", "legacy"), "{\"equipped\":{}}");

        assertTrue(resolved.isEmpty());
    }
}
