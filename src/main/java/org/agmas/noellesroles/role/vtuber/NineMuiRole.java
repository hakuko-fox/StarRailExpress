package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.NineMuiRoleData;

public class NineMuiRole extends NormalRole {
    public NineMuiRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.NINE_MUI,
                RoleSkill.skill(SRE.id("9muimui_blessing"), "skill.noellesroles.9muimui.blessing", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator() || RoleData.getNullable(NineMuiRoleData.class, player) == null) return false;
                    return RoleData.getNullable(NineMuiRoleData.class, player)
                    .useBlessingSkill(player, context);
                }).cooldownSeconds(90).showOnHud(true).build());
    }
}
