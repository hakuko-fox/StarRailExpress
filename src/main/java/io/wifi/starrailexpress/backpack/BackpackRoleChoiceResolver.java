package io.wifi.starrailexpress.backpack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Pure conflict resolver for concrete role reservations. */
public final class BackpackRoleChoiceResolver {
    private BackpackRoleChoiceResolver() {
    }

    public record Request(UUID playerId, String roleId, int roleType) {
        public Request(UUID playerId, String roleId) {
            this(playerId, roleId, 0);
        }
    }

    public record Resolution(Map<UUID, String> winners, Set<UUID> losers) {
    }

    public static Resolution resolve(List<Request> requests, Map<String, Integer> capacities,
            Map<String, Integer> alreadyReserved, Random random) {
        return resolve(requests, capacities, alreadyReserved, Map.of(), Map.of(), random);
    }

    /** Both neutral subtypes share the round's neutral slots. */
    public static int faction(int roleType) {
        return roleType == 3 ? 2 : roleType;
    }

    public static Resolution resolve(List<Request> requests, Map<String, Integer> capacities,
            Map<String, Integer> alreadyReserved, Map<Integer, Integer> factionCapacities,
            Map<Integer, Integer> reservedFactions, Random random) {
        List<Request> candidates = new ArrayList<>(requests);
        java.util.Collections.shuffle(candidates, random);
        Map<String, Integer> roleCounts = new HashMap<>(alreadyReserved);
        Map<Integer, Integer> factionCounts = new HashMap<>(reservedFactions);

        Map<UUID, String> winners = new HashMap<>();
        Set<UUID> losers = new HashSet<>();
        for (Request request : candidates) {
            int faction = faction(request.roleType());
            int roleCount = Math.max(0, roleCounts.getOrDefault(request.roleId(), 0));
            int factionCount = Math.max(0, factionCounts.getOrDefault(faction, 0));
            if (roleCount < Math.max(0, capacities.getOrDefault(request.roleId(), 1))
                    && factionCount < Math.max(0, factionCapacities.getOrDefault(faction, Integer.MAX_VALUE))) {
                winners.put(request.playerId(), request.roleId());
                roleCounts.put(request.roleId(), roleCount + 1);
                factionCounts.put(faction, factionCount + 1);
            } else {
                losers.add(request.playerId());
            }
        }
        return new Resolution(winners, losers);
    }
}
