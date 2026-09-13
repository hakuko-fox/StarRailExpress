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

public class YoruRole extends NormalRole {
    public YoruRole(ResourceLocation id, int color, boolean innocent, boolean killer,
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
        RoleSkill.register(ModRoles.YORU, pairSkill);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        player.removeEffect(MobEffects.BLINDNESS);
    }
}
