package io.wifi.starrailexpress.game.modes.funny.rotation;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

public class LightningDraftState {

    public final List<ServerPlayer> allPlayers;
    public final int totalPlayers;

    // ===== 职业池与结果 =====
    public final ArrayList<SRERole> rolePool = new ArrayList<>();
    public final Map<UUID, SRERole> selectedRoles = new LinkedHashMap<>();
    public final Set<UUID> randomChoosers = new HashSet<>();

    // ===== 玩家顺序（按阵营权重降序，同权重随机）=====
    public final List<UUID> playerOrder = new ArrayList<>();

    // ===== 轮次控制 =====
    public int remainingRoles;
    public int currentRoundIndex = 0;
    public int playersInThisRound = 0;
    public Map<UUID, List<SRERole>> roundCandidates = new HashMap<>();
    public long roundStartTime;
    public int perPlayerTimeLimit;
    public boolean isSelecting = false;
    public int confirmCountdown = -1;

    // ===== 卡片追踪 =====
    private final Map<Integer, Integer> cardUsedCount = new HashMap<>();
    private final Map<Integer, Integer> cardMaxPerType = new HashMap<>();
    private final Set<UUID> cardReturnedPlayers = new HashSet<>();

    // ===== 特殊平民与最后阶段 =====

    public LightningDraftState(List<ServerPlayer> players) {
        this.allPlayers = new ArrayList<>(players);
        this.totalPlayers = players.size();
        this.remainingRoles = totalPlayers;
    }

