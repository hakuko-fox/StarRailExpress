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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.agmas.noellesroles.packet.ProblemScreenOpenC2SPacket;
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

    private record MaolunChallenge(UUID casterUuid, long deadlineTick) {
    }

    private static final Map<UUID, MaolunChallenge> MAOLUN_CHALLENGES = new HashMap<>();
    private static final Set<UUID> MAOLUN_RESOLVED_RESULTS = new java.util.HashSet<>();
    private static final Map<UUID, Integer> MAOLUN_FAILURES = new HashMap<>();

    public static boolean startMaolunChallenge(ServerPlayer caster, ServerPlayer target) {
        if (!canStartMaolunChallenge(caster, target)) {
            return false;
        }
        int duration = 60 * 20;
        MAOLUN_RESOLVED_RESULTS.remove(target.getUUID());
        MAOLUN_CHALLENGES.put(target.getUUID(), new MaolunChallenge(caster.getUUID(),
                target.level().getGameTime() + duration));
        MAOLUN_FAILURES.putIfAbsent(target.getUUID(), 0);
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, duration, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, duration, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, duration, 0, false, false, true));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                new ProblemScreenOpenC2SPacket(true, 2, 60, true));
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

    public static boolean finishMaolunChallenge(ServerPlayer target, boolean success) {
        if (!MAOLUN_CHALLENGES.containsKey(target.getUUID())) {
            return false;
        }
        clearMaolunChallengeEffects(target);
        if (success) {
            MAOLUN_CHALLENGES.remove(target.getUUID());
            target.displayClientMessage(Component.translatable(
                    "message.noellesroles.meowlen.challenge_succeeded"), true);
            return true;
        }
        MAOLUN_CHALLENGES.remove(target.getUUID());
        int failures = MAOLUN_FAILURES.merge(target.getUUID(), 1, Integer::sum);
        target.displayClientMessage(Component.translatable("message.noellesroles.meowlen.challenge_failed",
                Component.literal(Integer.toString(Math.max(0, 2 - failures)))), true);
        if (failures >= 2) {
            MAOLUN_FAILURES.remove(target.getUUID());
            GameUtils.killPlayer(target, true, null, Noellesroles.id("meowlen_math_failure"));
        }
        return true;
    }

    public static boolean consumeResolvedMaolunChallengeResult(ServerPlayer target) {
        return MAOLUN_RESOLVED_RESULTS.remove(target.getUUID());
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
            if (target.level().getGameTime() >= challenge.deadlineTick()) {
                if (finishMaolunChallenge(target, false)) {
                    MAOLUN_RESOLVED_RESULTS.add(targetUuid);
                }
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
            if (MAOLUN_CHALLENGES.containsKey(player.getUUID())) clearMaolunChallengeEffects(player);
        }
        MAOLUN_CHALLENGES.clear();
        MAOLUN_RESOLVED_RESULTS.clear();
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
