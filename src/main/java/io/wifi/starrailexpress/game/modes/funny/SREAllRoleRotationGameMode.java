/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.EggRoleInterface;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.data.AllRoleRotationSavedData;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.RoleWeightedUtil;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.harpymodloader.modded_murder.ModdedWeights;

import java.util.*;
import java.util.function.Predicate;

/**
 * 全職業輪跑模式（sre:all_role_rotation）。
 *
 * 跨多局的持久佇列：每局由伺服器依佇列順序自動分配職業，優先派出「從未玩過」的職業，
 * 再派出「最久沒玩過」的職業，直到所有合格職業都曾被遊玩過一遍後持續公平循環。
 *
 * - 維持殺手 / 義警 / 中立 / 平民的比例（沿用 RoleCountManager）。
 * - 每局依「當前地圖」的 disabledRoles 動態排除被禁用的職業（SREDisableManager）。
 * - 進度（每職業的被遊玩次數、上次被遊玩的回合）儲存於世界資料，跨重啟保留。
 */
public class SREAllRoleRotationGameMode extends SREMurderGameMode {

    public SREAllRoleRotationGameMode(ResourceLocation identifier) {
        super(identifier, 10, 3);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        Harpymodloader.refreshRoles();
        gameWorldComponent.clearRoleMap();
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");
        MurderTimeEventComponent.KEY.get(serverWorld).initializeDefaults();
        Harpymodloader.setRoleMaximum(org.agmas.noellesroles.role.ModRoles.SHERIFF_ID, 100);

        assignRoleRotation(serverWorld, gameWorldComponent, players);
    }

