package io.wifi.starrailexpress.vtuberstore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VtuberRoundRewardsTest {
    @Test
    void survivingWinnersAndLosersReceiveTheirOwnRates() {
        VtuberStoreConfig.Rewards rewards = new VtuberStoreConfig.Rewards();

        assertEquals(6, rewards.amountForRound(true, true));
        assertEquals(2, rewards.amountForRound(false, true));
    }

    @Test
    void eliminatedPlayersReceiveDeadRatesEvenWhenTheirTeamWins() {
        VtuberStoreConfig.Rewards rewards = new VtuberStoreConfig.Rewards();

        assertEquals(3, rewards.amountForRound(true, false));
        assertEquals(1, rewards.amountForRound(false, false));
    }

    @Test
    void serverConfigurationControlsAllFourOutcomesIncludingDisabledRewards() {
        VtuberStoreConfig.Rewards rewards = new VtuberStoreConfig.Rewards();
        rewards.winnerAlive = 17;
        rewards.winnerDead = 11;
        rewards.loserAlive = 5;
        rewards.loserDead = 0;

        assertEquals(17, rewards.amountForRound(true, true));
        assertEquals(11, rewards.amountForRound(true, false));
        assertEquals(5, rewards.amountForRound(false, true));
        assertEquals(0, rewards.amountForRound(false, false));
    }
}
