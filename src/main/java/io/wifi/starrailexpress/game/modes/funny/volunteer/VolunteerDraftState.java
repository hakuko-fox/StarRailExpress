package io.wifi.starrailexpress.game.modes.funny.volunteer;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import net.exmo.sre.repair.role.RepairRole;

import java.util.*;
import java.util.stream.Collectors;

public class VolunteerDraftState {

    private final List<ServerPlayer> players;
    private final int volunteerCount;
    private final Map<UUID, List<SRERole>> candidatePool = new HashMap<>();
    public final Map<UUID, List<Integer>> submittedPreferences = new HashMap<>();
    private final Map<UUID, SRERole> finalAssignment = new LinkedHashMap<>();
    public final Set<UUID> submittedPlayers = new HashSet<>();
    private final List<SRERole> globalRolePool = new ArrayList<>();
    // 记录哪些玩家是手动提交的（通过客户端提交）
    public final Set<UUID> manuallySubmitted = new HashSet<>();
    public Phase phase = Phase.WAITING;
    public long phaseStartTime;
    private int commitTimeLimit = 60 * 20;
    // 新增字段
    private int resultTimeLimit = 5 * 20; // 结果展示 10 秒
    private int adjustTimeLimit = 5; // 5tick
    private final Random random;
    private final ServerLevel world;

    public void startCommitPhase(long gameTime) {
        this.commitTimeLimit = SREConfig.instance().volunteerModeSelectionTime * 20;
        this.phase = Phase.COMMIT;
        this.phaseStartTime = gameTime;
    }

    public VolunteerDraftState(List<ServerPlayer> players, ServerLevel world) {
        this.players = new ArrayList<>(players);
        this.world = world;
        this.random = new Random(world.getGameTime());
        this.volunteerCount = SREConfig.instance().volunteerModeVolunteerCount;
        if (volunteerCount < 2)
            throw new IllegalStateException("volunteerModeVolunteerCount must be at least 2");
        initializePools(world);
        generateCandidates();
        phaseStartTime = world.getGameTime();
    }

