package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.modes.funny.rotation.LightningDraftState;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.network.packet.RoleRotationSelectC2SPacket;
import io.wifi.starrailexpress.network.packet.RoleRotationSyncS2CPacket;
import io.wifi.starrailexpress.network.CloseUiPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

public class SRERoleRotationGameMode extends SREMurderGameMode {

    private static final int ROTATION_SAFE_TIME = 5 * 60 * 20;
    private boolean isInRotationPhase = false;
    private long rotationTimeout = -1;
    private LightningDraftState draftState;

    public SRERoleRotationGameMode(ResourceLocation identifier) {
        super(identifier, 10, 3);
    }

    // 静态注册网络接收器
    public static void registerServerPacketRecievers() {
        ServerPlayNetworking.registerGlobalReceiver(RoleRotationSelectC2SPacket.TYPE, (packet, context) -> {
            context.player().server.execute(() -> {
                ServerPlayer player = context.player();
                if (player.level() instanceof ServerLevel serverLevel) {
                    var gameMode = SREGameWorldComponent.getInstance(serverLevel).getGameMode();
                    if (gameMode instanceof SRERoleRotationGameMode rotationMode) {
                        rotationMode.handlePlayerSelection(player, packet.choiceIndex());
                    }
                }
            });
        });
    }

    @Override
    public void initializeGame(ServerLevel world, SREGameWorldComponent gameComp, List<ServerPlayer> players) {
        gameComp.clearRoleMap(false);
        for (ServerPlayer p : players) {
            gameComp.addRole(p, SpecialGameModeRoles.CUSTOM_PENDING, false);
            p.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(
                    new MobEffectInstance(MobEffects.INVISIBILITY, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ROTATION_SAFE_TIME + 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, 40, 10, true, false, false));
            p.addEffect(new MobEffectInstance(ModEffects.CCA_FREEZED, 40, 10, true, false, false));
            RoleUtils.sendWelcomeAnnouncement(p);
        }

        // 初始化闪电轮抽
        draftState = new LightningDraftState(new ArrayList<>(players));
        draftState.initializeRolePool(world);
        draftState.assignRotationOrder();
        draftState.startNextRound(world);

        isInRotationPhase = true;
        rotationTimeout = world.getGameTime() + ROTATION_SAFE_TIME;
        broadcastSync(world);
    }

