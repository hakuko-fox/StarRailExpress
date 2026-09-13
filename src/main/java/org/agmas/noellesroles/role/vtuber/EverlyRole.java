package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.agmas.noellesroles.utils.RoleUtils;

public class EverlyRole extends NormalRole {
    public EverlyRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static boolean useTimeStop(ServerPlayer sp, RoleSkillContext ctx) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        int balance = MoneyUtils.getBalance(sp);
        if (balance < 150) {
            sp.displayClientMessage(Component.translatable(
                    "message.noellesroles.vtuber.not_enough_coins", 150), true);
            return false;
        }
        boolean ok = TimeStopEffect.tryTriggerStart(sp, 5 * 20,
                Component.translatable("skill.noellesroles.everly.timestop"));
        if (!ok) {
            return false;
        }
        MoneyUtils.addToBalance(sp, -150);
        return true;
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.EVERLY,
                RoleSkill.skill(SRE.id("everly_timestop"), "skill.noellesroles.everly.timestop", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return false;
                    return EverlyRole.useTimeStop(player, context);
                }).charges(1).showOnHud(true).build(),
                RoleSkill.skill(SRE.id("everly_time_reversal"), "skill.noellesroles.everly.time_reversal", context -> {
                    return VtuberRoleRuntime
                    .useRewind(context.player(), 5, 150);
                }).charges(1).shifted(true).showOnHud(true).build());
    }

    public static void giveInitialItems(Player player) {
        if (!SREItemUtils.hasItem(player, TMMItems.REVOLVER)) {
            RoleUtils.insertOrDropItem(player, TMMItems.REVOLVER.getDefaultInstance());
        }
    }
}
