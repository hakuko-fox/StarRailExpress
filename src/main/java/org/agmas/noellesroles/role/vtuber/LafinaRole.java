package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.LafinaRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;

public class LafinaRole extends NormalRole {
    public LafinaRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(LafinaRoleData::new);
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.LAFINA,
                RoleSkill.skill(SRE.id("lavanaii_bear_charge"), "skill.noellesroles.lavanaii.bear_charge", context -> {
                    ServerPlayer player = context.player();
                    if (RoleData.getNullable(LafinaRoleData.class, player) == null) return false;
                    int balance = MoneyUtils.getBalance(player);
                    if (balance < 150) {
                        player.displayClientMessage(Component.translatable("message.noellesroles.lavanaii.not_enough_coins"), true);
                        return false;
                    }
                    MoneyUtils.addToBalance(player, -150);
                    beginLafinaCharge(player);
                    return true;
                }).cooldownSeconds(70).showOnHud(true).build());
    }

    public static void beginLafinaCharge(ServerPlayer player) {
        Vec3 direction = player.getLookAngle().multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < 0.01D) {
            direction = new Vec3(0, 0, 1);
        }
        RoleData.getNullable(LafinaRoleData.class, player).charge = new LafinaCharge(direction, new java.util.HashSet<>());
    }

    public static boolean isLafinaCharging(Player player) {
        return player != null && RoleData.getOptional(LafinaRoleData.class, player).map(data -> data.charge != null).orElse(false);
    }

    private static void tickLafinaCharge(ServerPlayer player) {
        var data = RoleData.getNullable(LafinaRoleData.class, player);
        LafinaCharge charge = data == null ? null : data.charge;
        if (charge == null) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player) || player.horizontalCollision) {
            data.charge = null;
            player.setDeltaMovement(Vec3.ZERO);
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
            return;
        }
        Vec3 direction = charge.direction();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5, 2,
                false, false, true));
        player.setDeltaMovement(direction.scale(1.15D).add(0, player.getDeltaMovement().y, 0));
        player.hurtMarked = true;
        for (Player target : player.level().players()) {
            if (target == player || !GameUtils.isPlayerAliveAndSurvival(target) || target.distanceToSqr(player) > 3.0D
                    || !charge.hitPlayers().add(target.getUUID())) {
                continue;
            }
            target.push(direction.x * 1.3D, 0.35D, direction.z * 1.3D);
            if (target instanceof ServerPlayer serverTarget) {
                serverTarget.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(serverTarget));
            }
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                    3 * 20, 0, false, false, true));
        }
    }

    public record LafinaCharge(Vec3 direction, java.util.Set<UUID> hitPlayers) {}

    @Override public void serverTick(ServerPlayer player) { tickLafinaCharge(player); }

    public static void registerEvents() {
        AllowPlayerDeath.EVENT.register((player, reason) -> !isLafinaCharging(player));
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, reason) -> !isLafinaCharging(player));
    }
}
