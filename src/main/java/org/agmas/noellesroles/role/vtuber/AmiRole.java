package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;

public class AmiRole extends NormalRole {
    public AmiRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
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
        RoleSkill.register(ModRoles.AMI,
                RoleSkill.skill(SRE.id("amimi_repel"), "skill.noellesroles.amimi.repel", context ->
                AmiRole.useAmiRepel(context.player()))
                .cooldownSeconds(90).showOnHud(true).build(),
                RoleSkill.skill(SRE.id("amimi_alcohol_life"), "skill.noellesroles.amimi.alcohol_life", context ->
                AmiRole.useAmiTrap(context.player()))
                .shifted(true).cooldownSeconds(10).showOnHud(true).build());
    }

    public static boolean useAmiRepel(ServerPlayer caster) {
        if (!VtuberRoleSupport.deduct(caster, 100)) {
            return false;
        }
        VtuberRoleRuntime.applyForcedMovement(caster, false, 20 * 3);
        return true;
    }

    public static boolean useAmiTrap(ServerPlayer player) {
        return VtuberRoleRuntime.useFoodTrap(player);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        VtuberRoleSupport.tickPasserby(player, game);
        player.removeEffect(MobEffects.BLINDNESS);
    }

}
