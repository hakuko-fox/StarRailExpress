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

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.data.AllRoleRotationSavedData;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import net.exmo.sre.repair.role.RepairRole;
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
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

import java.util.*;

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
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
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
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);
        int killerCount = RoleCountManager.getKillerCount(players.size());
        int vigilanteCount = RoleCountManager.getVigilanteCount(players.size());
        int neutralsCount = RoleCountManager.getNeutralCount(players.size());
        for (Map.Entry<UUID, SRERole> entry : forcedRoles.entrySet()) {
            Player player = serverWorld.getPlayerByUUID(entry.getKey());
            SRERole role = entry.getValue();
            if (player != null && role != null) {
                roleAssignments.put(player, role);
                if (role.canUseKiller())
                    killerCount--;
                else if (role.isVigilanteTeam())
                    vigilanteCount--;
                else if (!role.isInnocent())
                    neutralsCount--;
            }
        }
        killerCount = Math.max(0, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        // 第二步：依佇列順序從各陣營挑選職業
        List<SRERole> killers = pickForFaction(eligibleKillers(), killerCount, state, random);
        List<SRERole> vigilantes = pickForFaction(eligibleVigilantes(), vigilanteCount, state, random);
        List<SRERole> neutrals = pickForFaction(eligibleNeutrals(), neutralsCount, state, random);

        // 處理 setOccupiedRoleCount(0) 的職業：不佔用原本名額，額外補充同陣營職業
        long zeroKillers = killers.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        long zeroVigilantes = vigilantes.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        long zeroNeutrals = neutrals.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        if (zeroKillers > 0)
            killers.addAll(pickForFaction(eligibleKillers(), (int) zeroKillers, state, random));
        if (zeroVigilantes > 0)
            vigilantes.addAll(pickForFaction(eligibleVigilantes(), (int) zeroVigilantes, state, random));
        if (zeroNeutrals > 0)
            neutrals.addAll(pickForFaction(eligibleNeutrals(), (int) zeroNeutrals, state, random));

        int assignedSpecial = killers.size() + vigilantes.size() + neutrals.size();
        int civilianCount = players.size() - assignedSpecial - forcedRoles.size();

        List<SRERole> civilians = pickForFaction(eligibleCivilians(),
                Math.max(0, civilianCount), state, random);

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

        instances = RoleAssignmentManager.removeOpposingJobs(instances,
                RoleAssignmentPool.create("Killer", SREAllRoleRotationGameMode::isKillerEligible),
                RoleAssignmentPool.create("Neutrals", SREAllRoleRotationGameMode::isNeutralEligible),
                RoleAssignmentPool.create("Vigilante", SREAllRoleRotationGameMode::isVigilanteEligible),
                RoleAssignmentPool.create("Civilian", SREAllRoleRotationGameMode::isCivilianEligible),
                true, 10);
        instances = RoleAssignmentManager.expandWithCompanionRoles(instances);

        int needCivilian = (players.size() - forcedRoles.size()) - instances.size();
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
            hashMap.put(ri,
                    HarpyModLoaderConfig.HANDLER.instance().roleWeights.getOrDefault(ri.role().getIdentifier(), 1f));
        }

        // 按陣營分組的選擇器，用於 ForcePlayerTeam
        Map<Integer, RoleWeightedUtil> roleSelectors = new HashMap<>();
        Map<Integer, HashMap<RoleInstance, Float>> byType = new HashMap<>();
        for (RoleInstance ri : hashMap.keySet()) {
            int type = PlayerRoleWeightManager.getRoleType(ri.role());
            byType.computeIfAbsent(type, k -> new HashMap<>()).put(ri, hashMap.get(ri));
        }
        for (Map.Entry<Integer, HashMap<RoleInstance, Float>> e : byType.entrySet())
            roleSelectors.putIfAbsent(e.getKey(), new RoleWeightedUtil(e.getValue()));

        List<ServerPlayer> unassignedPlayers = new ArrayList<>();
        for (ServerPlayer player : players)
            if (roleAssignments.get(player) == null)
                unassignedPlayers.add(player);

        // 分配 ForcePlayerTeam（陣營卡）：盡量配發符合陣營的職業，無法配發則退回卡片
        for (Map.Entry<UUID, Integer> entry : PlayerRoleWeightManager.ForcePlayerTeam.entrySet()) {
            UUID uid = entry.getKey();
            ServerPlayer selected = unassignedPlayers.stream().filter(p -> p.getUUID().equals(uid)).findFirst()
                    .orElse(null);
            if (selected == null)
                continue;
            int roleType = entry.getValue();
            RoleWeightedUtil selector = roleSelectors.get(roleType);
            if (selector == null) {
                refundFactionCard(selected, roleType);
                continue;
            }
            RoleInstance ri = selector.selectRandomKeyBasedOnWeightsAndRemoved();
            if (ri != null) {
                hashMap.remove(ri);
                roleAssignments.put(selected, ri.role());
                unassignedPlayers.remove(selected);
            } else {
                refundFactionCard(selected, roleType);
            }
        }

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
     * 依佇列順序從某陣營挑選 need 個職業：從未玩過優先 → 最久未玩優先；
     * 若合格職業少於需求，則從「最久未玩」開始重複挑選。
     */
    private List<SRERole> pickForFaction(List<SRERole> eligible, int need, AllRoleRotationSavedData state,
            RandomSource random) {
        List<SRERole> result = new ArrayList<>();
        if (need <= 0 || eligible.isEmpty())
            return result;

        List<SRERole> unplayed = new ArrayList<>();
        List<SRERole> played = new ArrayList<>();
        for (SRERole r : eligible) {
            AllRoleRotationSavedData.RoleTrack t = state.getTrackOrNull(r.getIdentifier().toString());
            if (t == null || t.playedCount == 0)
                unplayed.add(r);
            else
                played.add(r);
        }
        Collections.shuffle(unplayed, new Random(random.nextLong()));
        // 先洗牌再以 lastPlayedRow 穩定排序，使「同回合內」順序隨機但整體仍由舊到新
        Collections.shuffle(played, new Random(random.nextLong()));
        played.sort(Comparator.comparingLong(r -> {
            AllRoleRotationSavedData.RoleTrack t = state.getTrackOrNull(r.getIdentifier().toString());
            return t == null ? 0L : t.lastPlayedRound;
        }));

        List<SRERole> sequence = new ArrayList<>(unplayed);
        sequence.addAll(played);

        for (SRERole r : sequence) {
            if (result.size() >= need)
                break;
            result.add(r);
        }
        // 不足時以「最久未玩」為基準循環挑選（允許本局重複）
        List<SRERole> lruBase = played.isEmpty() ? eligible : played;
        int i = 0;
        while (result.size() < need) {
            result.add(lruBase.get(i % lruBase.size()));
            i++;
        }
        return result;
    }

    // ===== 各陣營合格職業過濾（與 Murder / LightningDraft 池一致，並排除當前地圖禁用職業）=====

    private static boolean baseEligible(SRERole role) {
        return !Harpymodloader.VANNILA_ROLES.contains(role)
                && !role.isOtherModeRole()
                && !(role instanceof RepairRole)
                && role != TMMRoles.DISCOVERY_CIVILIAN
                && role != TMMRoles.LOOSE_END
                && !SREDisableManager.isRoleDisabled(role);
    }

    private static boolean isKillerEligible(SRERole role) {
        return baseEligible(role)
                && role.canUseKiller()
                && !role.isNeutrals()
                && !role.isNeutralForKiller()
                && !role.isInnocent()
                && role != TMMRoles.CIVILIAN;
    }

    private static boolean isVigilanteEligible(SRERole role) {
        return baseEligible(role) && role.isVigilanteTeam();
    }

    private static boolean isNeutralEligible(SRERole role) {
        return baseEligible(role)
                && ((!role.canUseKiller() && !role.isInnocent()) || role.isNeutrals())
                && role != TMMRoles.CIVILIAN;
    }

    private static boolean isCivilianEligible(SRERole role) {
        boolean enableCivilianInPool = HarpyModLoaderConfig.HANDLER.instance().enableCivilianInPool;
        return baseEligible(role)
                && !role.isVigilanteTeam()
                && !role.canUseKiller()
                && !role.isNeutrals()
                && role.isInnocent()
                && (enableCivilianInPool || role != TMMRoles.CIVILIAN);
    }

    private List<SRERole> eligibleKillers() {
        return filterRoles(SREAllRoleRotationGameMode::isKillerEligible);
    }

    private List<SRERole> eligibleVigilantes() {
        return filterRoles(SREAllRoleRotationGameMode::isVigilanteEligible);
    }

    private List<SRERole> eligibleNeutrals() {
        return filterRoles(SREAllRoleRotationGameMode::isNeutralEligible);
    }

    private List<SRERole> eligibleCivilians() {
        return filterRoles(SREAllRoleRotationGameMode::isCivilianEligible);
    }

    private static List<SRERole> filterRoles(java.util.function.Predicate<SRERole> predicate) {
        List<SRERole> out = new ArrayList<>();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (predicate.test(role))
                out.add(role);
        }
        return out;
    }

    /** 退還一張陣營卡給玩家（當無法配發符合陣營的職業時呼叫）。 */
    private static void refundFactionCard(ServerPlayer player, int roleType) {
        io.wifi.starrailexpress.progression.ProgressionState.FactionCardType cardType =
                io.wifi.starrailexpress.progression.ProgressionState.FactionCardType.fromRoleType(roleType);
        if (cardType != io.wifi.starrailexpress.progression.ProgressionState.FactionCardType.NONE) {
            ProgressionDataManager.addFactionCard(player, cardType, 1);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.sre.role_rotation.card_limit")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }
}