    private void initializePools(ServerLevel world) {
        int total = players.size();
        int killerCount = Math.max(1, RoleCountManager.getKillerCount(total));
        int vigilanteCount = Math.max(0, RoleCountManager.getVigilanteCount(total));
        int neutralsCount = Math.max(0, RoleCountManager.getNeutralCount(total));

        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        boolean enableCivilianInPool = config.enableCivilianInPool;

        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        !RoleUtils.compareRole(role, ModRoles.PUPPETEER) &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.isVigilanteTeam() &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole));
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        ((!role.canUseKiller() && !role.isInnocent()) || role.isNeutrals()) &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        (enableCivilianInPool || role != TMMRoles.CIVILIAN));

        if (enableCivilianInPool) {
            Harpymodloader.setRoleMaximum(TMMRoles.CIVILIAN.getIdentifier(), 1);
        }

        List<RoleInstance> baseRoles = SREMurderGameMode.getAllRoles(
                killerCount, vigilanteCount, neutralsCount,
                total, 0,
                killerPool, neutralsPool, vigilantePool, civilianPool, true);
        globalRolePool.clear();
        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null)
                globalRolePool.add(inst.role());
        }
        while (globalRolePool.size() < total) {
            globalRolePool.add(TMMRoles.CIVILIAN);
        }
    }

    private void generateCandidates() {
        int maxPerRole = volunteerCount; // 每个角色最多出现在候选列表中的次数
        Map<String, Integer> roleUsage = new HashMap<>(); // 记录角色已被选入候选的次数

        // 玩家处理顺序随机化
        List<ServerPlayer> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers, random);

        for (ServerPlayer player : shuffledPlayers) {
            UUID pid = player.getUUID();
            List<SRERole> factionPool = getFactionPool(player); // 该玩家可选的阵营池

            // 分离：未达上限的角色 vs 已达上限的角色
            List<SRERole> underLimit = new ArrayList<>();
            List<SRERole> overLimit = new ArrayList<>();
            for (SRERole role : factionPool) {
                String id = role.identifier().toString();
                int used = roleUsage.getOrDefault(id, 0);
                if (used < maxPerRole) {
                    underLimit.add(role);
                } else {
                    overLimit.add(role);
                }
            }

            List<SRERole> chosen = new ArrayList<>();
            // 先从 underLimit 中随机选择，至多 volunteerCount 个
            Collections.shuffle(underLimit, random);
            int takeFromUnder = Math.min(volunteerCount, underLimit.size());
            for (int i = 0; i < takeFromUnder; i++) {
                SRERole r = underLimit.get(i);
                chosen.add(r);
                roleUsage.merge(r.identifier().toString(), 1, Integer::sum);
            }

            // 如果还不够 volunteerCount，从 overLimit（已达上限）中补足
            int remaining = volunteerCount - chosen.size();
            if (remaining > 0) {
                Collections.shuffle(overLimit, random);
                for (int i = 0; i < remaining && i < overLimit.size(); i++) {
                    SRERole r = overLimit.get(i);
                    chosen.add(r);
                    roleUsage.merge(r.identifier().toString(), 1, Integer::sum); // 允许超出上限
                }
            }

            // 极端情况：阵营池本身不足 volunteerCount，用全局池补足（保留原 fallback 逻辑）
            if (chosen.size() < volunteerCount) {
                List<SRERole> globalPool = new ArrayList<>(globalRolePool);
                globalPool.removeAll(chosen);
                Collections.shuffle(globalPool, random);
                while (chosen.size() < volunteerCount && !globalPool.isEmpty()) {
                    SRERole r = globalPool.remove(0);
                    chosen.add(r);
                    roleUsage.merge(r.identifier().toString(), 1, Integer::sum);
                }
            }

            candidatePool.put(pid, chosen);
        }
    }

    private List<SRERole> getFactionPool(ServerPlayer player) {
        Integer forced = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
        if (forced != null) {
            int type = normalizeCardType(forced);
            return globalRolePool.stream()
                    .filter(r -> PlayerRoleWeightManager.getRoleType(r) == type)
                    .collect(Collectors.toList());
        }
        return globalRolePool.stream()
                .filter(r -> r.isInnocent() && !r.canUseKiller())
                .collect(Collectors.toList());
    }

    private int normalizeCardType(int raw) {
        return switch (raw) {
            case 5 -> 1;
            case 3 -> 2;
            default -> raw;
        };
    }

    public boolean submitPreference(UUID playerId, List<Integer> orderedPreferences) {
        if (submittedPlayers.contains(playerId))
            return false;
        List<SRERole> candidates = candidatePool.get(playerId);
        if (candidates == null)
            return false;
        for (int pref : orderedPreferences) {
            if (pref != -1 && (pref < 0 || pref >= candidates.size()))
                return false;
        }
        submittedPreferences.put(playerId, new ArrayList<>(orderedPreferences));
        submittedPlayers.add(playerId);
        manuallySubmitted.add(playerId); // 标记手动提交
        if (submittedPlayers.size() >= players.size()) {
            phase = Phase.ADJUST;
            phaseStartTime = world.getGameTime();
            return true;
        }
        return false;
    }

    public void runAssignment() {
        Map<String, Integer> capacity = new HashMap<>();
        for (SRERole role : globalRolePool) {
            capacity.merge(role.identifier().toString(), 1, Integer::sum);
        }

        // --- 第一阶段：手动提交者按权重平行志愿分配 ---
        List<UUID> manualPlayers = new ArrayList<>(manuallySubmitted);
        manualPlayers.sort((a, b) -> {
            int wA = candidatePool.getOrDefault(a, List.of()).stream()
                    .mapToInt(r -> getWeight(a, r)).max().orElse(0);
            int wB = candidatePool.getOrDefault(b, List.of()).stream()
                    .mapToInt(r -> getWeight(b, r)).max().orElse(0);
            if (wA != wB)
                return Integer.compare(wB, wA);
            return Integer.compare(Objects.hash(a, random.nextInt()), Objects.hash(b, random.nextInt()));
        });

        List<UUID> remainingManual = new ArrayList<>();
        for (UUID pid : manualPlayers) {
            List<Integer> prefs = submittedPreferences.get(pid);
            List<SRERole> candidates = candidatePool.get(pid);
            boolean assigned = false;
            if (prefs != null && candidates != null) {
                for (int choice : prefs) {
                    if (choice == -1)
                        continue;
                    if (choice < 0 || choice >= candidates.size())
                        continue;
                    SRERole target = candidates.get(choice);
                    String rid = target.identifier().toString();
                    if (capacity.getOrDefault(rid, 0) > 0) {
                        finalAssignment.put(pid, target);
                        capacity.merge(rid, -1, Integer::sum);
                        assigned = true;
                        break;
                    }
                }
            }
            if (!assigned)
                remainingManual.add(pid);
        }

        // 处理手动玩家中带有 -1 随机意愿的
        List<UUID> finalManualRemaining = new ArrayList<>();
        for (UUID pid : remainingManual) {
            List<Integer> prefs = submittedPreferences.get(pid);
            if (prefs != null && prefs.contains(-1)) {
                List<SRERole> available = getAvailableRolesForPlayer(pid, capacity);
                if (!available.isEmpty()) {
                    available.sort(Comparator.comparingInt(r -> -getWeight(pid, r)));
                    finalAssignment.put(pid, available.get(0));
                    capacity.merge(available.get(0).identifier().toString(), -1, Integer::sum);
                    continue;
                }
            }
            finalManualRemaining.add(pid);
        }
        // 兜底
        for (UUID pid : finalManualRemaining) {
            List<SRERole> available = getAvailableRolesForPlayer(pid, capacity);
            if (!available.isEmpty()) {
                available.sort(Comparator.comparingInt(r -> -getWeight(pid, r)));
                finalAssignment.put(pid, available.get(0));
            } else {
                finalAssignment.put(pid, TMMRoles.CIVILIAN);
            }
        }

        // --- 第二阶段：自动提交者从剩余容量中分配 ---
        List<UUID> autoPlayers = new ArrayList<>(submittedPlayers);
        autoPlayers.removeAll(manuallySubmitted);
        for (UUID pid : autoPlayers) {
            List<SRERole> available = getAvailableRolesForPlayer(pid, capacity);
            if (!available.isEmpty()) {
                available.sort(Comparator.comparingInt(r -> -getWeight(pid, r)));
                finalAssignment.put(pid, available.get(0));
                capacity.merge(available.get(0).identifier().toString(), -1, Integer::sum);
            } else {
                finalAssignment.put(pid, TMMRoles.CIVILIAN);
            }
        }

        phase = Phase.RESULT;
        phaseStartTime = world.getGameTime();
    }

    public int getResultTimeLimit() {
        return resultTimeLimit;
    }

    private List<SRERole> getAvailableRolesForPlayer(UUID playerId, Map<String, Integer> capacity) {
        ServerPlayer player = players.stream().filter(p -> p.getUUID().equals(playerId)).findFirst().orElse(null);
        if (player == null)
            return List.of();
        List<SRERole> factionPool = getFactionPool(player);
        List<SRERole> available = new ArrayList<>();
        for (SRERole role : factionPool) {
            if (capacity.getOrDefault(role.identifier().toString(), 0) > 0) {
                available.add(role);
            }
        }
        if (available.isEmpty()) {
            // 回退到全池剩余
            for (SRERole role : globalRolePool) {
                if (capacity.getOrDefault(role.identifier().toString(), 0) > 0) {
                    available.add(role);
                }
            }
        }
        return available;
    }

    private int getWeight(UUID playerId, SRERole role) {
        int type = PlayerRoleWeightManager.getRoleType(role);
        return (int) (PlayerRoleWeightManager.getRoleWeightPercent(playerId, type) * 100);
    }

    // Getters
    public Phase getPhase() {
        return phase;
    }

    public long getPhaseStartTime() {
        return phaseStartTime;
    }

    public int getCommitTimeLimit() {
        return commitTimeLimit;
    }

    public int getVolunteerCount() {
        return volunteerCount;
    }

    public List<String> getMyCandidateIds(UUID uuid) {
        return candidatePool.getOrDefault(uuid, List.of()).stream()
                .map(r -> r.identifier().toString()).toList();
    }

    public Map<UUID, String> getFinalRolesAsStrings() {
        return finalAssignment.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().identifier().toString()));
    }

    public String getMyFinalRoleId(UUID uuid) {
        SRERole role = finalAssignment.get(uuid);
        return role != null ? role.identifier().toString() : "";
    }

    public Map<UUID, SRERole> getFinalAssignment() {
        return finalAssignment;
    }

    public enum Phase {
        WAITING, COMMIT, ADJUST, RESULT
    }

    // 提供 getter
    public int getAdjustTimeLimit() {
        return adjustTimeLimit;
    }

    // 玩家退出处理：移除该玩家所有数据，若剩余玩家均已提交则直接进入 ADJUST
    public boolean removePlayer(UUID playerId) {
        candidatePool.remove(playerId);

        manuallySubmitted.remove(playerId);
        submittedPreferences.remove(playerId);
        submittedPlayers.remove(playerId);
        players.removeIf(p -> p.getUUID().equals(playerId));
        if (phase == Phase.COMMIT && !players.isEmpty() && submittedPlayers.size() == players.size()) {
            phase = Phase.ADJUST;
            phaseStartTime = world.getGameTime();
            return true;
        }
        return false;
    }

    public void setPhase(Phase adjust) {
        this.phase = adjust;
    }

    public void setPhaseStartTime(long gameTime) {
        this.phaseStartTime = gameTime;
    }
}