package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.role.ModRoles;

public class LunaRole extends NormalRole {
    public LunaRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public boolean onUseGun(Player player) {
        return !VtuberRoleRuntime.isWeaponBlocked(player);
    }

    @Override
    public boolean onUseKnife(Player player) {
        return !VtuberRoleRuntime.isWeaponBlocked(player);
    }

    public static void registerSkills() {
        RoleSkill.Definition pairSkill = RoleSkill.skill(SRE.id("luna_yoru_pair"),
                "skill.noellesroles.luna_yoru.pair", context ->
                LunaRole.usePairSkill(context.player()))
                .charges(1).showOnHud(true).build();
        RoleSkill.register(ModRoles.LUNA, pairSkill);
    }

    public static boolean usePairSkill(ServerPlayer caster) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(caster.level());
        var counterpartRole = game.isRole(caster, ModRoles.LUNA) ? ModRoles.YORU : ModRoles.LUNA;
        ServerPlayer counterpart = caster.serverLevel().players().stream()
                .filter(player -> GameUtils.isPlayerAliveAndSurvival(player) && game.isRole(player, counterpartRole))
                .findFirst().orElse(null);
        if (counterpart == null) {
            caster.displayClientMessage(Component.translatable("message.noellesroles.luna_yoru.missing_pair"), true);
            return false;
        }
        boolean returnToRooms = caster.distanceToSqr(counterpart) <= 9.0D;
        if (returnToRooms && (!GameUtils.roomToPlayer.containsKey(caster.getUUID())
                    || !GameUtils.roomToPlayer.containsKey(counterpart.getUUID()))) {
            caster.displayClientMessage(Component.translatable("message.noellesroles.luna_yoru.missing_room"), true);
            return false;
        }
        if (!VtuberRoleSupport.deduct(caster, 200)) {
            return false;
        }
        if (!returnToRooms) {
            Vec3 side = caster.getLookAngle().cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize().scale(0.8D);
            counterpart.teleportTo(caster.serverLevel(), caster.getX() + side.x, caster.getY(),
                    caster.getZ() + side.z, Set.of(), counterpart.getYRot(), counterpart.getXRot());
        } else {
            GameUtils.teleportBackToRoom(caster);
            GameUtils.teleportBackToRoom(counterpart);
        }
        return true;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        player.removeEffect(MobEffects.BLINDNESS);
    }
}
