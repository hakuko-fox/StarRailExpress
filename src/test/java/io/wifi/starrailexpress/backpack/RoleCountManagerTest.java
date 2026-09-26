package io.wifi.starrailexpress.backpack;

import org.agmas.harpymodloader.commands.RoleCountManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleCountManagerTest {
    private final int originalKillerCount = RoleCountManager.forcedKillerCount;
    private final int originalVigilanteCount = RoleCountManager.forcedVigilanteCount;

    @AfterEach
    void restoreCounts() {
        RoleCountManager.forcedKillerCount = originalKillerCount;
        RoleCountManager.forcedVigilanteCount = originalVigilanteCount;
    }

    @Test
    void explicitCountsOverrideAutomaticRatios() {
        RoleCountManager.forcedKillerCount = 2;
        RoleCountManager.forcedVigilanteCount = 1;

        assertEquals(2, RoleCountManager.getKillerCount(6));
        assertEquals(2, RoleCountManager.getDraftKillerCount(6));
        assertEquals(1, RoleCountManager.getVigilanteCount(6));
    }

    @Test
    void explicitZeroIsPreservedInDraftModes() {
        RoleCountManager.forcedKillerCount = 0;

        assertEquals(0, RoleCountManager.getKillerCount(12));
        assertEquals(0, RoleCountManager.getDraftKillerCount(12));
    }

    @Test
    void draftAutomaticMinimumAppliesOnlyWhenThereArePlayers() {
        RoleCountManager.forcedKillerCount = -1;

        assertEquals(0, RoleCountManager.getKillerCount(3));
        assertEquals(1, RoleCountManager.getDraftKillerCount(3));
        assertEquals(0, RoleCountManager.getDraftKillerCount(0));
    }

    @Test
    void explicitCountCannotExceedPlayerCount() {
        RoleCountManager.forcedKillerCount = 20;

        assertEquals(3, RoleCountManager.getKillerCount(3));
        assertEquals(3, RoleCountManager.getDraftKillerCount(3));
    }
}