    private void broadcastSync(ServerLevel world) {
        RoleRotationSyncS2CPacket packet = new RoleRotationSyncS2CPacket(
                draftState.isSelecting,
                draftState.currentRoundIndex,
                draftState.totalPlayers,
                draftState.confirmCountdown,
                draftState.perPlayerTimeLimit,
                draftState.roundStartTime,
                draftState.playerOrder,
                draftState.getSelectedRolesAsStrings(),
                draftState.randomChoosers,
                draftState.getRoundCandidatesAsStrings());
        for (ServerPlayer p : world.players()) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    private void handlePlayerSelection(ServerPlayer player, int choiceIndex) {
        if (!isInRotationPhase || draftState == null)
            return;
        if (draftState.processSelection(player.serverLevel(), player.getUUID(), choiceIndex)) {
            broadcastSync(player.serverLevel());
        }
    }

    @Override
    public void tickServerGameLoop(ServerLevel world, SREGameWorldComponent gameComp) {
        if (!isInRotationPhase || draftState == null)
            return;

        // 总超时
        if (world.getGameTime() >= rotationTimeout) {
            forceFinishRotation(world, gameComp);
            return;
        }

        // 确认倒计时
        if (!draftState.isSelecting && draftState.confirmCountdown > 0) {
            draftState.confirmCountdown--;
            if (draftState.confirmCountdown % 20 == 0)
                broadcastSync(world);
            if (draftState.confirmCountdown <= 0) {
                draftState.adjustRemainingRoles(world);
                finishRotationPhase(world, gameComp);
                return;
            }
        }

        // 轮选超时
        if (draftState.isSelecting) {
            long elapsed = world.getGameTime() - draftState.roundStartTime;
            if (elapsed >= draftState.perPlayerTimeLimit) {
                draftState.timeoutUnfinishedPlayers(world);
                broadcastSync(world);
            }
        }

        super.tickServerGameLoop(world, gameComp);
    }

    private void forceFinishRotation(ServerLevel world, SREGameWorldComponent gameComp) {
        for (UUID uuid : draftState.playerOrder) {
            if (!draftState.selectedRoles.containsKey(uuid)) {
                SRERole role = draftState.rolePool.isEmpty() ? TMMRoles.CIVILIAN : draftState.rolePool.remove(0);
                draftState.selectedRoles.put(uuid, role);
            }
        }
        draftState.remainingRoles = 0;
        finishRotationPhase(world, gameComp);
    }

    private void finishRotationPhase(ServerLevel world, SREGameWorldComponent gameComp) {
        Map<UUID, SRERole> finalRoles = new HashMap<>(draftState.selectedRoles);
        isInRotationPhase = false;
        draftState = null;

        completeRoleSelection(world, gameComp, finalRoles);

        world.players().forEach(p -> {
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(p);
            mood.setMood(1);
            mood.sync();
        });
        OnGameTrueStarted.EVENT.invoker().onGameTrueStarted(world);
    }

    private void completeRoleSelection(ServerLevel world, SREGameWorldComponent gameComp,
            Map<UUID, SRERole> selectedRoles) {
        SRERoleWorldComponent roleComp = SRERoleWorldComponent.KEY.get(world);
        for (ServerPlayer p : world.players()) {
            SRERole role = selectedRoles.get(p.getUUID());
            if (role != null) {
                gameComp.addRole(p, role, false);
                p.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.selected",
                                RoleUtils.getRoleName(role).withColor(role.getColor()))
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
        roleComp.sync();

        List<ServerPlayer> alive = world.getPlayers(GameUtils::isPlayerAliveAndSurvivalIgnoreShitSplit);
        for (ServerPlayer p : alive) {
            SRERole role = roleComp.getRole(p);
            p.removeEffect(ModEffects.SKILL_BANED);
            p.removeEffect(ModEffects.SAFE_TIME);
            p.removeEffect(ModEffects.MOVE_BANED);
            p.removeEffect(MobEffects.INVISIBILITY);

            if (role != null) {
                RoleUtils.sendWelcomeAnnouncement(p);
                if (role.canUseKiller()) {
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(p);
                    if (shop.balance < GameConstants.getMoneyStart()) {
                        shop.setBalance(GameConstants.getMoneyStart());
                    }
                }
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(p, role);
            }
            ServerPlayNetworking.send(p, new CloseUiPayload());
        }

        int safeTime = SREConfig.instance().safeTimeCooldown * 20;
        GameUtils.addItemCooldowns(world, safeTime);

        int modifierCount = (int) (alive.size() * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierCount, world, gameComp, alive);
        GameUtils.recordPlayerStats(world, gameComp, new ArrayList<>(world.players()));
        SRE.REPLAY_MANAGER.updateReplayInitialRoles(alive, gameComp.getRoles());
    }

    @Override
    public void finalizeGame(ServerLevel world, SREGameWorldComponent gameComp) {
        super.finalizeGame(world, gameComp);
        isInRotationPhase = false;
        rotationTimeout = -1;
        draftState = null;
    }

    @Override
    public boolean autoTriggerGameTrueStarted() {
        return false;
    }

    @Override
    public GameUtils.WinStatus allowGameEnd(ServerLevel world, GameUtils.WinStatus winStatus, boolean looseEnds,
            SREGameWorldComponent gameComp) {
        if (isInRotationPhase)
            return GameUtils.WinStatus.NONE;
        return AllowGameEnd.EVENT.invoker().allowGameEnd(world, winStatus, false);
    }

    @Override
    public void gameStarted(ServerLevel world, SREGameWorldComponent gameComp, ArrayList<ServerPlayer> ready) {
    }

    @Override
    public void recordPlayerStats(ServerLevel world, SREGameWorldComponent gameComp, ArrayList<ServerPlayer> ready) {
    }
}