    /**
     * 处理已离线的、尚未选择职业的玩家。
     * 
     * @param world 当前世界
     * @return 是否实际处理了任何离线玩家（用于决定是否重新广播）
     */
    public boolean handleOfflinePlayers(ServerLevel world) {
        List<UUID> offlineUnselected = new ArrayList<>();
        for (UUID uuid : playerOrder) {
            if (!selectedRoles.containsKey(uuid)) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.isRemoved()) {
                    offlineUnselected.add(uuid);
                }
            }
        }
        if (offlineUnselected.isEmpty())
            return false;

        for (UUID uuid : offlineUnselected) {
            // 若该离线玩家还在本轮候选人中，直接移除
            roundCandidates.remove(uuid);

            SRERole randomRole = selectRandomRole(world);
            selectedRoles.put(uuid, randomRole);
            rolePool.remove(randomRole);
            remainingRoles--;
            randomChoosers.add(uuid); // 视为随机
        }

        // 如果当前正在选择阶段，且本轮所有候选人都已完成（可能是因为移除了离线玩家后列表为空）
        if (isSelecting && roundCandidates.isEmpty()) {
            finishRound(world);
        }
        return true;
    }

    // ---------- 初始化角色池 ----------
    public void initializeRolePool(ServerLevel world) {
        rolePool.clear();

        int killerCount = Math.max(1, RoleCountManager.getKillerCount(totalPlayers));
        int vigilanteCount = Math.max(0, RoleCountManager.getVigilanteCount(totalPlayers));
        int neutralsCount = Math.max(0, RoleCountManager.getNeutralCount(totalPlayers));

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
                totalPlayers + 2, 0,
                killerPool, neutralsPool, vigilantePool, civilianPool, true);
        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null)
                rolePool.add(inst.role());
        }

        initializeCardTracking();
    }

    private void initializeCardTracking() {
        cardUsedCount.clear();
        cardMaxPerType.clear();
        cardReturnedPlayers.clear();
        int limit = Math.max(1, totalPlayers / 7);
        cardMaxPerType.put(4, limit);
        cardMaxPerType.put(2, limit);
        cardMaxPerType.put(1, limit);

        Map<Integer, List<UUID>> byType = new HashMap<>();
        for (ServerPlayer p : allPlayers) {
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(p.getUUID());
            if (forcedType != null) {
                int normalized = normalizeCardType(forcedType);
                byType.computeIfAbsent(normalized, k -> new ArrayList<>()).add(p.getUUID());
            }
        }
        for (Map.Entry<Integer, List<UUID>> entry : byType.entrySet()) {
            int type = entry.getKey();
            List<UUID> uuids = entry.getValue();
            int max = cardMaxPerType.getOrDefault(type, 0);
            cardUsedCount.put(type, Math.min(uuids.size(), max));
            for (int i = max; i < uuids.size(); i++) {
                UUID uid = uuids.get(i);
                PlayerRoleWeightManager.ForcePlayerTeam.remove(uid);
                cardReturnedPlayers.add(uid);
                ServerPlayer sp = allPlayers.stream().filter(p -> p.getUUID().equals(uid)).findFirst().orElse(null);
                if (sp != null) {
                    FactionCardType cardType = FactionCardType.fromInt(type);
                    if (cardType != FactionCardType.NONE) {
                        ProgressionDataManager.addFactionCard(sp, cardType, 1);
                        sp.displayClientMessage(Component.translatable("message.sre.role_rotation.card_limit")
                                .withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }

    private static int normalizeCardType(int rawType) {
        return switch (rawType) {
            case 5 -> 1;
            case 3 -> 2;
            default -> rawType;
        };
    }

    // ---------- 玩家顺序：按最大权重降序，同权重随机 ----------
    public void assignRotationOrder() {
        List<ServerPlayer> sorted = new ArrayList<>(allPlayers);
        final Random random = new Random();
        sorted.sort((a, b) -> {
            double w1 = PlayerRoleWeightManager.getMaxWeight(a);
            double w2 = PlayerRoleWeightManager.getMaxWeight(b);
            if (w1 != w2)
                return Double.compare(w2, w1); // 降序
            int aa = random.nextInt(), bb = random.nextInt();
            return aa > bb ? 1 : (aa == bb ? 0 : -1);
        });
        playerOrder.clear();
        for (ServerPlayer p : sorted) {
            playerOrder.add(p.getUUID());
        }
    }

    // ---------- 轮次计算（首轮增加3秒缓冲）----------
    public void startNextRound(ServerLevel world) {
        if (remainingRoles <= 0) {
            startConfirmCountdown();
            return;
        }

        int n = remainingRoles;
        int b = Math.max(1, n / 3);
        playersInThisRound = b;

        // 按顺序选取尚未选择的玩家
        List<UUID> roundPlayers = new ArrayList<>();
        for (UUID uuid : playerOrder) {
            if (!selectedRoles.containsKey(uuid)) {
                roundPlayers.add(uuid);
                if (roundPlayers.size() == b)
                    break;
            }
        }

        // 从池中随机抽取 need 个职业
        int need = Math.min(n, b * 3);
        List<SRERole> drawn = new ArrayList<>(rolePool);
        Collections.shuffle(drawn, new Random(world.getGameTime()));
        drawn = new ArrayList<>(drawn.subList(0, need));

        // 平均分配：每人最多3个
        roundCandidates.clear();
        int idx = 0;
        for (UUID playerId : roundPlayers) {
            int count = Math.min(3, need - idx);
            if (count <= 0)
                break;
            List<SRERole> candidates = new ArrayList<>(drawn.subList(idx, idx + count));
            roundCandidates.put(playerId, candidates);
            idx += count;
        }

        currentRoundIndex++;
        roundStartTime = world.getGameTime();

        // 计算基础时限：最大选项数 × 3秒
        int baseTime = roundCandidates.values().stream()
                .mapToInt(List::size).max().orElse(0) * 3 * 20;
        // 第一轮额外增加 3 秒（60 tick）缓冲时间
        if (currentRoundIndex == 1) {
            perPlayerTimeLimit = baseTime + 60; // 例如 3个选项 → 9+3=12秒
        } else {
            perPlayerTimeLimit = baseTime;
        }

        isSelecting = true;
        confirmCountdown = -1;
    }

    // ---------- 处理玩家选择 ----------
    public boolean processSelection(ServerLevel world, UUID playerUuid, int choiceIndex) {
        if (!isSelecting || !roundCandidates.containsKey(playerUuid))
            return false;

        List<SRERole> candidates = roundCandidates.get(playerUuid);
        SRERole chosen = null;
        if (choiceIndex >= 0 && choiceIndex < candidates.size()) {
            chosen = candidates.get(choiceIndex);
        } else if (choiceIndex == 3) { // 随机
            chosen = selectRandomRole(world);
            randomChoosers.add(playerUuid);
        }
        if (chosen == null)
            return false;

        selectedRoles.put(playerUuid, chosen);
        rolePool.remove(chosen);
        remainingRoles--;

        roundCandidates.remove(playerUuid);

        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("gui.sre.role_rotation.selected",
                            RoleUtils.getRoleName(chosen).withColor(chosen.getColor()))
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
        RoleUtils.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);

        if (roundCandidates.isEmpty()) {
            finishRound(world);
        }
        return true;
    }

    private void finishRound(ServerLevel world) {
        isSelecting = false;
        if (remainingRoles > 0) {
            playSoundToAll(world, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.5f);
            startNextRound(world); // 立刻开始下一轮
        } else {
            playSoundToAll(world, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            startConfirmCountdown();
        }
    }

    private void startConfirmCountdown() {
        isSelecting = false;
        confirmCountdown = 6 * 20;
    }

    private void playSoundToAll(ServerLevel world, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer p : world.players()) {
            RoleUtils.playSound(p, sound, SoundSource.MASTER, volume, pitch);
        }
    }

    private SRERole selectRandomRole(ServerLevel world) {
        if (rolePool.isEmpty())
            return TMMRoles.CIVILIAN;
        return rolePool.get(new Random(world.getGameTime()).nextInt(rolePool.size()));
    }

    public void timeoutUnfinishedPlayers(ServerLevel world) {
        if (!isSelecting)
            return;
        List<UUID> unfinished = new ArrayList<>(roundCandidates.keySet());
        for (UUID uuid : unfinished) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                SRERole randomRole = selectRandomRole(world);
                selectedRoles.put(uuid, randomRole);
                rolePool.remove(randomRole);
                remainingRoles--;
                randomChoosers.add(uuid);
                player.displayClientMessage(
                        Component.literal("选择超时，已随机分配职业").withStyle(ChatFormatting.RED),
                        true);
            }
        }
        roundCandidates.clear();
        finishRound(world);
    }

    // ---------- 调整剩余职业 ----------
    public void adjustRemainingRoles(ServerLevel serverWorld) {
    }

    // ---------- 供同步包使用的转换 ----------
    public Map<UUID, List<String>> getRoundCandidatesAsStrings() {
        return roundCandidates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(r -> r.identifier().toString()).toList()));
    }

    public Map<UUID, String> getSelectedRolesAsStrings() {
        return selectedRoles.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().identifier().toString()));
    }
}