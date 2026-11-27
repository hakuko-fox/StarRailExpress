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

package io.wifi.starrailexpress.backpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.network.PlayerDataPartSyncPayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import io.wifi.starrailexpress.roster.RoleRosterManager;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.agmas.harpymodloader.modded_murder.ForceTeamInfo;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 场外背包数据管理器（MySQL 分区 {@code data_key="backpack"}）。
 * 结构镜像 {@link ProgressionDataManager}：内存缓存 + 入服异步加载 + 脏标记周期 flush + 断线/关服阻塞 flush。
 * 是阵营卡牌迁移后的唯一来源；{@code ProgressionDataManager.addFactionCard/activateFactionCard} 委托至此。
 */
public final class BackpackManager {
    public static final String PART = "backpack";
    private static final Gson GSON = new GsonBuilder().create();
    private static final long FLUSH_INTERVAL_MS = 5_000L;
    private static final long FLUSH_TIMEOUT_MS = 4_000L;
    private static final long EXTERNAL_REFRESH_INTERVAL_MS = 15_000L;
    private static long nextExternalRefreshAt;
    private static Entry externalRefreshEntry;
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Set<UUID>> ROUND_PARTICIPANTS = new HashMap<>();
    private static final Map<UUID, SRERole> ROLE_CHOICE_RESERVATIONS = new HashMap<>();

