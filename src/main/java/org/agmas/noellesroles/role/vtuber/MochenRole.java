package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.role.ModRoles;

public class MochenRole extends NormalRole {
    public MochenRole(ResourceLocation id, int color, boolean innocent, boolean killer,
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
        RoleSkill.register(ModRoles.MOCHEN,
                RoleSkill.skill(SRE.id("mochen_time_reversal"), "skill.noellesroles.mochen.time_reversal", context -> {
                    boolean rewound = VtuberRoleRuntime
                    .useRewind(context.player(), 3);
                    if (!rewound) {
                        context.setSkillCooldown(5 * 20);
                    }
                    return rewound;
                })
                .cooldownSeconds(120).showOnHud(true).build());
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        player.removeEffect(MobEffects.BLINDNESS);
    }
}