    /** 依輪跑佇列分配職業，並執行與 Murder 一致的應用流程（報幕 / 商店 / 修飾符）。 */
    private void assignRoleRotation(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        AllRoleRotationSavedData state = AllRoleRotationSavedData.get(serverWorld);
        Map<Player, SRERole> roleAssignments = buildRotationAssignments(serverWorld, state, players);

        OnGamePlayerRolesConfirm.EVENT.invoker().beforeAssignRole(serverWorld, roleAssignments);

        long killCount = roleAssignments.values().stream()
                .filter(role -> role != null && role != TMMRoles.CIVILIAN && role.canUseKiller())
                .count();

        for (Map.Entry<Player, SRERole> entry : roleAssignments.entrySet()) {
            SRERole value = entry.getValue();
            if (value != null) {
                gameWorldComponent.addRole(entry.getKey(), value, false);
                if (value.canUseKiller()) {
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(entry.getKey());
                    if (shop.balance < GameConstants.getMoneyStart())
                        shop.setBalance(GameConstants.getMoneyStart());
                }
            } else {
                gameWorldComponent.addRole(entry.getKey(), TMMRoles.CIVILIAN, false);
            }
        }
        gameWorldComponent.syncRoles();

        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            int roleType = PlayerRoleWeightManager.getRoleType(role);
            PlayerRoleWeightManager.addWeight(player, roleType, 1);
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.getIdentifier().toString(), (int) killCount,
                            (int) (players.size() - killCount)));
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }

        // 標記本局實際被遊玩的職業，並進入下一回合
        for (SRERole role : new HashSet<>(roleAssignments.values())) {
            if (role != null)
                state.markPlayed(role.getIdentifier().toString());
        }
        state.advanceRound();

        int modifierRoleCount = (int) ((float) players.size()
                * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        SREMurderGameMode.assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, players);

        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    /**
     * 依佇列順序決定本局各陣營要派出的職業，再對應到玩家身上。
     * 玩家對應邏輯沿用 Murder（強制職業 → 弰營卡 → 反權重），以保留公平性。
     */
    private Map<Player, SRERole> buildRotationAssignments(ServerLevel serverWorld,
            AllRoleRotationSavedData state, List<ServerPlayer> players) {
        Map<Player, SRERole> roleAssignments = new HashMap<>();
        for (Player player : players)
            roleAssignments.put(player, null);

        RandomSource random = serverWorld.getRandom();

        // 第一步：強制分配的職業
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE);
        Set<ServerPlayer> forcedPlayers = new HashSet<>();
        int killerCount = RoleCountManager.getKillerCount(players.size());
        int vigilanteCount = RoleCountManager.getVigilanteCount(players.size());
        int neutralsCount = RoleCountManager.getNeutralCount(players.size());
        Map<Integer, Integer> factionSeats = new HashMap<>(Map.of(
                4, killerCount, 5, vigilanteCount, 2, neutralsCount,
                1, Math.max(0, players.size() - killerCount - vigilanteCount - neutralsCount)));
        boolean eggAssigned = false;
        List<ServerPlayer> companionPlayers = new ArrayList<>(players);
        companionPlayers.removeIf(player -> forcedRoles.containsKey(player.getUUID()));
        Collections.shuffle(companionPlayers, new Random(random.nextLong()));
        for (Map.Entry<UUID, SRERole> entry : forcedRoles.entrySet()) {
            Player player = serverWorld.getPlayerByUUID(entry.getKey());
            SRERole role = entry.getValue();
            if (!(player instanceof ServerPlayer serverPlayer) || !players.contains(serverPlayer)
                    || role == null || !isRotationEligible(role, serverWorld, companionPlayers.size() + 1)) {
                continue;
            }
            Set<SRERole> group = companionGroup(role);
            boolean hasEgg = group.stream().anyMatch(EggRoleInterface.class::isInstance);
            if ((hasEgg && eggAssigned) || !fitsFactionSeats(group, factionSeats)) {
                continue;
            }
            eggAssigned |= hasEgg;
            reserveFactionSeats(group, factionSeats);
            for (SRERole member : group) {
                ServerPlayer recipient = member == role ? serverPlayer : companionPlayers.removeLast();
                roleAssignments.put(recipient, member);
                forcedPlayers.add(recipient);
            }
        }
        killerCount = factionSeats.get(4);
        vigilanteCount = factionSeats.get(5);
        neutralsCount = factionSeats.get(2);

        // 第二步：依佇列順序從各陣營挑選職業
        Set<String> selectedKillers = new HashSet<>();
        Set<String> selectedVigilantes = new HashSet<>();
        Set<String> selectedNeutrals = new HashSet<>();
        Set<String> selectedCivilians = new HashSet<>();
        for (ServerPlayer forcedPlayer : forcedPlayers) {
            SRERole role = roleAssignments.get(forcedPlayer);
            Set<String> selected = switch (role.getRoleType()) {
                case 4 -> selectedKillers;
                case 5 -> selectedVigilantes;
                case 2, 3 -> selectedNeutrals;
                default -> selectedCivilians;
            };
            selected.add(role.identifier().toString());
        }
        int availableSlots = players.size() - forcedPlayers.size();
        Predicate<SRERole> eligible =
                role -> isRotationEligible(role, serverWorld, availableSlots);
        var killerFilter = eligible.and(SREAllRoleRotationGameMode::isKillerEligible);
        var vigilanteFilter = eligible.and(SREAllRoleRotationGameMode::isVigilanteEligible);
        var neutralFilter = eligible.and(SREAllRoleRotationGameMode::isNeutralEligible);
        var civilianFilter = eligible.and(SREAllRoleRotationGameMode::isCivilianEligible);
        List<SRERole> killers = pickForFaction(filterRoles(killerFilter), killerCount, state, random, selectedKillers);
        List<SRERole> vigilantes = pickForFaction(filterRoles(vigilanteFilter), vigilanteCount, state, random,
                selectedVigilantes);
        List<SRERole> neutrals = pickForFaction(filterRoles(neutralFilter), neutralsCount, state, random, selectedNeutrals);

        // 處理 setOccupiedRoleCount(0) 的職業：不佔用原本名額，額外補充同陣營職業
        long zeroKillers = killers.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        long zeroVigilantes = vigilantes.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        long zeroNeutrals = neutrals.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        if (zeroKillers > 0)
            killers.addAll(pickForFaction(filterRoles(killerFilter), (int) zeroKillers, state, random, selectedKillers));
        if (zeroVigilantes > 0)
            vigilantes.addAll(pickForFaction(filterRoles(vigilanteFilter), (int) zeroVigilantes, state, random,
                    selectedVigilantes));
        if (zeroNeutrals > 0)
            neutrals.addAll(pickForFaction(filterRoles(neutralFilter), (int) zeroNeutrals, state, random, selectedNeutrals));

        int assignedSpecial = killers.size() + vigilantes.size() + neutrals.size();
        int civilianCount = players.size() - assignedSpecial - forcedPlayers.size();

        List<SRERole> civilians = pickForFaction(filterRoles(civilianFilter),
                Math.max(0, civilianCount), state, random, selectedCivilians);

        // 第三步：合併為 RoleInstance，並處理對立 / 伴生職業（沿用 Murder 邏輯）
        List<RoleInstance> instances = new ArrayList<>();
        for (SRERole r : killers)
            instances.add(new RoleInstance(UUID.randomUUID(), r));
        for (SRERole r : vigilantes)
            instances.add(new RoleInstance(UUID.randomUUID(), r));
        for (SRERole r : neutrals)
            instances.add(new RoleInstance(UUID.randomUUID(), r));
        for (SRERole r : civilians)
            instances.add(new RoleInstance(UUID.randomUUID(), r));

        instances = fitCompanionGroups(instances, availableSlots, eligible, state, random, eggAssigned,
                roleAssignments.values(), factionSeats);

        int needCivilian = (players.size() - forcedPlayers.size()) - instances.size();
        for (int i = 0; i < needCivilian; i++)
            instances.add(new RoleInstance(UUID.randomUUID(), TMMRoles.CIVILIAN));

        // 第四步：將展開後的職業分配給未分配的玩家（沿用 Murder 的對應流程）
        return mapInstancesToPlayers(serverWorld, roleAssignments, instances, players);
    }

    /** 把職業實例對應到玩家：強制陣營卡 → 反權重分配 → 平民兜底。 */
    private Map<Player, SRERole> mapInstancesToPlayers(ServerLevel serverWorld,
            Map<Player, SRERole> roleAssignments, List<RoleInstance> instances, List<ServerPlayer> players) {
        // 建立加權表
        LinkedHashMap<RoleInstance, Float> hashMap = new LinkedHashMap<>();
        for (RoleInstance ri : instances) {
            float weight = HarpyModLoaderConfig.HANDLER.instance().roleWeights
                    .getOrDefault(ri.role().getIdentifier(), 1f);
            // Every accepted seat must reach a player, including reserved companion seats.
            hashMap.put(ri, weight > 0 ? weight : 1f);
        }

        List<ServerPlayer> unassignedPlayers = new ArrayList<>();
        for (ServerPlayer player : players)
            if (roleAssignments.get(player) == null)
                unassignedPlayers.add(player);

        FactionCardUtils.assignForcedTeams(unassignedPlayers, ServerPlayer::getUUID,
                PlayerRoleWeightManager.ForcePlayerTeam, hashMap,
                role -> PlayerRoleWeightManager.getRoleType(role.role()),
                slots -> new RoleWeightedUtil(slots).selectRandomKeyBasedOnWeightsAndRemoved(),
                (player, role) -> roleAssignments.put(player, role.role()),
                (player, forced) -> {
                    if (forced.type() == ForceTeamType.CARD)
                        FactionCardUtils.refund(player, forced.roleType());
                });

        // 剩餘職業以反權重方式分配給未分配玩家
        RoleWeightedUtil roleSelector = new RoleWeightedUtil(hashMap);
        Collections.shuffle(unassignedPlayers);
        while (!unassignedPlayers.isEmpty() && roleSelector.size() > 0) {
            RoleInstance ri = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
            if (ri == null)
                break;
            int roleType = PlayerRoleWeightManager.getRoleType(ri.role());
            Player selected = SREMurderGameMode.pickPlayerWithProgressBias(serverWorld, unassignedPlayers, roleType);
            if (selected != null) {
                unassignedPlayers.remove(selected);
                roleAssignments.put(selected, ri.role());
                ProgressionDataManager.onRoleAssigned((ServerPlayer) selected, ri.role());
            }
        }
        for (Player up : unassignedPlayers) {
            roleAssignments.put(up, TMMRoles.CIVILIAN);
            ProgressionDataManager.onRoleAssigned((ServerPlayer) up, TMMRoles.CIVILIAN);
        }
        return roleAssignments;
    }

    /**
     * 依佇列順序從某陣營挑選 need 個職業：出場次數最少優先，次數相同時最久未玩優先。
     * 合格職業用完後，再從佇列前端開始挑選。
     */
    private List<SRERole> pickForFaction(List<SRERole> eligible, int need, AllRoleRotationSavedData state,
            RandomSource random, Set<String> selectedThisRound) {
        List<SRERole> result = new ArrayList<>();
        if (need <= 0 || eligible.isEmpty())
            return result;

        List<SRERole> sequence = new ArrayList<>(eligible);
        Collections.shuffle(sequence, new Random(random.nextLong()));
        sequence.sort(rotationPriority(state));
        while (result.size() < need && !sequence.isEmpty()) {
            List<SRERole> available = sequence.stream()
                    .filter(role -> !selectedThisRound.contains(role.getIdentifier().toString()))
                    .toList();
            if (available.isEmpty()) {
                selectedThisRound.clear();
                available = sequence;
            }
            SRERole selected = available.get(0);
            selectedThisRound.add(selected.getIdentifier().toString());
            result.add(selected);
        }
        // Reset this faction's picker after its pool is exhausted. A role can only
        // repeat after every eligible role in this faction has been selected.
        return result;
    }

    // ===== 各陣營合格職業過濾（與 Murder / LightningDraft 池一致，並排除當前地圖禁用職業）=====

    private static boolean isRotationEligible(SRERole role, ServerLevel level, int availableSlots) {
        if (!role.occupationedRoles.isEmpty()
                || Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1) <= 0
                || (HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights
                        && ModdedWeights.getRoleWeight(role) <= 0
                        && !Harpymodloader.MAP_FORCED_ROLES.contains(role.identifier()))) {
            return false;
        }
        Set<SRERole> group = companionGroup(role);
        return group.size() <= availableSlots
                && group.stream().filter(EggRoleInterface.class::isInstance).count() <= 1
                && group.stream().allMatch(member -> canPlay(member, level)
                        && (!(member instanceof EggRoleInterface)
                                || Harpymodloader.ROLE_MAX.getOrDefault(member.identifier(), 0) > 0));
    }

    private static boolean canPlay(SRERole role, ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        String mapName = Objects.requireNonNullElse(areas.mapName, "unknown");
        return !Harpymodloader.VANNILA_ROLES.contains(role)
                && !role.isOtherModeRole()
                && !role.hasRoundEvent()
                && role != TMMRoles.DISCOVERY_CIVILIAN
                && role != TMMRoles.LOOSE_END
                && !SREDisableManager.isRoleDisabled(role, level)
                && role.canSpawnInMap(mapName, areas.areasSettings)
                && (role.spawnInfo.map.isEmpty() || role.spawnInfo.map.contains(mapName));
    }

    private static Set<SRERole> companionGroup(SRERole role) {
        Set<SRERole> group = new LinkedHashSet<>();
        Deque<SRERole> pending = new ArrayDeque<>();
        pending.add(role);
        while (!pending.isEmpty()) {
            SRERole member = pending.removeFirst();
            if (group.add(member)) {
                for (SRERole companion : RoleAssignmentManager.getCompanionRoles(member)) {
                    if (companion != null) {
                        pending.add(companion);
                    }
                }
            }
        }
        return group;
    }

    private static Comparator<SRERole> rotationPriority(AllRoleRotationSavedData state) {
        return Comparator.comparingInt((SRERole role) -> playedCount(state, role))
                .thenComparingLong(role -> {
                    var track = state.getTrackOrNull(role.identifier().toString());
                    return track == null ? 0L : track.lastPlayedRound;
                });
    }

    private static int playedCount(AllRoleRotationSavedData state, SRERole role) {
        var track = state.getTrackOrNull(role.identifier().toString());
        return track == null ? 0 : track.playedCount;
    }

    public static Set<SRERole> eligibleRotationRoles(ServerLevel level, int slots) {
        Set<SRERole> roles = new LinkedHashSet<>();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (isRotationEligible(role, level, slots)
                    && (isKillerEligible(role) || isNeutralEligible(role)
                            || isVigilanteEligible(role) || isCivilianEligible(role))) {
                roles.addAll(companionGroup(role));
            }
        }
        return roles;
    }

    private List<RoleInstance> fitCompanionGroups(List<RoleInstance> candidates, int slots,
            Predicate<SRERole> eligible, AllRoleRotationSavedData state, RandomSource random, boolean eggAssigned,
            Collection<SRERole> forcedRoles, Map<Integer, Integer> factionSeats) {
        List<RoleInstance> assignments = new ArrayList<>();
        List<RoleInstance> remainingPicks = new ArrayList<>();
        Set<SRERole> activeRoles = new HashSet<>(forcedRoles);
        activeRoles.remove(null);
        Map<RoleInstance, Set<SRERole>> groups = new LinkedHashMap<>();
        Set<String> selected = new HashSet<>();
        for (RoleInstance candidate : candidates) {
            groups.put(candidate, companionGroup(candidate.role()));
            selected.add(candidate.role().identifier().toString());
        }
        RoleInstance eggPick = eggAssigned ? null : candidates.stream()
                .filter(candidate -> groups.get(candidate).stream().anyMatch(EggRoleInterface.class::isInstance))
                .filter(candidate -> groups.get(candidate).size() <= slots
                        && fitsFactionSeats(groups.get(candidate), factionSeats)
                        && isCompatible(groups.get(candidate), activeRoles))
                .min(Comparator.comparing(
                        (RoleInstance candidate) -> groups.get(candidate).stream()
                                .filter(EggRoleInterface.class::isInstance).findFirst().orElseThrow(),
                        rotationPriority(state)))
                .orElse(null);
        // Reserve the egg pick first so another group cannot take its seats.
        if (eggPick != null) {
            reserveFactionSeats(groups.get(eggPick), factionSeats);
            addGroup(assignments, groups.get(eggPick));
            activeRoles.addAll(groups.get(eggPick));
        }
        for (var entry : groups.entrySet()) {
            RoleInstance candidate = entry.getKey();
            if (candidate.equals(eggPick)) {
                continue;
            }
            Set<SRERole> group = entry.getValue();
            if (group.size() > 1 && canFitRegularGroup(group, activeRoles, slots - assignments.size(), factionSeats)) {
                reserveFactionSeats(group, factionSeats);
                addGroup(assignments, group);
                activeRoles.addAll(group);
            } else {
                remainingPicks.add(candidate);
            }
        }

        for (RoleInstance pick : remainingPicks) {
            int seatsLeft = slots - assignments.size();
            if (seatsLeft == 0) {
                break;
            }
            Set<SRERole> group = groups.get(pick);
            if (group.size() != 1 || !canFitRegularGroup(group, activeRoles, seatsLeft, factionSeats)) {
                int faction = rotationFaction(pick.role());
                List<SRERole> replacements = filterRoles(eligible.and(role -> rotationFaction(role) == faction
                        && canFitRegularGroup(companionGroup(role), activeRoles, seatsLeft, factionSeats)));
                List<SRERole> reroll = pickForFaction(replacements, 1, state, random, selected);
                if (reroll.isEmpty()) {
                    continue;
                }
                group = companionGroup(reroll.getFirst());
            }
            reserveFactionSeats(group, factionSeats);
            addGroup(assignments, group);
            activeRoles.addAll(group);
        }
        return assignments;
    }

    private static boolean canFitRegularGroup(Set<SRERole> group, Set<SRERole> activeRoles, int seatsLeft,
            Map<Integer, Integer> factionSeats) {
        return group.size() <= seatsLeft && group.stream().noneMatch(EggRoleInterface.class::isInstance)
                && fitsFactionSeats(group, factionSeats) && isCompatible(group, activeRoles);
    }

    private static boolean fitsFactionSeats(Set<SRERole> group, Map<Integer, Integer> available) {
        Map<Integer, Integer> remaining = new HashMap<>(available);
        reserveFactionSeats(group, remaining);
        return remaining.values().stream().noneMatch(seats -> seats < 0);
    }

    private static void reserveFactionSeats(Set<SRERole> group, Map<Integer, Integer> available) {
        for (SRERole role : group) {
            int faction = rotationFaction(role);
            int occupied = faction == 1 ? Math.max(1, role.getOccupiedRoleCount()) : role.getOccupiedRoleCount();
            if (occupied <= 0) {
                available.merge(1, -1, Integer::sum);
            } else {
                available.merge(faction, -occupied, Integer::sum);
            }
        }
    }

    private static int rotationFaction(SRERole role) {
        int type = role.getRoleType();
        return switch (type) {
            case 2, 3 -> 2;
            case 4, 5 -> type;
            default -> 1;
        };
    }

    private static boolean isCompatible(Set<SRERole> group, Set<SRERole> activeRoles) {
        for (SRERole role : group) {
            if (role.opposingRoles.stream().anyMatch(activeRoles::contains)
                    || activeRoles.stream().anyMatch(active -> active.opposingRoles.contains(role))) {
                return false;
            }
        }
        return true;
    }

    private static void addGroup(List<RoleInstance> assignments, Set<SRERole> group) {
        for (SRERole member : group) {
            assignments.add(new RoleInstance(UUID.randomUUID(), member));
        }
    }

    private static boolean isKillerEligible(SRERole role) {
        return role.canUseKiller()
                && !role.isNeutrals()
                && !role.isNeutralForKiller()
                && !role.isInnocent()
                && role != TMMRoles.CIVILIAN;
    }

    private static boolean isVigilanteEligible(SRERole role) {
        return role.isVigilanteTeam();
    }

    private static boolean isNeutralEligible(SRERole role) {
        return ((!role.canUseKiller() && !role.isInnocent()) || role.isNeutrals())
                && role != TMMRoles.CIVILIAN;
    }

    private static boolean isCivilianEligible(SRERole role) {
        boolean enableCivilianInPool = HarpyModLoaderConfig.HANDLER.instance().enableCivilianInPool;
        return !role.isVigilanteTeam()
                && !role.canUseKiller()
                && !role.isNeutrals()
                && role.isInnocent()
                && (enableCivilianInPool || role != TMMRoles.CIVILIAN);
    }

    private static List<SRERole> filterRoles(Predicate<SRERole> predicate) {
        List<SRERole> out = new ArrayList<>();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (predicate.test(role))
                out.add(role);
        }
        return out;
    }

}
