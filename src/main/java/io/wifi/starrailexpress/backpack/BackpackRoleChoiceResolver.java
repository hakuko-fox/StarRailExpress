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

    public record Request(UUID playerId, String roleId) {
    }

    public record Resolution(Map<UUID, String> winners, Set<UUID> losers) {
    }

    public static Resolution resolve(List<Request> requests, Map<String, Integer> capacities,
            Map<String, Integer> alreadyReserved, Random random) {
        Map<String, List<Request>> grouped = new HashMap<>();
        for (Request request : requests) {
            grouped.computeIfAbsent(request.roleId(), ignored -> new ArrayList<>()).add(request);
        }

        Map<UUID, String> winners = new HashMap<>();
        Set<UUID> losers = new HashSet<>();
        for (Map.Entry<String, List<Request>> entry : grouped.entrySet()) {
            List<Request> candidates = entry.getValue();
            candidates = new ArrayList<>(candidates);
            java.util.Collections.shuffle(candidates, random);
            int capacity = Math.max(0, capacities.getOrDefault(entry.getKey(), 1));
            int reserved = Math.max(0, alreadyReserved.getOrDefault(entry.getKey(), 0));
            for (int index = 0; index < candidates.size(); index++) {
                Request request = candidates.get(index);
                if (index < Math.max(0, capacity - reserved)) {
                    winners.put(request.playerId(), request.roleId());
                } else {
                    losers.add(request.playerId());
                }
            }
        }
        return new Resolution(winners, losers);
    }
}
