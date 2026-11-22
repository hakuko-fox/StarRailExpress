package org.agmas.noellesroles.game.roles.vtuber;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VtuberV21RulesTest {
    @Test void stoneFormLastsTwentyFiveSecondsAtFullEnergy() {
        assertEquals(4, VtuberV21Rules.stoneEnergy(2000, true, 499));
        assertEquals(0, VtuberV21Rules.stoneEnergy(2000, true, 500));
        assertEquals(0, VtuberV21Rules.stoneEnergy(2000, true, 1000));
    }

    @Test void penguinFormRechargesOnePercentPerSecondAndCapsAtFull() {
        assertEquals(20, VtuberV21Rules.stoneEnergy(0, false, 20));
        assertEquals(2000, VtuberV21Rules.stoneEnergy(0, false, 2000));
        assertEquals(2000, VtuberV21Rules.stoneEnergy(1999, false, 20));
        assertEquals(2000, VtuberV21Rules.stoneEnergy(1, false, Integer.MAX_VALUE));
    }

    @Test void neutralTaskGoalRoundsSeventyPercentUpForEachRosterSize() {
        for (int players = 1; players <= 64; players++) {
            int goal = VtuberV21Rules.taskGoal(players);
            assertTrue(goal * 10 >= players * 7);
            assertTrue((goal - 1) * 10 < players * 7);
        }
        assertEquals(7, VtuberV21Rules.taskGoal(10));
        assertEquals(8, VtuberV21Rules.taskGoal(11));
    }

    @Test void knifeAndGunFatalDelaysDiffer() {
        assertEquals(300, VtuberV21Rules.fatalDelayTicks(false));
        assertEquals(200, VtuberV21Rules.fatalDelayTicks(true));
    }

    @Test void repeatedHitsCannotPostponeDeathAndEarlierGunDamageTakesPriority() {
        long knife = VtuberV21Rules.fatalDeadline(0, 100, false);
        assertEquals(400, knife);
        assertEquals(400, VtuberV21Rules.fatalDeadline(knife, 120, false));
        assertEquals(320, VtuberV21Rules.fatalDeadline(knife, 120, true));
        assertEquals(320, VtuberV21Rules.fatalDeadline(320, 200, false));
    }

    @Test void sleepRoleReducesCooldownWhileEvilAndDecoyHitsSkipIt() {
        assertEquals(70, VtuberV21Rules.weaponCooldown(100, "yuzu_fengling", false, false));
        assertEquals(0, VtuberV21Rules.weaponCooldown(100, "ayers", true, false));
        assertEquals(100, VtuberV21Rules.weaponCooldown(100, "ayers", false, false));
        assertEquals(0, VtuberV21Rules.weaponCooldown(100, "civilian", false, true));
        assertEquals(100, VtuberV21Rules.weaponCooldown(100, "civilian", false, false));
    }
}
