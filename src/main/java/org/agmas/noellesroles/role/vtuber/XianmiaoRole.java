package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

public class XianmiaoRole extends NormalRole {
    public XianmiaoRole(ResourceLocation id, int color, boolean innocent, boolean killer,
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
        io.wifi.starrailexpress.api.RoleSkill.register(org.agmas.noellesroles.role.ModRoles.XIANMIAO,
                io.wifi.starrailexpress.api.RoleSkill.skill(io.wifi.starrailexpress.SRE.id("xianmiao_vanish"),
                        "skill.noellesroles.xianmiao.vanish", context -> {
                            ServerPlayer player = context.player();
                            if (!VtuberRoleSupport.deduct(player, 75)) return false;
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.INVISIBILITY, 7 * 20, 0, false, false, true));
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(org.agmas.noellesroles.init.ModEffects.VOICE_SILENCE, 7 * 20, 0, false, false, true));
                            return true;
                        }).cooldownSeconds(90).showOnHud(true).build());
    }

    public static void giveInitialItems(Player player) {
        RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_REVOLVER.getDefaultInstance());

    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        VtuberRoleSupport.tickUncontrolledAlliance(player, game, now);
    }

    @Override public void onFinishQuest(Player player, String quest) {
        super.onFinishQuest(player, quest);
        VtuberTraits.catTaskBonus(player);
    }
}
