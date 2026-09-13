package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.role.ModRoles;

public class TinalisRole extends NormalRole {
    public TinalisRole(ResourceLocation id, int color, boolean innocent, boolean killer,
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
        RoleSkill.register(ModRoles.TINALIS,
                RoleSkill.skill(SRE.id("tinalis_attract"), "skill.noellesroles.tinalis.attract", context ->
                TinalisRole.useTinalisAttract(context.player()))
                .cooldownSeconds(90).showOnHud(true).build());
    }

    public static boolean useTinalisAttract(ServerPlayer caster) {
        if (!VtuberRoleSupport.deduct(caster, 100)) {
            return false;
        }
        VtuberRoleRuntime.applyForcedMovement(caster, true, 20 * 5);
        return true;
    }
}
