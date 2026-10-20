package io.wifi.starrailexpress.backpack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleChoiceFactionCapacityTest {
    private BackpackRoleChoiceResolver.Request request(String role, int type) {
        return new BackpackRoleChoiceResolver.Request(UUID.randomUUID(), role, type);
    }

    @Test
    void differentKillerRolesCompeteForOneSharedFactionSlot() {
        var first = request("sre:killer_a", 4);
        var second = request("sre:killer_b", 4);
        var result = BackpackRoleChoiceResolver.resolve(List.of(first, second),
                Map.of(first.roleId(), 1, second.roleId(), 1), Map.of(), Map.of(4, 1), Map.of(), new Random(7));
        assertEquals(1, result.winners().size());
        assertEquals(1, result.losers().size());
    }

    @Test
    void bothNeutralSubtypesConsumeTheSameNeutralCapacity() {
        var neutral = request("sre:neutral", 2);
        var killerNeutral = request("sre:killer_neutral", 3);
        var result = BackpackRoleChoiceResolver.resolve(List.of(neutral, killerNeutral),
                Map.of(neutral.roleId(), 1, killerNeutral.roleId(), 1), Map.of(), Map.of(2, 1), Map.of(), new Random(7));
        assertEquals(1, result.winners().size());
        assertEquals(1, result.losers().size());
    }

    @Test
    void administratorReservationsLeaveNoExtraFactionSlot() {
        var killer = request("sre:killer", 4);
        var result = BackpackRoleChoiceResolver.resolve(List.of(killer), Map.of(killer.roleId(), 1),
                Map.of("sre:admin_killer", 1), Map.of(4, 1), Map.of(4, 1), new Random(7));
        assertTrue(result.winners().isEmpty());
        assertEquals(java.util.Set.of(killer.playerId()), result.losers());
    }

    @Test
    void zeroFactionCapacityRefundsEveryApplicant() {
        var killer = request("sre:killer", 4);
        var result = BackpackRoleChoiceResolver.resolve(List.of(killer), Map.of(killer.roleId(), 1),
                Map.of(), Map.of(4, 0), Map.of(), new Random(7));
        assertTrue(result.winners().isEmpty());
        assertEquals(1, result.losers().size());
    }

    @Test
    void roleCapacityStillAppliesWhenFactionHasSpareSlots() {
        var first = request("sre:doctor", 1);
        var second = request("sre:doctor", 1);
        var result = BackpackRoleChoiceResolver.resolve(List.of(first, second), Map.of(first.roleId(), 1),
                Map.of(), Map.of(1, 5), Map.of(), new Random(7));
        assertEquals(1, result.winners().size());
        assertEquals(1, result.losers().size());
    }

    @Test
    void fullKillerFactionDoesNotRejectIndependentCivilianAndVigilanteChoices() {
        var killer = request("sre:killer", 4);
        var civilian = request("sre:doctor", 1);
        var vigilante = request("sre:sheriff", 5);
        var requests = List.of(killer, civilian, vigilante);
        var capacities = Map.of(killer.roleId(), 1, civilian.roleId(), 1, vigilante.roleId(), 1);
        var result = BackpackRoleChoiceResolver.resolve(requests, capacities, Map.of(),
                Map.of(4, 0, 1, 1, 5, 1), Map.of(), new Random(7));
        assertEquals(Map.of(civilian.playerId(), civilian.roleId(), vigilante.playerId(), vigilante.roleId()),
                result.winners());
        assertEquals(java.util.Set.of(killer.playerId()), result.losers());
    }
}
