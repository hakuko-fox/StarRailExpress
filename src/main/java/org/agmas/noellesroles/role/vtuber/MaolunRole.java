package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.TrueFalseResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.game.roles.vtuber.MaolunMathSession;
import org.agmas.noellesroles.packet.MaolunAnswerC2SPacket;
import org.agmas.noellesroles.packet.MaolunQuestionS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;

public class MaolunRole extends CustomWinnerRole {
    public MaolunRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
    }

    @Override
    public WinStatus checkWin(ServerPlayer player,
            WinStatus winStatus) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return WinStatus.NOT_MODIFY;
        }
        boolean isOnlySurvivor = player.serverLevel().players().stream()
                .noneMatch(other -> other != player && GameUtils.isPlayerAliveAndSurvival(other));
        return isOnlySurvivor
                ? WinStatus.CUSTOM
                : WinStatus.NOT_MODIFY;
    }

    @Override
    public boolean onUseGun(Player player) { return false; }

    @Override
    public boolean onUseKnife(Player player) { return false; }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return VtuberRoleSupport.isWeapon(item.getItem()) ? TrueFalseResult.FALSE : TrueFalseResult.PASS;
    }

    private record MaolunChallenge(UUID casterUuid, MaolunMathSession session) {
    }

    private static final Map<UUID, MaolunChallenge> MAOLUN_CHALLENGES = new HashMap<>();
    private static final Map<UUID, Integer> MAOLUN_FAILURES = new HashMap<>();

    public static boolean startMaolunChallenge(ServerPlayer caster, ServerPlayer target) {
        if (!canStartMaolunChallenge(caster, target)) {
            return false;
        }
        int duration = 60 * 20;
        MaolunMathSession session = new MaolunMathSession(target.level().getGameTime() + duration);
        MAOLUN_CHALLENGES.put(target.getUUID(), new MaolunChallenge(caster.getUUID(), session));
        MAOLUN_FAILURES.putIfAbsent(target.getUUID(), 0);
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, duration, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, duration, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, duration, 0, false, false, true));
        sendQuestion(target, session, 0);
        caster.displayClientMessage(Component.translatable("message.noellesroles.meowlen.challenge_started",
                target.getName()), true);
        return true;
    }

    public static boolean startMaolunSelection(ServerPlayer caster, ServerPlayer first, ServerPlayer second) {
        if (first == second || !canStartMaolunChallenge(caster, first)
                    || !canStartMaolunChallenge(caster, second)) {
            return false;
        }
        return startMaolunChallenge(caster, first) && startMaolunChallenge(caster, second);
    }

    private static boolean canStartMaolunChallenge(ServerPlayer caster, ServerPlayer target) {
        return target != null
                && GameUtils.isPlayerAliveAndSurvival(caster)
                && target.level() == caster.level()
                && SREGameWorldComponent.KEY.get(caster.level()).isRole(caster, ModRoles.MAOLUN)
                && caster != target
                && GameUtils.isPlayerAliveAndSurvival(target)
                && !MAOLUN_CHALLENGES.containsKey(target.getUUID());
    }

    public static boolean isChallengeActive(ServerPlayer target) {
        return MAOLUN_CHALLENGES.containsKey(target.getUUID());
    }

    public static void handleAnswer(ServerPlayer target, MaolunAnswerC2SPacket answer) {
        MaolunChallenge challenge = MAOLUN_CHALLENGES.get(target.getUUID());
        if (challenge == null) return;
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            finishMaolunChallenge(target, false);
            return;
        }
        var result = challenge.session().answer(answer.sessionId(), answer.sequence(), answer.choice(),
                target.level().getGameTime());
        switch (result) {
            case NEXT_QUESTION -> sendQuestion(target, challenge.session(), 0);
            case SUCCESS -> finishMaolunChallenge(target, true);
            case FAILURE -> finishMaolunChallenge(target, false);
            case IGNORED -> { }
        }
    }

    private static void sendQuestion(ServerPlayer target, MaolunMathSession session, int status) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                new MaolunQuestionS2CPacket(session.id(), session.sequence(), session.deadline(),
                        session.consecutiveCorrect(), session.remainingMistakes(), status,
                        status == 0 ? session.question().getQuestion() : "",
                        status == 0 ? session.question().getOptions() : List.of()));
    }

    private static boolean finishMaolunChallenge(ServerPlayer target, boolean success) {
        MaolunChallenge challenge = MAOLUN_CHALLENGES.remove(target.getUUID());
        if (challenge == null) {
            return false;
        }
        sendQuestion(target, challenge.session(), success ? 1 : 2);
        clearMaolunChallengeEffects(target);
        if (success) {
            target.displayClientMessage(Component.translatable(
                    "message.noellesroles.meowlen.challenge_succeeded"), true);
            return true;
        }
        int failures = MAOLUN_FAILURES.merge(target.getUUID(), 1, Integer::sum);
        target.displayClientMessage(Component.translatable("message.noellesroles.meowlen.challenge_failed",
                Component.literal(Integer.toString(Math.max(0, 2 - failures)))), true);
        if (failures >= 2) {
            MAOLUN_FAILURES.remove(target.getUUID());
            GameUtils.killPlayer(target, true, null, Noellesroles.id("meowlen_math_failure"));
        }
        return true;
    }

    public static void tickMaolunChallenges(net.minecraft.server.MinecraftServer server) {
        if (server == null || MAOLUN_CHALLENGES.isEmpty()) {
            return;
        }
        for (UUID targetUuid : java.util.List.copyOf(MAOLUN_CHALLENGES.keySet())) {
            MaolunChallenge challenge = MAOLUN_CHALLENGES.get(targetUuid);
            if (challenge == null) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
            if (target == null) {
                MAOLUN_CHALLENGES.remove(targetUuid);
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(target)
                    || target.level().getGameTime() >= challenge.session().deadline()) {
                finishMaolunChallenge(target, false);
            }
        }
    }

    private static void clearMaolunChallengeEffects(ServerPlayer target) {
        target.removeEffect(ModEffects.MOVE_BANED);
        target.removeEffect(ModEffects.USED_BANED);
        target.removeEffect(ModEffects.INVENTORY_BANED);
    }

    public static void registerEvents() {
        OnGameTrueStarted.EVENT.register(MaolunRole::resetChallenges);
        OnGameEnd.EVENT.register((level, game) -> resetChallenges(level));
    }

    private static void resetChallenges(net.minecraft.server.level.ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            MaolunChallenge challenge = MAOLUN_CHALLENGES.get(player.getUUID());
            if (challenge != null) {
                clearMaolunChallengeEffects(player);
                sendQuestion(player, challenge.session(), 2);
            }
        }
        MAOLUN_CHALLENGES.clear();
        MAOLUN_FAILURES.clear();
    }

    public static void selectMeowlenTargets(ServerPlayer caster, ServerPlayer first, ServerPlayer second) {
        long now = GameUtils.getTicksFromGameStart(caster.level());
        if (!GameUtils.isPlayerAliveAndSurvival(caster)
                    || RoleData.getNullable(VtuberRoleData.class, caster) == null) return;
        if (caster.hasEffect(ModEffects.SAFE_TIME)) {
            caster.displayClientMessage(Component.translatable("message.tip.cant_use_skill"), true);
            return;
        }
        long cooldownUntil = RoleData.getNullable(VtuberRoleData.class, caster).getMenuCooldownUntil();
        if (now < cooldownUntil) {
            long remainingSeconds = (cooldownUntil - now + 19L) / 20L;
            caster.displayClientMessage(Component.translatable(
                    "message.sre.skill.cooldown", remainingSeconds), true);
            return;
        }
        if (first == null || second == null || first == second || first == caster || second == caster
                || first.level() != caster.level() || second.level() != caster.level()
                || !GameUtils.isPlayerAliveAndSurvival(first) || !GameUtils.isPlayerAliveAndSurvival(second)) {
            return;
        }
        if (MaolunRole.startMaolunSelection(caster, first, second)) {

            RoleData.getNullable(VtuberRoleData.class, caster).setMenuCooldownUntil(now + 20L * 70L);
        }
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        player.removeEffect(MobEffects.BLINDNESS);
    }

}
