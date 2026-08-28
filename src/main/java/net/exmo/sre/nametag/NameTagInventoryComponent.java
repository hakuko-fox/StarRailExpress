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

package net.exmo.sre.nametag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.agmas.noellesroles.packet.NameTagSyncPayload;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NameTagInventoryComponent implements RoleComponent {
    private static final Logger logger = LoggerFactory.getLogger(NameTagInventoryComponent.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DATABASE_SYNC_KEY = "nametags";
    private static final long DATABASE_SYNC_FLUSH_TIMEOUT_MS = 4000L;

    public static final ComponentKey<NameTagInventoryComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("nametag_inventory"), NameTagInventoryComponent.class);

    private final Player player;
    public ArrayList<String> nameTags = new ArrayList<>();
    public String CurrentNameTag = "";
    private int killerWinStreak;
    private int policeWinStreak;
    private int neutralWinStreak;
    private int lossStreak;
    private int firstDeathStreak;

    private boolean isNetworkSyncEnabled = false;
    private boolean databaseLoadPending = false;
    private boolean databaseSyncQueued = false;

    public NameTagInventoryComponent(Player player) {
        this.player = player;
    }

    /**
     * 初始化网络同步
     * 
     * @param host 服务器主机地址
     * @param port 服务器端口
     * @param key  认证密钥
     */
    public void initializeNetworkSync(String host, int port, String key) {
        this.isNetworkSyncEnabled = SREConfig.instance().itemSkinSyncServerEnabled
                && SREConfig.instance().mysqlPlayerSyncEnabled
                && MysqlPlayerDataStore.isAvailable();
        this.databaseLoadPending = false;
        this.databaseSyncQueued = false;
        if (this.isNetworkSyncEnabled) {
            logger.info("玩家 {} 的名片 MySQL 同步已启用", this.player.getName().getString());
        } else if (SREConfig.instance().itemSkinSyncServerEnabled) {
            logger.warn("玩家 {} 的名片 MySQL 同步未启用，数据库不可用或配置未完成。", this.player.getName().getString());
        }
    }

    /**
     * 禁用全局网络同步
     */
    public static void disableGlobalNetworkSync() {
        MysqlPlayerDataStore.shutdown();
    }

    /**
     * 禁用网络同步
     */
    public void disableNetworkSync() {
        this.isNetworkSyncEnabled = false;
        this.databaseLoadPending = false;
        this.databaseSyncQueued = false;
    }

    public void syncFromLinkedServer() {
        if (!SREConfig.instance().itemSkinSyncServerEnabled)
            return;
        if (!this.isNetworkSyncEnabled || !(this.player instanceof ServerPlayer serverPlayer)
                || serverPlayer.getServer() == null) {
            return;
        }

        this.databaseLoadPending = true;
        MysqlPlayerDataStore.loadBatchAsync(this.player.getUUID(), List.of(DATABASE_SYNC_KEY))
                .thenAccept(records -> {
                    MysqlPlayerDataStore.SyncRecord record = records.get(DATABASE_SYNC_KEY);
                    serverPlayer.getServer().execute(() -> {
                        this.databaseLoadPending = false;
                        if (record != null && record.payload() != null && !record.payload().isBlank()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> nametagData = GSON.fromJson(record.payload(), Map.class);
                            if (nametagData != null) {
                                this.applyNetworkNametagData(nametagData);
                                this.persistLocal();
                                this.sync();
                                syncSelectedNameTagDisplay();
                                logger.debug("玩家 {} 的名片数据已从 MySQL 拉取", this.player.getName().getString());
                            }
                        }
                        flushQueuedNetworkSync();
                    });
                })
                .exceptionally(throwable -> {
                    this.databaseLoadPending = false;
                    logger.error("从 MySQL 拉取玩家 {} 的名片数据时出错", this.player.getName().getString(), throwable);
                    this.isNetworkSyncEnabled = false;
                    // 出错不同步
                    return null;
                });
    }

    public MutableComponent generate() {
        ArrayList<MutableComponent> toAddNameTags = new ArrayList<>();
        if (getPlayer().isSpectator()) {
            toAddNameTags.add(Component.translatable("starrailexpress.tag.spectator"));
        }
        // ComponentUtils.formatList(toAddNameTags);
        if (CurrentNameTag != null && !CurrentNameTag.isEmpty() && !CurrentNameTag.isBlank()) {
            toAddNameTags.add(NameTagTitleCatalog.displayText(CurrentNameTag));
        }
        if (!toAddNameTags.isEmpty()) {
            return ComponentUtils.formatList(toAddNameTags, Component.literal(" "), (t) -> {
                return t;
            }).copy();
        }
        return null;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        nameTags.clear();
        nameTags.addAll(compoundTag.getList("nameTags", 8).stream().map(Tag::getAsString).toList());

        CurrentNameTag = compoundTag.getString("CurrentNameTag");
        killerWinStreak = compoundTag.getInt("KillerWinStreak");
        policeWinStreak = compoundTag.getInt("PoliceWinStreak");
        neutralWinStreak = compoundTag.getInt("NeutralWinStreak");
        lossStreak = compoundTag.getInt("LossStreak");
        firstDeathStreak = compoundTag.getInt("FirstDeathStreak");
    }

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        // 保存 nameTags 列表
        ListTag nameTagsList = new ListTag();
        for (String nameTag : nameTags) {
            nameTagsList.add(StringTag.valueOf(nameTag));
        }
        compoundTag.put("nameTags", nameTagsList);

        // 保存当前选中的名片
        compoundTag.putString("CurrentNameTag", CurrentNameTag);
        compoundTag.putInt("KillerWinStreak", killerWinStreak);
        compoundTag.putInt("PoliceWinStreak", policeWinStreak);
        compoundTag.putInt("NeutralWinStreak", neutralWinStreak);
        compoundTag.putInt("LossStreak", lossStreak);
        compoundTag.putInt("FirstDeathStreak", firstDeathStreak);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.nameTags.clear();
        this.CurrentNameTag = "";
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
        this.persistLocal();
        this.syncToNetwork();
        this.syncSelectedNameTagDisplay();
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 添加名片
     */
    public void addNameTag(String nameTag) {
        if (!nameTags.contains(nameTag)) {
            nameTags.add(nameTag);
            this.sync();
            this.persistLocal();
            this.broadcastUnlock(nameTag);
            // 触发网络同步
            syncToNetwork();
        }
    }

    /** Adds a batch of recovered titles without emitting global unlock announcements. */
    public List<String> addNameTagsSilently(Iterable<String> recoveredNameTags) {
        ArrayList<String> added = new ArrayList<>();
        for (String nameTag : recoveredNameTags) {
            if (nameTag != null && !nameTag.isBlank()
                    && !this.nameTags.contains(nameTag) && !added.contains(nameTag)) {
                added.add(nameTag);
            }
        }
        if (added.isEmpty()) {
            return List.of();
        }

        this.nameTags.addAll(added);
        this.sync();
        this.persistLocal();
        syncToNetwork();
        return List.copyOf(added);
    }

    private void broadcastUnlock(String nameTag) {
        if (!(this.player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }
        broadcastUnlock(serverPlayer.getServer(), serverPlayer.getName(), nameTag);
    }

    public static void broadcastUnlock(MinecraftServer server, Component playerName, String nameTag) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.sre.nametag.global_unlocked",
                        playerName, NameTagTitleCatalog.displayText(nameTag)),
                false);
        Component title = Component.translatable("message.sre.nametag.title_unlocked", playerName)
                .withStyle(ChatFormatting.GOLD);
        Component subtitle = NameTagTitleCatalog.displayText(nameTag);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            SRENetworkMessageUtils.sendTitleTime(viewer, 10, 60, 20);
            SRENetworkMessageUtils.sendTitle(viewer, title);
            SRENetworkMessageUtils.sendSubtitle(viewer, subtitle);
        }
    }

    /**
     * 移除名片
     */
    public void removeNameTag(String nameTag) {
        if (nameTags.remove(nameTag)) {
            // 如果移除的是当前选中的名片，清空当前选中
            if (nameTag.equals(CurrentNameTag)) {
                CurrentNameTag = "";
            }
            this.sync();
            this.persistLocal();
            // 触发网络同步
            syncToNetwork();
            this.syncSelectedNameTagDisplay();
        }
    }

    public static void syncSelectedNameTags(MinecraftServer server) {
        if (server == null || !SREConfig.instance().isItemSkinEnabled) {
            return;
        }

        Map<UUID, String> selectedNameTags = new HashMap<>();
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            String selected = KEY.get(onlinePlayer).CurrentNameTag;
            if (selected != null && !selected.isBlank()) {
                selectedNameTags.put(onlinePlayer.getUUID(), selected);
            }
        }

        NameTagSyncPayload payload = new NameTagSyncPayload(selectedNameTags);
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(onlinePlayer, payload);
        }
    }

    /**
     * 设置当前名片
     */
    public void setCurrentNameTag(String nameTag) {
        if (nameTags.contains(nameTag)) {
            CurrentNameTag = nameTag;
            this.sync();
            this.persistLocal();
            // 触发网络同步
            syncToNetwork();
            this.syncSelectedNameTagDisplay();
        }
    }

    private void syncSelectedNameTagDisplay() {
        if (!(this.player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        syncSelectedNameTags(server);
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                List.of(serverPlayer));
        server.getPlayerList().broadcastAll(packet);
    }

    /**
     * 获取当前名片
     */
    public String getCurrentNameTag() {
        return CurrentNameTag;
    }

    /**
     * 将名片数据异步同步到 MySQL
     */
    public void syncToNetwork() {
        if (!this.isNetworkSyncEnabled) {
            return;
        }
        if (this.databaseLoadPending) {
            this.databaseSyncQueued = true;
            return;
        }

        MysqlPlayerDataStore.saveBatchAsync(
                this.player.getUUID(),
                Map.of(DATABASE_SYNC_KEY, GSON.toJson(buildNametagPayload())),
                System.currentTimeMillis())
                .whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        logger.warn("异步同步玩家 {} 的名片数据到 MySQL 失败。", this.player.getName().getString(), throwable);
                        return;
                    }
                    if (!Boolean.TRUE.equals(success)) {
                        logger.warn("异步同步玩家 {} 的名片数据到 MySQL 未成功写入。", this.player.getName().getString());
                        queueReloadAfterDatabaseConflict();
                    }
                });
    }

    /**
     * 应用从网络获取的名片数据
     */
    private void applyNetworkNametagData(Map<String, Object> nametagData) {
        try {
            if (nametagData.containsKey("nameTags")) {
                Object nameTagsObj = nametagData.get("nameTags");
                if (nameTagsObj instanceof List) {
                    List<?> rawList = (List<?>) nameTagsObj;
                    List<String> newNameTags = new ArrayList<>();
                    for (Object item : rawList) {
                        if (item instanceof String) {
                            newNameTags.add((String) item);
                        }
                    }
                    for (String nameTag : newNameTags) {
                        if (!this.nameTags.contains(nameTag)) {
                            this.nameTags.add(nameTag);
                        }
                    }
                }
            }

            if (nametagData.containsKey("currentNametag")) {
                Object currentNametag = nametagData.get("currentNametag");
                if (currentNametag instanceof String) {
                    String selected = (String) currentNametag;
                    if (this.nameTags.contains(selected)) {
                        this.CurrentNameTag = selected;
                    }
                }
            }

        } catch (Exception e) {
            logger.error("应用网络名片数据时出错", e);
        }
    }

    public boolean flushNetworkSyncBlocking() {
        if (!this.isNetworkSyncEnabled || this.databaseLoadPending) {
            return false;
        }
        boolean success = MysqlPlayerDataStore.saveBatchBlocking(
                this.player.getUUID(),
                Map.of(DATABASE_SYNC_KEY, GSON.toJson(buildNametagPayload())),
                System.currentTimeMillis(),
                DATABASE_SYNC_FLUSH_TIMEOUT_MS);
        if (!success) {
            queueReloadAfterDatabaseConflict();
        }
        return success;
    }

    public void flushNetworkSyncAsyncOnDisconnect() {
        if (!this.isNetworkSyncEnabled || this.databaseLoadPending) {
            return;
        }

        MysqlPlayerDataStore.saveBatchAsync(
                this.player.getUUID(),
                Map.of(DATABASE_SYNC_KEY, GSON.toJson(buildNametagPayload())),
                System.currentTimeMillis())
                .whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        logger.warn("断线时异步同步玩家 {} 的名片数据到 MySQL 失败。", this.player.getName().getString(), throwable);
                        return;
                    }
                    if (!Boolean.TRUE.equals(success)) {
                        logger.warn("断线时异步同步玩家 {} 的名片数据到 MySQL 未成功写入。", this.player.getName().getString());
                        queueReloadAfterDatabaseConflict();
                    }
                });
    }

    private void flushQueuedNetworkSync() {
        if (!this.databaseSyncQueued || !this.isNetworkSyncEnabled || this.databaseLoadPending) {
            return;
        }
        this.databaseSyncQueued = false;
        syncToNetwork();
    }

    private void queueReloadAfterDatabaseConflict() {
        if (!this.isNetworkSyncEnabled || this.databaseLoadPending) {
            return;
        }
        this.databaseSyncQueued = true;
        syncFromLinkedServer();
    }

    /**
     * 检查网络同步是否已启用
     */
    public boolean isNetworkSyncEnabled() {
        return this.isNetworkSyncEnabled;
    }

    private Map<String, Object> buildNametagPayload() {
        Map<String, Object> nametagData = new HashMap<>();
        nametagData.put("nameTags", new ArrayList<>(this.nameTags));
        nametagData.put("currentNametag", this.CurrentNameTag);
        nametagData.put("killerWinStreak", this.killerWinStreak);
        nametagData.put("policeWinStreak", this.policeWinStreak);
        nametagData.put("neutralWinStreak", this.neutralWinStreak);
        nametagData.put("lossStreak", this.lossStreak);
        nametagData.put("firstDeathStreak", this.firstDeathStreak);
        nametagData.put("version", System.currentTimeMillis());
        nametagData.put("timestamp", System.currentTimeMillis());
        return nametagData;
    }

    public void restoreLocalData() {
        if (this.player instanceof ServerPlayer serverPlayer) {
            NameTagDataStore.restore(serverPlayer, this);
            this.sync();
            this.syncSelectedNameTagDisplay();
        }
    }

    void mergeLocalData(NameTagDataStore.PlayerData data) {
        if (data.nameTags != null) {
            for (String nameTag : data.nameTags) {
                if (nameTag != null && !nameTag.isBlank() && !this.nameTags.contains(nameTag)) {
                    this.nameTags.add(nameTag);
                }
            }
        }
        if ((this.CurrentNameTag == null || this.CurrentNameTag.isBlank())
                && data.currentNameTag != null && this.nameTags.contains(data.currentNameTag)) {
            this.CurrentNameTag = data.currentNameTag;
        }
        this.killerWinStreak = Math.max(this.killerWinStreak, data.killerWinStreak);
        this.policeWinStreak = Math.max(this.policeWinStreak, data.policeWinStreak);
        this.neutralWinStreak = Math.max(this.neutralWinStreak, data.neutralWinStreak);
        this.lossStreak = Math.max(this.lossStreak, data.lossStreak);
        this.firstDeathStreak = Math.max(this.firstDeathStreak, data.firstDeathStreak);
    }

    public void updateAchievementStreaks(boolean killerRound, boolean policeRound, boolean neutralRound,
            boolean won, boolean firstDeath) {
        if (killerRound) {
            this.killerWinStreak = won ? this.killerWinStreak + 1 : 0;
        }
        if (policeRound) {
            this.policeWinStreak = won ? this.policeWinStreak + 1 : 0;
        }
        if (neutralRound) {
            this.neutralWinStreak = won ? this.neutralWinStreak + 1 : 0;
        }
        this.lossStreak = won ? 0 : this.lossStreak + 1;
        this.firstDeathStreak = firstDeath ? this.firstDeathStreak + 1 : 0;
        this.sync();
        this.persistLocal();
        this.syncToNetwork();
    }

    public int getKillerWinStreak() {
        return killerWinStreak;
    }

    public int getPoliceWinStreak() {
        return policeWinStreak;
    }

    public int getNeutralWinStreak() {
        return neutralWinStreak;
    }

    public int getLossStreak() {
        return lossStreak;
    }

    public int getFirstDeathStreak() {
        return firstDeathStreak;
    }

    private void persistLocal() {
        if (this.player instanceof ServerPlayer serverPlayer) {
            NameTagDataStore.save(serverPlayer, this);
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }
}
