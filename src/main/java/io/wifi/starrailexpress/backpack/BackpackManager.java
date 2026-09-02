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
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
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
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.SREDisableManager;
import net.exmo.sre.repair.role.RepairRole;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private BackpackManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(BackpackManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(BackpackManager::flushAllBlocking);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ENTRIES.clear());
    }

    // ====================== 公开服务端 API ======================

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
        if (roundId == null || amount < 0 || !isLoaded(player.getUUID())) {
            return false;
        }
        Entry entry = getEntry(player.getUUID());
        String id = roundId.toString();
        if (id.equals(entry.state.lastVtuberCoinRoundId)) {
            return false;
        }
        long next = (long) entry.state.vtuberCoins + amount;
        if (next > Integer.MAX_VALUE) {
            return false;
        }
        entry.state.vtuberCoins = (int) next;
        entry.state.lastVtuberCoinRoundId = id;
        markDirty(player, entry);
        return true;
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

    /** 逐字复刻 {@code ProgressionDataManager.activateFactionCard}：卡库写改为背包。 */
    public static boolean activateCard(ServerPlayer player, FactionCardType type) {
        if (type == FactionCardType.NONE || !isLoaded(player.getUUID())) {
            return false;
        }
        Entry entry = getEntry(player.getUUID());
        int current = entry.state.cards.getOrDefault(type, 0);
        if (current < 1
                || PlayerRoleWeightManager.ForcePlayerTeam.containsKey(player.getUUID())) {
            return false;
        }
        if (!entry.state.pendingRoleId.isBlank()) {
            entry.state.pendingRoleId = "";
            entry.state.roleChoiceCards = Math.min(Integer.MAX_VALUE, entry.state.roleChoiceCards + 1);
        }
        PlayerRoleWeightManager.ForcePlayerTeam.put(player.getUUID(), type.getTypeRoleId());
        entry.state.cards.put(type, current - 1);
        entry.state.pendingFactionCard = type;
        markDirty(player, entry);
        Component message = Component.translatable("message.sre.progression.faction_card_activated",
                Component.translatable(type.displayName));
        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);
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
            FactionCardType previous = entry.state.pendingFactionCard;
            entry.state.cards.merge(previous, 1, Integer::sum);
            PlayerRoleWeightManager.ForcePlayerTeam.remove(player.getUUID());
            entry.state.pendingFactionCard = FactionCardType.NONE;
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
            entry.state.roleChoiceCards = Math.min(Integer.MAX_VALUE, entry.state.roleChoiceCards + 1);
            changed = true;
        }
        if (entry.state.pendingFactionCard != FactionCardType.NONE) {
            FactionCardType type = entry.state.pendingFactionCard;
            entry.state.cards.merge(type, 1, Integer::sum);
            entry.state.pendingFactionCard = FactionCardType.NONE;
            PlayerRoleWeightManager.ForcePlayerTeam.remove(player.getUUID());
            changed = true;
        }
        if (!changed) {
            return ChoiceResult.NO_SELECTION;
        }
        markDirty(player, entry);
        return ChoiceResult.SUCCESS;
    }

    /** Resolve persistent role choices before the standard murder-role assignment starts. */
    public static void prepareRoleChoices(net.minecraft.server.level.ServerLevel world,
            List<ServerPlayer> players, boolean supportedMode) {
        if (!supportedMode) {
            for (ServerPlayer player : players) {
                refundPendingRole(player, "message.sre.backpack.choice.mode_refund");
            }
            return;
        }

        List<BackpackRoleChoiceResolver.Request> applicants = new ArrayList<>();
        Map<String, Integer> reserved = new HashMap<>();
        for (ServerPlayer player : players) {
            Entry entry = ENTRIES.get(player.getUUID());
            if (entry == null || !entry.loaded || entry.state.pendingFactionCard == FactionCardType.NONE
                    || !entry.state.pendingRoleId.isBlank()) {
                continue;
            }
            if (Harpymodloader.FORCED_MODDED_ROLE.containsKey(player.getUUID())) {
                entry.state.cards.merge(entry.state.pendingFactionCard, 1, Integer::sum);
                entry.state.pendingFactionCard = FactionCardType.NONE;
                markDirty(player, entry);
                continue;
            }
            PlayerRoleWeightManager.ForcePlayerTeam.put(player.getUUID(),
                    entry.state.pendingFactionCard.getTypeRoleId());
            entry.state.pendingFactionCard = FactionCardType.NONE;
            markDirty(player, entry);
        }
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
            applicants.add(new BackpackRoleChoiceResolver.Request(player.getUUID(), role.identifier().toString()));
        }
        for (ServerPlayer player : players) {
            SRERole role = Harpymodloader.FORCED_MODDED_ROLE.get(player.getUUID());
            if (role != null) {
                reserved.merge(role.identifier().toString(), 1, Integer::sum);
            }
        }

        Map<String, Integer> capacities = new HashMap<>();
        for (BackpackRoleChoiceResolver.Request request : applicants) {
            ResourceLocation roleId = ResourceLocation.tryParse(request.roleId());
            capacities.put(request.roleId(), Math.max(0, Harpymodloader.ROLE_MAX.getOrDefault(roleId, 1)));
        }
        BackpackRoleChoiceResolver.Resolution resolution = BackpackRoleChoiceResolver.resolve(
                applicants, capacities, reserved, new Random(world.random.nextLong()));
        for (Map.Entry<UUID, String> winner : resolution.winners().entrySet()) {
            ResourceLocation roleId = ResourceLocation.tryParse(winner.getValue());
            SRERole role = roleId == null ? null : TMMRoles.getRole(roleId);
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(winner.getKey());
            if (player != null && role != null) {
                Harpymodloader.FORCED_MODDED_ROLE.put(player.getUUID(), role);
                clearPendingRole(player);
            }
        }
        for (UUID loser : resolution.losers()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(loser);
            if (player != null) {
                refundPendingRole(player, "message.sre.backpack.choice.conflict_refund");
            }
        }
    }

    private static boolean isSelectableRole(SRERole role) {
        if (role == null || role == TMMRoles.DISCOVERY_CIVILIAN || role == TMMRoles.LOOSE_END
                || role.isOtherModeRole() || role instanceof RepairRole || role.getOccupiedRoleCount() > 1
                || SREDisableManager.isRoleDisabled(role) || !RoleRosterManager.isRoleEnabled(role)) {
            return false;
        }
        return Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1) > 0;
    }

    private static void clearPendingRole(ServerPlayer player) {
        Entry entry = getEntry(player.getUUID());
        entry.state.pendingRoleId = "";
        markDirty(player, entry);
    }

    private static void refundPendingRole(ServerPlayer player, String messageKey) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry == null || !entry.loaded || entry.state.pendingRoleId.isBlank()) {
            return;
        }
        entry.state.pendingRoleId = "";
        entry.state.roleChoiceCards = Math.min(Integer.MAX_VALUE, entry.state.roleChoiceCards + 1);
        markDirty(player, entry);
        player.displayClientMessage(Component.translatable(messageKey), true);
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
        if (entry == null || !isDatabaseEnabled()) {
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

        if (bp.migrated) {
            // 已迁移：清理可能残留的陈旧通行证卡牌（崩溃于清源持久化前的情形）
            boolean stale = false;
            for (FactionCardType type : FactionCardType.values()) {
                if (type != FactionCardType.NONE && pg.factionCards.getOrDefault(type, 0) > 0) {
                    pg.factionCards.put(type, 0);
                    stale = true;
                }
            }
            if (stale) {
                ProgressionDataManager.markFactionCardsCleared(player);
                ProgressionDataManager.flushBlocking(uuid);
            }
            return;
        }

        // 1) 加法合并通行证卡牌进背包
        for (FactionCardType type : FactionCardType.values()) {
            if (type == FactionCardType.NONE) {
                continue;
            }
            int c = pg.factionCards.getOrDefault(type, 0);
            if (c > 0) {
                bp.cards.merge(type, c, Integer::sum);
            }
        }
        // 2) 置 migrated 并先落背包（卡牌绝不会丢）
        bp.migrated = true;
        markDirty(player, entry);
        flushBlocking(uuid);
        // 3) 清空通行证侧并持久化
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                pg.factionCards.put(type, 0);
            }
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
        if (!isDatabaseEnabled() || entry.loadInFlight) {
            return;
        }
        entry.loadInFlight = true;
        entry.lastLoadAttemptAt = System.currentTimeMillis();
        MysqlPlayerDataStore.loadBatchAsync(player.getUUID(), List.of(PART))
                .whenComplete((records, throwable) -> {
                    entry.loadInFlight = false;
                    MinecraftServer server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    server.execute(() -> {
                        if (ENTRIES.get(player.getUUID()) != entry) {
                            return;
                        }
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to load backpack part for {}", player.getUUID(), throwable);
                            return;
                        }
                        MysqlPlayerDataStore.SyncRecord record = records.get(PART);
                        if (record != null && record.payload() != null && !record.payload().isBlank()) {
                            entry.state = fromJson(record.payload());
                            entry.updatedAt = Math.max(entry.updatedAt, record.updatedAt());
                            entry.dirty = false;
                        }
                        entry.loaded = true;
                        send(player, entry);
                        migrateIfNeeded(player);
                    });
                });
    }

    private static void onDisconnect(ServerPlayer player) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry != null) {
            entry.online = false;
            if (!isDatabaseEnabled()) {
                saveLocal(player, entry);
                ENTRIES.remove(player.getUUID(), entry);
            } else if (flushBlocking(player.getUUID())) {
                ENTRIES.remove(player.getUUID(), entry);
            } else {
                SRE.LOGGER.warn("Keeping unsaved backpack data for {} in memory after disconnect",
                        player.getUUID());
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Entry entry = ENTRIES.get(player.getUUID());
            if (entry == null || !entry.online) {
                continue;
            }
            if (!entry.loaded && !entry.loadInFlight
                    && now - entry.lastLoadAttemptAt >= FLUSH_INTERVAL_MS) {
                reloadFromDatabase(player, entry);
                continue;
            }
            if (!entry.dirty || entry.saveInFlight
                    || now - entry.lastFlushAt < FLUSH_INTERVAL_MS) {
                continue;
            }
            flushAsync(player, entry);
        }
    }

    private static void flushAsync(ServerPlayer player, Entry entry) {
        if (!isDatabaseEnabled() || entry.loadInFlight) {
            return;
        }
        entry.saveInFlight = true;
        entry.dirty = false;
        entry.lastFlushAt = System.currentTimeMillis();
        long updatedAt = Math.max(1L, entry.updatedAt);
        MysqlPlayerDataStore.saveBatchAsync(player.getUUID(), Map.of(PART, toJson(entry.state, updatedAt)), updatedAt)
                .whenComplete((success, throwable) -> {
                    entry.saveInFlight = false;
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        entry.dirty = true;
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to save backpack part for {}", player.getUUID(), throwable);
                        } else {
                            SRE.LOGGER.warn("Failed to save backpack part for {}; retaining local changes for retry",
                                    player.getUUID());
                        }
                    }
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
    }
}
