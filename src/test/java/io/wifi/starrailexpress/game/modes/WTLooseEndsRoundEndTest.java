package io.wifi.starrailexpress.game.modes;

import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WTLooseEndsRoundEndTest {
    @Test
    void simultaneousEliminationSettlesWithoutAWinnerEvenWhenTimeExpires() {
        assertEquals(WinStatus.NO_PLAYER, WTLooseEndsGameMode.getRoundWinStatus(0, true));
        assertEquals(WinStatus.NO_PLAYER, WTLooseEndsGameMode.getRoundWinStatus(0, false));
    }

    @Test
    void lastSurvivorWinsEvenWhenTimeExpires() {
        assertEquals(WinStatus.LOOSE_END, WTLooseEndsGameMode.getRoundWinStatus(1, true));
        assertEquals(WinStatus.LOOSE_END, WTLooseEndsGameMode.getRoundWinStatus(1, false));
    }

    @Test
    void multipleSurvivorsOnlySettleWhenTimeExpires() {
        assertEquals(WinStatus.NONE, WTLooseEndsGameMode.getRoundWinStatus(2, true));
        assertEquals(WinStatus.TIME, WTLooseEndsGameMode.getRoundWinStatus(2, false));
    }
}
