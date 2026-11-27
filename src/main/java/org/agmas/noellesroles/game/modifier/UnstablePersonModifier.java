package org.agmas.noellesroles.game.modifier;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.GameUtilityEvents;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.Map;
import java.util.WeakHashMap;

public final class UnstablePersonModifier {
    private static final int PHASE_TICKS = 15 * 20;
    private static final int MIN_WAIT_TICKS = 45 * 20;
    private static final int MAX_WAIT_TICKS = 90 * 20;
    private static final int SOUND_RADIUS = 10;
    private static final Map<ServerPlayer, Long> NEXT_SHIFT = new WeakHashMap<>();

    private UnstablePersonModifier() {}

    public static boolean isShifted(Player player) {
        return player.hasEffect(ModEffects.UNSTABLE_SHIFT);
    }

    static void serverTick(ServerPlayer player) {
        if (!SREGameWorldComponent.KEY.get(player.level()).isRunning()
                || !GameUtils.isPlayerAliveAndSurvival(player)) {
            clear(player);
            return;
        }

        long now = player.level().getGameTime();
        long next = NEXT_SHIFT.computeIfAbsent(player, ignored -> now + randomWait(player));
        if (now < next) {
            return;
        }

        NEXT_SHIFT.put(player, now + PHASE_TICKS + randomWait(player));
        player.addEffect(new MobEffectInstance(ModEffects.UNSTABLE_SHIFT, PHASE_TICKS, 0, false, false, false));
        for (ServerPlayer listener : player.serverLevel().players()) {
            if (listener.distanceToSqr(player) <= SOUND_RADIUS * SOUND_RADIUS) {
                RoleUtils.playSound(listener, NRSounds.TIME_STOP, SoundSource.PLAYERS,
                        player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F);
            }
        }
    }

    private static int randomWait(ServerPlayer player) {
        return MIN_WAIT_TICKS + player.getRandom().nextInt(MAX_WAIT_TICKS - MIN_WAIT_TICKS + 1);
    }

    private static void clear(Player player) {
        NEXT_SHIFT.remove(player);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.removeEffect(ModEffects.UNSTABLE_SHIFT);
        }
    }

    static void init() {
        GameUtilityEvents.BEFORE_KILL.register((victim, body, killer, reason, force) ->
                force || killer == null || killer == victim || (!isShifted(killer) && (victim == null || !isShifted(victim))));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) ->
                GameUtils.isForcedDeathInProgress() || !(source.getEntity() instanceof Player attacker)
                        || attacker == victim || (!isShifted(attacker)
                        && (!(victim instanceof Player other) || !isShifted(other))));
        ResetPlayerEvent.EVENT.register(UnstablePersonModifier::clear);
        ModifierRemoved.EVENT.register((player, modifier) -> {
            if (modifier == NRModifiers.UNSTABLE_PERSON) {
                clear(player);
            }
        });

        var phase = Noellesroles.id("unstable_shift");
        AttackBlockCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(phase, (player, level, hand, pos, side) -> interaction(player));
        AttackEntityCallback.EVENT.register(phase, (player, level, hand, target, hit) ->
                target instanceof Player other && isShifted(other) ? InteractionResult.FAIL : interaction(player));
        UseBlockCallback.EVENT.register(phase, (player, level, hand, hit) -> interaction(player));
        UseEntityCallback.EVENT.register(phase, (player, level, hand, target, hit) ->
                target instanceof Player other && isShifted(other) ? InteractionResult.FAIL : interaction(player));
        UseItemCallback.EVENT.register(phase, (player, level, hand) -> isShifted(player)
                ? InteractionResultHolder.fail(player.getItemInHand(hand))
                : InteractionResultHolder.pass(player.getItemInHand(hand)));
    }

    private static InteractionResult interaction(Player player) {
        return isShifted(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }
}
