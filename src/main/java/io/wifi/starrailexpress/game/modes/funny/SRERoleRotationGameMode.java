package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.RoleRotationWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.RoleRotationPlayerComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.network.RoleRotationSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

public class SRERoleRotationGameMode extends SREMurderGameMode {

    // 职业轮选安全时间（tick）- 5分钟
    private static final int ROTATION_SAFE_TIME = 5 * 60 * 20;

    // 当前是否正在职业轮选阶段
    private boolean isInRotationPhase = false;

    // 职业轮选阶段的倒计时
    private long rotationTimeout = -1;

    // 当前轮到第几个玩家
    private int currentTurnIndex = 1;

    // 玩家抽选职业的时间限制（tick）
    private int turnTimeLimit = 4 * 20; // 4秒

    public SRERoleRotationGameMode(ResourceLocation identifier) {
        super(identifier, 10, 3);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return true;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 设置为午夜
        (io.wifi.starrailexpress.cca.SRETrainWorldComponent.KEY.get(serverWorld))
                .setTimeOfDay(io.wifi.starrailexpress.cca.SRETrainWorldComponent.TimeOfDay.MIDNIGHT);

        // 清除现有角色
        gameWorldComponent.clearRoleMap();

        // 为所有玩家分配待定职业
        ArrayList<ServerPlayer> unassignedPlayers = new ArrayList<>(players);
        for (ServerPlayer player : unassignedPlayers) {
            gameWorldComponent.addRole(player, SpecialGameModeRoles.CUSTOM_PENDING, false);
            RoleRotationPlayerComponent.KEY.get(player).reset();
            player.addEffect(new MobEffectInstance(
                    ModEffects.SAFE_TIME,
                    ROTATION_SAFE_TIME + 40,
                    10, true, false, false));
            player.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    ROTATION_SAFE_TIME + 40,
                    10, true, false, false));
            player.addEffect(new MobEffectInstance(
                    ModEffects.SKILL_BANED,
                    40,
                    10, true, false, false));
            RoleUtils.sendWelcomeAnnouncement(player);
        }

        // 初始化角色池和轮选顺序
        RoleRotationWorldComponent rrwc = RoleRotationWorldComponent.KEY.get(serverWorld);
        rrwc.initializeRolePool(serverWorld, players);
        rrwc.startSelection();

        // 设置轮选超时
        isInRotationPhase = true;
        rotationTimeout = serverWorld.getGameTime() + ROTATION_SAFE_TIME;

        // 同步到客户端
        rrwc.sync();

        // 向所有玩家发送职业轮选GUI
        sendRotationGuiToAllPlayers(serverWorld);

        // 同步职业轮选状态
        broadcastRotationState(serverWorld);
    }

    private void sendRotationGuiToAllPlayers(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            RoleRotationSyncS2CPacket.sendToPlayer(player);
        }
    }

    private void broadcastRotationState(ServerLevel serverWorld) {
        RoleRotationWorldComponent rrwc = RoleRotationWorldComponent.KEY.get(serverWorld);
        for (ServerPlayer player : serverWorld.players()) {
            RoleRotationSyncS2CPacket.sendToPlayer(player);
            // 打开GUI
            ServerPlayNetworking.send(player, new CloseUiPayload()); // 先关闭可能存在的UI
        }
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.finalizeGame(serverWorld, gameWorldComponent);
        isInRotationPhase = false;
        rotationTimeout = -1;
        RoleRotationWorldComponent.KEY.get(serverWorld).clear();
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 处理职业轮选阶段
        if (isInRotationPhase && rotationTimeout != -1) {
            // 检查总超时（5分钟）
            if (serverWorld.getGameTime() >= rotationTimeout) {
                // 总超时，所有玩家选完职业
                finishRotationPhase(serverWorld, gameWorldComponent);
            } else {
                // 检查当前玩家的个人选择超时
                RoleRotationWorldComponent rrwc = RoleRotationWorldComponent.KEY.get(serverWorld);
                if (rrwc.isSelecting() && rrwc.isCurrentPlayerTimedOut()) {
                    // 当前玩家超时，自动随机分配职业
                    rrwc.autoAssignCurrentPlayer();
                    rrwc.sync();
                }
            }
        }

        super.tickServerGameLoop(serverWorld, gameWorldComponent);
    }

    @Override
    public void gameTrueStarted(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        // 不调用父类的安全时间，让玩家一直处于职业轮选安全时间直到选完职业
    }

    private void finishRotationPhase(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        RoleRotationWorldComponent rrwc = RoleRotationWorldComponent.KEY.get(serverWorld);

        // 检查是否所有玩家都已选完职业
        if (rrwc.getSelectedRoles().size() >= rrwc.getTotalPlayers()) {
            completeRoleSelection(serverWorld, gameWorldComponent);
        } else {
            // 未全部选完，自动为剩余玩家分配职业
            autoAssignRemainingPlayers(serverWorld, gameWorldComponent);
        }

        isInRotationPhase = false;
        rotationTimeout = -1;
    }

    public void completeRoleSelection(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 移除安全时间效果
        for (ServerPlayer p : serverWorld.players()) {
            p.removeEffect(ModEffects.SKILL_BANED);
            p.removeEffect(ModEffects.SAFE_TIME);
            p.removeEffect(MobEffects.INVISIBILITY);
            ServerPlayNetworking.send(p, new CloseUiPayload());
        }

        // 开始25秒安全时间
        int SAFE_TIME_COOLDOWN = SREConfig.instance().safeTimeCooldown * 20;
        GameUtils.addItemCooldowns(serverWorld, SAFE_TIME_COOLDOWN);

        // 记录玩家数据
        GameUtils.recordPlayerStats(serverWorld, gameWorldComponent, new ArrayList<>(serverWorld.players()));

        // 更新回放管理器
        SRE.REPLAY_MANAGER.updateReplayInitialRoles(new ArrayList<>(serverWorld.players()), gameWorldComponent.getRoles());

        // 清除状态
        RoleRotationWorldComponent.KEY.get(serverWorld).clear();
    }

    private void autoAssignRemainingPlayers(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        RoleRotationWorldComponent rrwc = RoleRotationWorldComponent.KEY.get(serverWorld);

        for (ServerPlayer player : serverWorld.players()) {
            if (!rrwc.getSelectedRoles().containsKey(player.getUUID())) {
                // 自动分配职业
                ArrayList<SRERole> pool = rrwc.getRolePool();
                SRERole role = pool.isEmpty() ? TMMRoles.CIVILIAN : pool.get(0);

                rrwc.getSelectedRoles().put(player.getUUID(), role);
                if (!pool.isEmpty()) {
                    pool.remove(0);
                }

                // 应用职业
                SRERoleWorldComponent roleWorldComponent = SRERoleWorldComponent.KEY.get(player.level());
                roleWorldComponent.addRole(player.getUUID(), role, false);

                player.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.auto_assigned",
                                RoleUtils.getRoleName(role).withColor(role.getColor()))
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            }
        }

        completeRoleSelection(serverWorld, gameWorldComponent);
    }

    @Override
    public GameUtils.WinStatus allowGameEnd(ServerLevel serverWorld, GameUtils.WinStatus winStatus,
            boolean isLooseEndsMode, SREGameWorldComponent gameWorldComponent) {
        if (isInRotationPhase) {
            return GameUtils.WinStatus.NONE;
        }
        return AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld, winStatus, false);
    }

    @Override
    public void recordPlayerStats(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        // 开始游戏后记录
    }

    // 处理玩家选择职业
    public void handlePlayerRoleSelection(ServerPlayer player, int choiceIndex) {
        RoleRotationWorldComponent rrwc = RoleRotationWorldComponent.KEY.get(player.level());
        rrwc.selectRole(player, choiceIndex);
    }

    // 获取当前轮到哪个玩家
    public int getCurrentTurnIndex() {
        return currentTurnIndex;
    }

    // 获取轮选时间限制
    public int getTurnTimeLimit() {
        return turnTimeLimit;
    }
}
