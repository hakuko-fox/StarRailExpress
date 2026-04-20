package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class GameMode {
    public final ResourceLocation identifier;
    public final int defaultStartTime;
    public final int minPlayerCount;

    /**
     * @param identifier       the game mode identifier
     * @param defaultStartTime the default time at which the timer will be set at
     *                         the start of the game mode, in minutes
     * @param minPlayerCount   the minimum amount of players required to start the
     *                         game mode
     */
    public GameMode(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        this.identifier = identifier;
        this.defaultStartTime = defaultStartTime;
        this.minPlayerCount = minPlayerCount;
    }

    public void readFromNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {

    }

    public void writeToNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {

    }

    public void tickCommonGameLoop() {
    }

    public void tickClientGameLoop() {
    }

    public boolean requiresAssignedRole() {
        return true;
    }

    public boolean isLooseEndMode() {
        return false;
    }

    /**
     * 记录玩家数据
     * 
     * @param serverWorld
     * @param gameComponent
     * @param readyPlayerList
     */
    public void recordPlayerStats(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        GameUtils.recordPlayerStats(serverWorld, gameComponent, readyPlayerList);
    }

    /**
     * 在游戏开始initializeGame后触发，在OnGameTrueStarted前触发
     * 
     * @param serverWorld
     * @param gameComponent
     * @param readyPlayerList
     */
    public void gameTrueStarted(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        int SAFE_TIME_COOLDOWN = SREConfig.instance().safeTimeCooldown * 20;
        if (gameComponent.getGameMode().hasSafeTime()) {
            GameUtils.addItemCooldowns(serverWorld, SAFE_TIME_COOLDOWN);
        }
        GameUtils.executeFunction(serverWorld.getServer().createCommandSourceStack(),
                "harpymodloader:start_game_" + AreasWorldComponent.KEY.get(serverWorld).mapName);
    }

    public boolean enforcesPlayAreaElimination() {
        return true;
    }

    public abstract void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent);

    public abstract void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players);

    public void beforeInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        GameUtils.baseInitialize(serverWorld, gameWorldComponent, players);
    }

    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {

    }

    public boolean hasSafeTime() {
        return true;
    }

    public boolean hasMood() {
        return true;
    }

    public Component getName() {
        // 根据游戏模式ID返回本地化的名称
        String gameModeId = this.identifier.getPath();
        return Component.translatableWithFallback("hud.sre.tip.gamemode." + gameModeId, gameModeId);
    }

    public void afterInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        // 初始化回放管理器,此时角色已经分配完成
        SRE.REPLAY_MANAGER.initializeReplay(readyPlayerList, gameComponent.getRoles());
        // 记录游戏开始事件
        SRE.REPLAY_MANAGER.addEvent(GameReplayData.EventType.GAME_START, null, null, null, null);

        // Update replay with actual roles after assignment
        SRE.REPLAY_MANAGER.updateRolesFromComponent(gameComponent);
    }

    public void stopGame(ServerLevel world) {
    }

    public void recordWinStats(ServerLevel world, SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent gameComponent) {
        GameUtils.recordWinStats(world, roundEnd, gameComponent);
    }

    public void showReplay(ServerLevel world, SREGameRoundEndComponent roundEnd, SREGameWorldComponent gameComponent) {
        Component text = SRE.REPLAY_MANAGER.generateReplay();
        for (ServerPlayer player : world.players()) {
            GameReplayManager.sendSystemMessage(player, text);
        }
    }
}