    private BackpackManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(BackpackManager::tick);
        OnGameStarted.EVENT.register(world -> {
            if (SREGameWorldComponent.KEY.get(world).getGameMode().autoTriggerGameTrueStarted()) {
                settleAssignedCards(world);
            }
        });
        OnGameTrueStarted.EVENT.register(BackpackManager::settleAssignedCards);
        OnGameEnd.EVENT.register((world, game) -> clearRoleChoiceReservations());
        ServerLifecycleEvents.SERVER_STOPPING.register(BackpackManager::flushAllBlocking);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ENTRIES.clear();
            ROUND_PARTICIPANTS.clear();
            clearRoleChoiceReservations();
            FactionCardForcing.clearAll();
        });
    }

    // ====================== 公开服务端 API ======================

    private static void settleAssignedCards(net.minecraft.server.level.ServerLevel world) {
        var game = SREGameWorldComponent.KEY.get(world);
        Set<UUID> participants = ROUND_PARTICIPANTS.remove(world.dimension().location());
        if (participants == null) return;
        for (ServerPlayer player : world.players()) {
            if (!participants.contains(player.getUUID())) continue;
            Entry entry = ENTRIES.get(player.getUUID());
            if (entry != null && entry.loaded) {
                SRERole actualRole = game.getRole(player);
                var result = CardSettlement.settle(entry.state,
                        actualRole == null ? null : actualRole.identifier().toString(),
                        actualRole == null ? -1 : PlayerRoleWeightManager.getRoleType(actualRole));
                if (result == CardSettlement.Result.NONE) continue;
                var forced = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
                if (forced != null && forced.type() == ForceTeamType.CARD) {
                    PlayerRoleWeightManager.ForcePlayerTeam.remove(player.getUUID());
                }
                PlayerRoleWeightManager.clearCardFactionBoost(player.getUUID());
                markDirty(player, entry);
                sendPendingCard(player);
                Component message = Component.translatable(switch (result) {
                    case SUCCESS -> "message.sre.backpack.assignment_success";
                    case FACTION_REFUNDED -> "message.sre.backpack.faction_mismatch_refund";
                    default -> "message.sre.backpack.role_mismatch_refund";
                });
                player.sendSystemMessage(message);
                player.displayClientMessage(message, true);
            }
        }
    }

    public static Map<FactionCardType, Integer> getCards(ServerPlayer player) {
        return new HashMap<>(getEntry(player.getUUID()).state.cards);
    }

    public static int getCardCount(ServerPlayer player, FactionCardType type) {
        return getEntry(player.getUUID()).state.cards.getOrDefault(type, 0);
    }

    public static int getVtuberCoins(ServerPlayer player) {
        return getEntry(player.getUUID()).state.vtuberCoins;
    }

    public static int getRoleChoiceCards(ServerPlayer player) {
        return getEntry(player.getUUID()).state.roleChoiceCards;
    }

    public static String getPendingRoleId(ServerPlayer player) {
        return getEntry(player.getUUID()).state.pendingRoleId;
    }

    public enum ChoiceResult {
        SUCCESS("message.sre.backpack.choice.success"),
        NOT_LOADED("message.sre.backpack.choice.not_loaded"),
        GAME_RUNNING("message.sre.backpack.choice.game_running"),
        NO_CARD("message.sre.backpack.choice.no_card"),
        INVALID_ROLE("message.sre.backpack.choice.invalid_role"),
        ALREADY_FORCED("message.sre.backpack.choice.already_forced"),
        NO_SELECTION("message.sre.backpack.choice.no_selection");

        private final String messageKey;

        ChoiceResult(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public static boolean ownsStoreSkin(ServerPlayer player, String skinType, String skinId) {
        return getEntry(player.getUUID()).state.purchasedSkins.contains(storeSkinKey(skinType, skinId));
    }

    public static boolean setVtuberCoins(ServerPlayer player, int amount) {
        if (amount < 0 || !isLoaded(player.getUUID())) {
            return false;
        }
        Entry entry = getEntry(player.getUUID());
        entry.state.vtuberCoins = amount;
        markDirty(player, entry);
        return true;
    }

    public static boolean addVtuberCoins(ServerPlayer player, int amount) {
        if (!isLoaded(player.getUUID())) {
            return false;
        }
        Entry entry = getEntry(player.getUUID());
        long next = (long) entry.state.vtuberCoins + amount;
        if (next < 0L || next > Integer.MAX_VALUE) {
            return false;
        }
        entry.state.vtuberCoins = (int) next;
        markDirty(player, entry);
        return true;
    }

    public static boolean addRoleChoiceCards(ServerPlayer player, int amount) {
        if (!isLoaded(player.getUUID())) {
            return false;
        }
        Entry entry = getEntry(player.getUUID());
        long next = (long) entry.state.roleChoiceCards + amount;
        if (next < 0L || next > Integer.MAX_VALUE) {
            return false;
        }
        entry.state.roleChoiceCards = (int) next;
        markDirty(player, entry);
        return true;
    }

    public static boolean tryBuyRoleChoiceCard(ServerPlayer player, int price) {
        if (price < 0 || !isLoaded(player.getUUID())) {
            return false;
        }
        Entry entry = getEntry(player.getUUID());
        if (entry.state.vtuberCoins < price || entry.state.roleChoiceCards == Integer.MAX_VALUE) {
            return false;
        }
        entry.state.vtuberCoins -= price;
        entry.state.roleChoiceCards++;
        markDirty(player, entry);
        return true;
    }

    public static boolean awardVtuberCoins(ServerPlayer player, UUID roundId, int amount) {
        return awardVtuberCoinsInternal(player, roundId, amount, null, false) >= 0;
    }

    /**
     * Awards the round reward and applies the persistent VTuber Coin streak rule.
     * A win streak of three or more adds 25%; a loss streak of three or more adds
     * 10%. Both bonuses are rounded up to the next integer.
     *
     * @return the actual awarded amount, or {@code -1} when the award was rejected
     */
    public static int awardVtuberCoinsForRound(ServerPlayer player, UUID roundId, int baseAmount,
            boolean winner) {
        return awardVtuberCoinsInternal(player, roundId, baseAmount, winner, false);
    }

    public static int awardQueuedVtuberCoins(ServerPlayer player, UUID roundId, int baseAmount, boolean winner) {
        return awardVtuberCoinsInternal(player, roundId, baseAmount, winner, true);
    }

    public static boolean isQueuedVtuberRewardSaved(UUID playerId, UUID roundId) {
        Entry entry = ENTRIES.get(playerId);
        return entry != null && entry.loaded && !entry.dirty && !entry.saveInFlight && !entry.loadInFlight
                && entry.state.appliedQueuedVtuberRewards.contains(roundId.toString());
    }

    private static int awardVtuberCoinsInternal(ServerPlayer player, UUID roundId, int baseAmount,
            Boolean winner, boolean queued) {
        if (roundId == null || baseAmount < 0 || !isLoaded(player.getUUID())) {
            return -1;
        }
        Entry entry = getEntry(player.getUUID());
        String id = roundId.toString();
        if (id.equals(entry.state.lastVtuberCoinRoundId) || entry.state.appliedQueuedVtuberRewards.contains(id)) {
            return -1;
        }

        int amount = baseAmount;
        int nextWinStreak = entry.state.vtuberWinStreak;
        int nextLossStreak = entry.state.vtuberLossStreak;
        if (winner != null) {
            if (winner) {
                nextWinStreak = incrementStreak(nextWinStreak);
                nextLossStreak = 0;
                if (nextWinStreak >= 3) {
                    amount = roundUpPercent(baseAmount, 25);
                }
            } else {
                nextWinStreak = 0;
                nextLossStreak = incrementStreak(nextLossStreak);
                if (nextLossStreak >= 3) {
                    amount = roundUpPercent(baseAmount, 10);
                }
            }
        }
        if (amount < 0) {
            return -1;
        }
        long next = (long) entry.state.vtuberCoins + amount;
        if (next > Integer.MAX_VALUE) {
            return -1;
        }
        entry.state.vtuberCoins = (int) next;
        if (winner != null) {
            entry.state.vtuberWinStreak = nextWinStreak;
            entry.state.vtuberLossStreak = nextLossStreak;
        }
        entry.state.lastVtuberCoinRoundId = id;
        if (queued) {
            entry.state.appliedQueuedVtuberRewards.add(id);
        }
        markDirty(player, entry);
        return amount;
    }

    private static int incrementStreak(int streak) {
        return streak == Integer.MAX_VALUE ? streak : streak + 1;
    }

    private static int roundUpPercent(int amount, int bonusPercent) {
        long numerator = (long) amount * (100L + bonusPercent);
        long rounded = (numerator + 99L) / 100L;
        return rounded > Integer.MAX_VALUE ? -1 : (int) rounded;
    }

    public static boolean tryBuyStoreSkin(ServerPlayer player, String skinType, String skinId, int price) {
        if (price < 0 || !isLoaded(player.getUUID())) {
            return false;
        }
        Entry entry = getEntry(player.getUUID());
        String key = storeSkinKey(skinType, skinId);
        if (entry.state.purchasedSkins.contains(key) || entry.state.vtuberCoins < price) {
            return false;
        }
        entry.state.vtuberCoins -= price;
        entry.state.purchasedSkins.add(key);
        markDirty(player, entry);
        return true;
    }

    public static boolean tryBuyFactionCard(ServerPlayer player, FactionCardType type, int price) {
        if (type == FactionCardType.NONE || price < 0 || !isLoaded(player.getUUID())) {
            return false;
        }
        Entry entry = getEntry(player.getUUID());
        if (entry.state.vtuberCoins < price) {
            return false;
        }
        int current = entry.state.cards.getOrDefault(type, 0);
        if (current == Integer.MAX_VALUE) {
            return false;
        }
        entry.state.vtuberCoins -= price;
        entry.state.cards.put(type, current + 1);
        markDirty(player, entry);
        return true;
    }

    public static String storeSkinKey(String skinType, String skinId) {
        return "skin:" + skinType + ":" + skinId;
    }

    public static void addCard(ServerPlayer player, FactionCardType type, int count) {
        if (type == FactionCardType.NONE || count == 0) {
            return;
        }
        Entry entry = getEntry(player.getUUID());
        int current = entry.state.cards.getOrDefault(type, 0);
        entry.state.cards.put(type, Math.max(0, current + count));
        markDirty(player, entry);
    }

    public enum ActivateResult {
        SUCCESS,
        INVALID,
        NOT_LOADED,
        GAME_RUNNING,
        NO_CARD,
        ALREADY_ACTIVE,
        ON_COOLDOWN,
        /** 自定义卡必须带上目标阵营（命令里漏参数或传了非法阵营）。 */
        NEEDS_TARGET
    }

    /** 固定目标阵营卡：目标就是卡自己的阵营。 */
    public static boolean activateCard(ServerPlayer player, FactionCardType type) {
        return activateCardResult(player, type, type) == ActivateResult.SUCCESS;
    }

    public static ActivateResult activateCardResult(ServerPlayer player, FactionCardType type) {
        return activateCardResult(player, type, type);
    }

    public static ActivateResult activateCardResult(ServerPlayer player, FactionCardType consumed,
            FactionCardType target) {
        if (consumed == null || consumed == FactionCardType.NONE) return ActivateResult.INVALID;
        if (target == null || !target.isSelectableTarget()
                || (consumed != FactionCardType.CUSTOM && consumed != target)) {
            return ActivateResult.NEEDS_TARGET;
        }
        if (!isLoaded(player.getUUID())) return ActivateResult.NOT_LOADED;
        if (SREGameWorldComponent.KEY.get(player.serverLevel()).isRunning()) return ActivateResult.GAME_RUNNING;
        Entry entry = getEntry(player.getUUID());
        int current = entry.state.cards.getOrDefault(consumed, 0);
        if (current < 1) {
            return ActivateResult.NO_CARD;
        }
        if (entry.state.pendingFactionCard != FactionCardType.NONE
                || PlayerRoleWeightManager.ForcePlayerTeam.containsKey(player.getUUID())) {
            return ActivateResult.ALREADY_ACTIVE;
        }
        if (FactionCardCooldown.isOnCooldown(entry.state, consumed)) {
            return ActivateResult.ON_COOLDOWN;
        }
        if (!entry.state.pendingRoleId.isBlank()) {
            entry.state.pendingRoleId = "";
            entry.state.roleChoiceCards = (int) Math.min(Integer.MAX_VALUE, (long) entry.state.roleChoiceCards + 1L);
        }
        int roleType = target.getTypeRoleId();
        entry.state.reserveFactionCard(consumed, roleType, System.currentTimeMillis());
        PlayerRoleWeightManager.ForcePlayerTeam.put(player.getUUID(), new ForceTeamInfo(roleType, ForceTeamType.CARD));
        PlayerRoleWeightManager.setCardFactionBoost(player.getUUID(), roleType,
                FactionCardForcing.PICK_BOOST_MULTIPLIER);
        markDirty(player, entry);
        sendPendingCard(player);
        Component message = consumed == FactionCardType.CUSTOM
                ? Component.translatable("message.sre.progression.custom_card_activated",
                        Component.translatable(target.displayName))
                : Component.translatable("message.sre.progression.faction_card_activated",
                        Component.translatable(consumed.displayName));
        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);
        return ActivateResult.SUCCESS;
    }

    public static long remainingCooldownMs(ServerPlayer player, FactionCardType type) {
        return FactionCardCooldown.remainingMs(getEntry(player.getUUID()).state, type);
    }

    /** Refund only the outstanding reservation, including its previous cooldown. */
    public static boolean refundFactionCard(ServerPlayer player, FactionCardType type) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry == null || !entry.loaded) {
            return false;
        }
        if (entry.state.pendingFactionCard == FactionCardType.CUSTOM
                && type == FactionCardType.fromRoleType(entry.state.pendingFactionCardRoleType())) {
            type = FactionCardType.CUSTOM;
        }
        if (!entry.state.refundFactionCard(type)) {
            return false;
        }
        var forced = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
        if (forced != null && forced.type() == ForceTeamType.CARD) {
            PlayerRoleWeightManager.ForcePlayerTeam.remove(player.getUUID());
        }
        PlayerRoleWeightManager.clearCardFactionBoost(player.getUUID());
        markDirty(player, entry);
        sendPendingCard(player);
        return true;
    }

    public static ChoiceResult selectRole(ServerPlayer player, String rawRoleId) {
        if (!isLoaded(player.getUUID())) {
            return ChoiceResult.NOT_LOADED;
        }
        if (SREGameWorldComponent.KEY.get(player.serverLevel()).isRunning()) {
            return ChoiceResult.GAME_RUNNING;
        }
        ResourceLocation roleId = ResourceLocation.tryParse(rawRoleId);
        SRERole role = roleId == null ? null : TMMRoles.getRole(roleId);
        if (!isSelectableRole(role)) {
            return ChoiceResult.INVALID_ROLE;
        }

        Entry entry = getEntry(player.getUUID());
        if (PlayerRoleWeightManager.ForcePlayerTeam.containsKey(player.getUUID())
                && entry.state.pendingFactionCard == FactionCardType.NONE) {
            return ChoiceResult.ALREADY_FORCED;
        }
        if (entry.state.pendingRoleId.isBlank()) {
            if (entry.state.roleChoiceCards < 1) {
                return ChoiceResult.NO_CARD;
            }
            entry.state.roleChoiceCards--;
        }
        if (entry.state.pendingFactionCard != FactionCardType.NONE) {
            refundFactionCard(player, entry.state.pendingFactionCard);
        }
        entry.state.pendingRoleId = role.identifier().toString();
        markDirty(player, entry);
        return ChoiceResult.SUCCESS;
    }

    public static ChoiceResult cancelSelection(ServerPlayer player) {
        if (!isLoaded(player.getUUID())) {
            return ChoiceResult.NOT_LOADED;
        }
        if (SREGameWorldComponent.KEY.get(player.serverLevel()).isRunning()) {
            return ChoiceResult.GAME_RUNNING;
        }
        Entry entry = getEntry(player.getUUID());
        boolean changed = false;
        if (!entry.state.pendingRoleId.isBlank()) {
            entry.state.pendingRoleId = "";
            entry.state.roleChoiceCards = (int) Math.min(Integer.MAX_VALUE, (long) entry.state.roleChoiceCards + 1L);
            changed = true;
        }
        if (entry.state.pendingFactionCard != FactionCardType.NONE) {
            changed |= refundFactionCard(player, entry.state.pendingFactionCard);
        }
        if (!changed) {
            return ChoiceResult.NO_SELECTION;
        }
        markDirty(player, entry);
        return ChoiceResult.SUCCESS;
    }

    /** Resolve persistent role choices after the map and mode are finalized. */
    public static void prepareRoleChoices(net.minecraft.server.level.ServerLevel world,
            List<ServerPlayer> players, GameMode mode) {
        var modeId = mode.identifier;
        boolean draftKillerSlots = modeId.equals(SREGameModes.VOLUNTEER_MODE_ID)
                || modeId.equals(SREGameModes.VOLUNTEER_OPEN_SELECT_MODE_ID)
                || modeId.equals(SREGameModes.ROLE_ROTATION_MODE_ID)
                || modeId.equals(SREGameModes.ROLE_ROTATION_HIDE_MODE_ID)
                || modeId.equals(SREGameModes.ROLE_ROTATION_SINGLE_SELECT_MODE_ID)
                || modeId.equals(SREGameModes.ROLE_ROTATION_SINGLE_SELECT_HIDE_MODE_ID);
        boolean hideAndSeek = modeId.equals(SREGameModes.HIDE_AND_SEEK_MODE_ID);
        boolean puppetRestricted = modeId.equals(SREGameModes.CLASS_CHANGE_MODE_ID)
                || modeId.equals(SREGameModes.VOLUNTEER_MODE_ID)
                || modeId.equals(SREGameModes.ROLE_ROTATION_SINGLE_SELECT_MODE_ID)
                || modeId.equals(SREGameModes.ROLE_ROTATION_SINGLE_SELECT_HIDE_MODE_ID);
        boolean supportedMode = draftKillerSlots || hideAndSeek
                || modeId.equals(SREGameModes.MURDER_ID)
                || modeId.equals(SREGameModes.VTUBER_MODE_ID)
                || modeId.equals(SREGameModes.HK_VTUBER_MODE_ID)
                || modeId.equals(SREGameModes.LOVERS_MODE_ID)
                || modeId.equals(SREGameModes.REFUGEE_MODE_ID)
                || modeId.equals(SREGameModes.REFUGEE_LOVER_MODE_ID)
                || modeId.equals(SREGameModes.CLASS_CHANGE_MODE_ID)
                || modeId.equals(SREGameModes.CUSTOM_SELECTED_MODE_ID)
                || modeId.equals(SREGameModes.ALL_ROLE_ROTATION_MODE_ID);
        String requiredFlag = modeId.equals(SREGameModes.HK_VTUBER_MODE_ID) ? "hkvtuber"
                : modeId.equals(SREGameModes.VTUBER_MODE_ID) ? "vtuber" : null;
        ROUND_PARTICIPANTS.put(world.dimension().location(), players.stream()
                .map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet()));
        for (ServerPlayer player : players) {
            Entry entry = ENTRIES.get(player.getUUID());
            if (entry == null || !entry.loaded || entry.state.pendingFactionCard == FactionCardType.NONE
                    || !entry.state.pendingRoleId.isBlank()) {
                continue;
            }
            var forced = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
            if (Harpymodloader.FORCED_MODDED_ROLE.containsKey(player.getUUID())
                    || (forced != null && forced.type() != ForceTeamType.CARD)) {
                refundFactionCard(player, entry.state.pendingFactionCard);
                continue;
            }
            int targetRoleType = entry.state.pendingFactionCardRoleType();
            PlayerRoleWeightManager.ForcePlayerTeam.put(player.getUUID(),
                    new ForceTeamInfo(targetRoleType, ForceTeamType.CARD));
            PlayerRoleWeightManager.setCardFactionBoost(player.getUUID(), targetRoleType,
                    FactionCardForcing.PICK_BOOST_MULTIPLIER);
        }
        if (!supportedMode) {
            for (ServerPlayer player : players) {
                refundPendingRole(player, "message.sre.backpack.choice.mode_refund");
            }
            return;
        }

        AreasWorldComponent areas = AreasWorldComponent.KEY.get(world);
        List<BackpackRoleChoiceResolver.Request> applicants = new ArrayList<>();
        Map<String, Integer> reserved = new HashMap<>();
        Map<Integer, Integer> reservedFactions = new HashMap<>();
        for (ServerPlayer player : players) {
            Entry entry = ENTRIES.get(player.getUUID());
            if (entry == null || !entry.loaded || entry.state.pendingRoleId.isBlank()) {
                continue;
            }
            if (Harpymodloader.FORCED_MODDED_ROLE.containsKey(player.getUUID())) {
                refundPendingRole(player, "message.sre.backpack.choice.admin_refund");
                continue;
            }
            ResourceLocation roleId = ResourceLocation.tryParse(entry.state.pendingRoleId);
            SRERole role = roleId == null ? null : TMMRoles.getRole(roleId);
            if (!isSelectableRole(role)) {
                refundPendingRole(player, "message.sre.backpack.choice.unavailable_refund");
                continue;
            }
            int roleType = role.getRoleType();
            if ((requiredFlag != null && !role.isFlag(requiredFlag))
                    || (hideAndSeek && roleType != 1 && roleType != 5)
                    || (puppetRestricted && role == org.agmas.noellesroles.role.ModRoles.PUPPETEER)) {
                refundPendingRole(player, "message.sre.backpack.choice.mode_refund");
                continue;
            }
            if (areas == null || !isRoleAllowedOnMap(role, areas.mapName, areas.areasSettings)) {
                refundPendingRole(player, "message.sre.backpack.choice.map_refund");
                continue;
            }
            int minimumPlayers = role.spawnInfo.minEnabledPlayer;
            if (minimumPlayers > players.size()) {
                Component message = Component.translatable("message.sre.backpack.choice.minimum_players_refund",
                        minimumPlayers, players.size());
                refundPendingRole(player, message);
                continue;
            }
            int maximumPlayers = role.spawnInfo.maxEnabledPlayer;
            if (maximumPlayers >= 0 && players.size() > maximumPlayers) {
                refundPendingRole(player, "message.sre.backpack.choice.unavailable_refund");
                continue;
            }
            applicants.add(new BackpackRoleChoiceResolver.Request(player.getUUID(), role.identifier().toString(),
                    roleType, Math.max(1, role.getOccupiedRoleCount())));
        }
        for (ServerPlayer player : players) {
            SRERole role = Harpymodloader.FORCED_MODDED_ROLE.get(player.getUUID());
            if (role != null) {
                reserved.merge(role.identifier().toString(), 1, Integer::sum);
                reservedFactions.merge(BackpackRoleChoiceResolver.faction(role.getRoleType()),
                        Math.max(1, role.getOccupiedRoleCount()), Integer::sum);
            }
        }

        Map<String, Integer> capacities = new HashMap<>();
        for (BackpackRoleChoiceResolver.Request request : applicants) {
            ResourceLocation roleId = ResourceLocation.tryParse(request.roleId());
            // An enabled role can be reserved even when its random spawn chance is zero.
            capacities.put(request.roleId(), Math.max(1, Harpymodloader.ROLE_MAX.getOrDefault(roleId, 1)));
        }
        int killers = draftKillerSlots ? RoleCountManager.getDraftKillerCount(players.size())
                : RoleCountManager.getKillerCount(players.size());
        int vigilantes = RoleCountManager.getVigilanteCount(players.size());
        int neutrals = hideAndSeek ? 0 : RoleCountManager.getNeutralCount(players.size());
        Map<Integer, Integer> factionCapacities = Map.of(4, killers, 5, vigilantes, 2, neutrals,
                1, Math.max(0, players.size() - killers - vigilantes - neutrals));
        BackpackRoleChoiceResolver.Resolution resolution = BackpackRoleChoiceResolver.resolve(
                applicants, capacities, reserved, factionCapacities, reservedFactions, new Random(world.random.nextLong()));
        for (Map.Entry<UUID, String> winner : resolution.winners().entrySet()) {
            ResourceLocation roleId = ResourceLocation.tryParse(winner.getValue());
            SRERole role = roleId == null ? null : TMMRoles.getRole(roleId);
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(winner.getKey());
            if (player != null && role != null) {
                Harpymodloader.FORCED_MODDED_ROLE.put(player.getUUID(), role);
                ROLE_CHOICE_RESERVATIONS.put(player.getUUID(), role);
            }
        }
        for (UUID loser : resolution.losers()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(loser);
            if (player != null) {
                refundPendingRole(player, "message.sre.backpack.choice.conflict_refund");
            }
        }
    }

    public static SRERole reservedRoleChoice(UUID playerId) {
        return ROLE_CHOICE_RESERVATIONS.get(playerId);
    }

    public static void cancelRoundRoleChoices(net.minecraft.server.level.ServerLevel world) {
        ROUND_PARTICIPANTS.remove(world.dimension().location());
        clearRoleChoiceReservations();
    }

    private static void clearRoleChoiceReservations() {
        ROLE_CHOICE_RESERVATIONS.forEach(Harpymodloader.FORCED_MODDED_ROLE::remove);
        ROLE_CHOICE_RESERVATIONS.clear();
    }

    private static boolean isSelectableRole(SRERole role) {
        if (role == null || role == TMMRoles.DISCOVERY_CIVILIAN || role == TMMRoles.LOOSE_END
                || role.isOtherModeRole() || role.getOccupiedRoleCount() <= 0
                || !role.occupationedRoles.isEmpty() || !role.occupationRoles.isEmpty()
                || SREDisableManager.isRoleDisabled(role) || !RoleRosterManager.isRoleEnabled(role)) {
            return false;
        }
        return true;
    }

    private static boolean isRoleAllowedOnMap(SRERole role, String mapName, AreasSettings settings) {
        if (mapName == null || settings == null) {
            return false;
        }
        if (containsConfiguredRole(settings.disabledRoles, role)) {
            return false;
        }
        // 地图显式启用或强制的职业不受普通地图生成限制。
        if (containsConfiguredRole(settings.enabledRoles, role)
                || containsConfiguredRole(settings.forcedRoles, role)) {
            return true;
        }
        return role.canSpawnInMap(mapName, settings)
                && (role.spawnInfo.map.isEmpty() || role.spawnInfo.map.contains(mapName));
    }

    private static boolean containsConfiguredRole(Set<String> configuredRoles, SRERole role) {
        if (configuredRoles == null) {
            return false;
        }
        String fullId = role.identifier().toString();
        String path = role.identifier().getPath();
        return configuredRoles.stream().anyMatch(id -> id != null
                && (fullId.equals(id.trim()) || path.equals(id.trim())));
    }

    private static void refundPendingRole(ServerPlayer player, String messageKey) {
        refundPendingRole(player, Component.translatable(messageKey));
    }

    private static void refundPendingRole(ServerPlayer player, Component message) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry == null || !entry.loaded || entry.state.pendingRoleId.isBlank()) {
            return;
        }
        entry.state.pendingRoleId = "";
        entry.state.roleChoiceCards = (int) Math.min(Integer.MAX_VALUE, (long) entry.state.roleChoiceCards + 1L);
        markDirty(player, entry);
        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);
    }

    // ====================== 待生效卡（客户端镜像） ======================

    /** 背包分区之外的第二个分区：本局待兑现的阵营卡，只给界面显示「本局已生效」用。 */
    public static final String CARD_PART = "card_force";

    private static final AtomicLong CARD_PART_STAMP = new AtomicLong();

    /** 客户端缓存按 updatedAt 丢弃旧包，所以这里保证时间戳单调递增。 */
    private static long nextCardPartStamp() {
        return CARD_PART_STAMP.updateAndGet(previous -> Math.max(previous + 1L, System.currentTimeMillis()));
    }

    /** 把「本局待兑现的卡」推给客户端；没有待生效卡时推空状态。 */
    static void sendPendingCard(ServerPlayer player) {
        BackpackState backpack = getEntry(player.getUUID()).state;
        FactionCardType target = FactionCardType.fromRoleType(backpack.pendingFactionCardRoleType());
        long stamp = nextCardPartStamp();
        PendingCardState state = target == FactionCardType.NONE
                ? PendingCardState.none(stamp)
                : PendingCardState.of(backpack.pendingFactionCard, target, stamp);
        ServerPlayNetworking.send(player,
                new PlayerDataPartSyncPayload(player.getUUID(), CARD_PART, GSON.toJson(state), stamp));
    }

    /** 命令开屏前可调用以保证客户端数据新鲜。 */
    public static void resend(ServerPlayer player) {
        send(player, getEntry(player.getUUID()));
    }

    public static boolean isLoaded(UUID playerUuid) {
        Entry entry = ENTRIES.get(playerUuid);
        return entry != null && entry.loaded;
    }

    public static boolean flushBlocking(UUID playerUuid) {
        Entry entry = ENTRIES.get(playerUuid);
        if (entry == null || !entry.loaded || entry.loadInFlight || entry.saveInFlight || !isDatabaseEnabled()) {
            return false;
        }
        boolean success = MysqlPlayerDataStore.saveBatchBlocking(
                playerUuid,
                Map.of(PART, toJson(entry.state, entry.updatedAt)),
                Math.max(1L, entry.updatedAt),
                FLUSH_TIMEOUT_MS);
        if (success) {
            entry.dirty = false;
        }
        return success;
    }

    // ====================== 迁移（移动，一次性） ======================

    /**
     * 把通行证的 {@code factionCards} 计数搬入背包并清零通行证侧。须在背包与通行证两侧 DB 记录都加载完成后调用，
     * 在 {@link ProgressionDataManager#reloadFromDatabase} 与本类 {@link #reloadFromDatabase} 完成时各触发一次。
     * 严格顺序：先落背包并置 migrated，再清/落通行证 —— 任一步失败都不会丢卡或重复计数。
     */
    public static void migrateIfNeeded(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Entry entry = ENTRIES.get(uuid);
        if (entry == null || !entry.loaded || !ProgressionDataManager.isLoaded(uuid)) {
            return; // 等两侧都加载完，由后完成者再触发
        }
        BackpackState bp = entry.state;
        ProgressionState pg = ProgressionDataManager.get(player);
        entry.lastMigrationAttemptAt = System.currentTimeMillis();

        if (bp.migrated && pg.factionCards.entrySet().stream()
                .noneMatch(card -> card.getKey() != FactionCardType.NONE && card.getValue() > 0)) {
            return;
        }
        if (!BackpackCardMigration.migrate(bp, pg.factionCards, () -> {
            markDirty(player, entry);
            return !SREConfig.instance().mysqlPlayerSyncEnabled || flushBlocking(uuid);
        })) {
            return;
        }
        ProgressionDataManager.markFactionCardsCleared(player);
        ProgressionDataManager.flushBlocking(uuid);
        SRE.LOGGER.info("Migrated faction cards from progression to backpack for {}", uuid);
    }

    // ====================== 生命周期 ======================

    private static void onJoin(ServerPlayer player) {
        Entry entry = getEntry(player.getUUID());
        entry.online = true;
        send(player, entry);
        sendPendingCard(player);
        if (!isDatabaseEnabled()) {
            loadLocal(player, entry);
            entry.loaded = true;
            entry.dirty = true;
            send(player, entry);
            migrateIfNeeded(player);
            return;
        }
        if (entry.loaded && entry.dirty) {
            return;
        }
        reloadFromDatabase(player, entry);
    }

    private static void reloadFromDatabase(ServerPlayer player, Entry entry) {
        reloadFromDatabase(player, entry, false);
    }

    private static void reloadFromDatabase(ServerPlayer player, Entry entry, boolean externalRefresh) {
        if (!isDatabaseEnabled() || entry.loadInFlight || entry.saveInFlight) {
            return;
        }
        entry.loadInFlight = true;
        entry.lastLoadAttemptAt = System.currentTimeMillis();
        Map<FactionCardType, Integer> beforeRead = new HashMap<>(entry.state.cards);
        MysqlPlayerDataStore.loadBatchAsync(player.getUUID(), List.of(PART))
                .whenComplete((records, throwable) -> {
                    MinecraftServer server = player.getServer();
                    if (server == null) {
                        entry.loadInFlight = false;
                        return;
                    }
                    server.execute(() -> {
                        // Keep saves excluded until the payload AND its baseline are installed.
                        entry.loadInFlight = false;
                        if (ENTRIES.get(player.getUUID()) != entry) {
                            return;
                        }
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to load backpack part for {}", player.getUUID(), throwable);
                            return;
                        }
                        MysqlPlayerDataStore.SyncRecord record = records.get(PART);
                        if (record != null && record.payload() != null && !record.payload().isBlank()) {
                            BackpackState loaded = fromJson(record.payload());
                            if (externalRefresh) {
                                // Only the changes made while this clean-cache read was in flight
                                // are local deltas. Never re-add the old cached balance.
                                mergeExternalRefresh(entry.state, loaded, beforeRead);
                            } else {
                                entry.dirty = false;
                            }
                            entry.state = loaded;
                            entry.updatedAt = Math.max(entry.updatedAt, record.updatedAt());
                        }
                        entry.loaded = true;
                        if (!entry.online) {
                            // Disconnect during a refresh must not drop its in-flight local deltas.
                            if (entry.dirty) flushAsync(player, entry);
                            else ENTRIES.remove(player.getUUID(), entry);
                            return;
                        }
                        send(player, entry);
                        migrateIfNeeded(player);
                    });
                });
    }

    static void mergeExternalRefresh(BackpackState local, BackpackState loaded,
            Map<FactionCardType, Integer> beforeRead) {
        for (FactionCardType type : FactionCardType.values()) {
            if (type == FactionCardType.NONE) continue;
            int delta = local.cards.getOrDefault(type, 0) - beforeRead.getOrDefault(type, 0);
            loaded.cards.put(type, Math.max(0, loaded.cards.getOrDefault(type, 0) + delta));
            long used = local.lastUsedAt(type);
            if (used > loaded.lastUsedAt(type)) loaded.markUsed(type, used);
        }
        loaded.migrated |= local.migrated;
    }

    private static void onDisconnect(ServerPlayer player) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry != null) {
            entry.online = false;
            if (!isDatabaseEnabled()) {
                saveLocal(player, entry);
                ENTRIES.remove(player.getUUID(), entry);
                return;
            }
            // An in-flight read first merges local deltas, then flushes them.
            // An in-flight save's completion also retains any newer dirty state.
            if (!entry.loadInFlight && !entry.saveInFlight) {
                if (entry.dirty) flushAsync(player, entry);
                else ENTRIES.remove(player.getUUID(), entry);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        ServerPlayer refreshPlayer = null;
        Entry refreshCandidate = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Entry entry = ENTRIES.get(player.getUUID());
            if (entry == null || !entry.online || entry.saveInFlight || entry.loadInFlight) continue;
            if (!entry.loaded && now - entry.lastLoadAttemptAt >= FLUSH_INTERVAL_MS) {
                reloadFromDatabase(player, entry);
                continue;
            }
            if (entry.loaded && now - entry.lastMigrationAttemptAt >= FLUSH_INTERVAL_MS) {
                migrateIfNeeded(player);
            }
            if (entry.dirty) {
                if (now - entry.lastFlushAt >= FLUSH_INTERVAL_MS) flushAsync(player, entry);
            } else if (entry.loaded && isDatabaseEnabled()
                    && now >= nextExternalRefreshAt
                    && now - entry.lastExternalRefreshAt >= EXTERNAL_REFRESH_INTERVAL_MS
                    && (externalRefreshEntry == null || !externalRefreshEntry.loadInFlight)) {
                // Oldest first: a fixed player-list order must not starve larger servers.
                if (refreshCandidate == null || entry.lastExternalRefreshAt < refreshCandidate.lastExternalRefreshAt) {
                    refreshPlayer = player;
                    refreshCandidate = entry;
                }
            }
        }
        if (refreshCandidate != null) {
            // Single server-wide lane: <=1 SELECT/second, <=1 in flight.
            nextExternalRefreshAt = now + 1_000L;
            refreshCandidate.lastExternalRefreshAt = now;
            externalRefreshEntry = refreshCandidate;
            reloadFromDatabase(refreshPlayer, refreshCandidate, true);
        }
    }

    private static void flushAsync(ServerPlayer player, Entry entry) {
        if (!isDatabaseEnabled() || !entry.loaded || entry.loadInFlight || entry.saveInFlight) {
            return;
        }
        entry.saveInFlight = true;
        entry.dirty = false;
        entry.lastFlushAt = System.currentTimeMillis();
        long updatedAt = Math.max(1L, entry.updatedAt);
        MysqlPlayerDataStore.saveBatchAsync(player.getUUID(), Map.of(PART, toJson(entry.state, updatedAt)), updatedAt)
                .whenComplete((success, throwable) -> {
                    MinecraftServer server = player.getServer();
                    if (server == null) { entry.saveInFlight = false; return; }
                    server.execute(() -> {
                        entry.saveInFlight = false;
                        if (ENTRIES.get(player.getUUID()) != entry) return;
                        if (throwable != null || !Boolean.TRUE.equals(success)) {
                            entry.dirty = true;
                            if (throwable != null) {
                                SRE.LOGGER.warn("Failed to save backpack part for {}", player.getUUID(), throwable);
                            } else {
                                reloadFromDatabase(player, entry);
                            }
                        } else if (!entry.online) {
                            if (entry.dirty) flushAsync(player, entry);
                            else ENTRIES.remove(player.getUUID(), entry);
                        }
                    });
                });
    }

    private static void flushAllBlocking(MinecraftServer server) {
        for (UUID playerUuid : List.copyOf(ENTRIES.keySet())) {
            flushBlocking(playerUuid);
        }
    }

    private static Entry getEntry(UUID uuid) {
        return ENTRIES.computeIfAbsent(uuid, ignored -> new Entry());
    }

    private static void markDirty(ServerPlayer player, Entry entry) {
        entry.updatedAt = Math.max(System.currentTimeMillis(), entry.updatedAt + 1L);
        entry.state.version = entry.updatedAt;
        entry.dirty = true;
        saveLocal(player, entry);
        send(player, entry);
    }

    private static void loadLocal(ServerPlayer player, Entry entry) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String json = BackpackSavedData.get(server).get(player.getUUID());
        if (json == null || json.isBlank()) {
            return;
        }
        entry.state = fromJson(json);
        entry.updatedAt = Math.max(entry.updatedAt, entry.state.version);
    }

    private static void saveLocal(ServerPlayer player, Entry entry) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            BackpackSavedData.get(server).put(player.getUUID(), toJson(entry.state, entry.updatedAt));
        }
    }

    private static void send(ServerPlayer player, Entry entry) {
        ServerPlayNetworking.send(player,
                new PlayerDataPartSyncPayload(player.getUUID(), PART, toJson(entry.state, entry.updatedAt),
                        entry.updatedAt));
    }

    private static boolean isDatabaseEnabled() {
        return SREConfig.instance().mysqlPlayerSyncEnabled && MysqlPlayerDataStore.isAvailable();
    }

    private static BackpackState fromJson(String json) {
        try {
            BackpackState state = GSON.fromJson(json, BackpackState.class);
            return state == null ? BackpackState.createDefault() : state.normalized();
        } catch (RuntimeException exception) {
            return BackpackState.createDefault();
        }
    }

    private static String toJson(BackpackState state, long updatedAt) {
        state.version = updatedAt;
        return GSON.toJson(state.normalized());
    }

    private static final class Entry {
        private BackpackState state = BackpackState.createDefault();
        private volatile boolean online;
        private volatile boolean dirty;
        private volatile boolean loaded;
        private volatile boolean loadInFlight;
        private volatile boolean saveInFlight;
        private volatile long updatedAt;
        private volatile long lastFlushAt;
        private volatile long lastLoadAttemptAt;
        private long lastMigrationAttemptAt;
        private long lastExternalRefreshAt;
    }
}
