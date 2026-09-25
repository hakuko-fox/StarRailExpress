package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.game.modes.funny.FactionCardUtils;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionCardUtilsTest {
    private record Slot(int type, int number) {}

    @Test
    void cardGetsActualSlotBeforeWeightRequestAndFailedCardReturnsToNormalAssignment() {
        UUID weighted = UUID.randomUUID();
        UUID card = UUID.randomUUID();
        UUID failedCard = UUID.randomUUID();
        List<UUID> unassigned = new ArrayList<>(List.of(weighted, card, failedCard));
        Map<UUID, ForceTeamInfo> requests = new LinkedHashMap<>();
        requests.put(weighted, new ForceTeamInfo(4, ForceTeamType.ROLE_WEIGHTS));
        requests.put(card, new ForceTeamInfo(4, ForceTeamType.CARD));
        requests.put(failedCard, new ForceTeamInfo(2, ForceTeamType.CARD));
        Map<Slot, Float> slots = new LinkedHashMap<>();
        Slot killer = new Slot(4, 1);
        Slot civilian = new Slot(1, 2);
        slots.put(killer, 1f);
        slots.put(civilian, 1f);
        Map<UUID, Slot> assigned = new HashMap<>();
        List<UUID> refunds = new ArrayList<>();

        FactionCardUtils.assignForcedTeams(unassigned, id -> id, requests, slots, Slot::type,
                available -> available.keySet().iterator().next(), assigned::put,
                (player, request) -> {
                    if (request.type() == ForceTeamType.CARD) refunds.add(player);
                });

        assertEquals(killer, assigned.get(card));
        assertEquals(List.of(failedCard), refunds);
        assertTrue(unassigned.contains(failedCard));
        assertTrue(unassigned.contains(weighted));
        assertFalse(requests.containsKey(failedCard));
        assertEquals(Map.of(civilian, 1f), slots);
    }

    @Test
    void civilianCardCanUseVigilanteSlot() {
        UUID card = UUID.randomUUID();
        List<UUID> unassigned = new ArrayList<>(List.of(card));
        Slot vigilante = new Slot(5, 1);
        Map<Slot, Float> slots = new LinkedHashMap<>(Map.of(vigilante, 1f));
        Map<UUID, Slot> assigned = new HashMap<>();

        FactionCardUtils.assignForcedTeams(unassigned, id -> id,
                Map.of(card, new ForceTeamInfo(1, ForceTeamType.CARD)), slots, Slot::type,
                available -> available.keySet().iterator().next(), assigned::put,
                (player, request) -> { throw new AssertionError("card should have a slot"); });

        assertEquals(vigilante, assigned.get(card));
        assertTrue(unassigned.isEmpty());
        assertTrue(slots.isEmpty());
    }
}
