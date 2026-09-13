package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;

public class BloodFoxRole extends NormalRole {
    public BloodFoxRole(ResourceLocation id, int color, boolean innocent, boolean killer,
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
        RoleSkill.register(ModRoles.BLOOD_FOX,
                RoleSkill.skill(SRE.id("blood_fox_transform"), "skill.noellesroles.blood_fox.transform", context ->
                BloodFoxRole.toggleBloodFox(context))
                .cooldownSeconds(10).toggleable(true).manualCooldown().showOnHud(true).build());
    }

    public static boolean toggleBloodFox(RoleSkillContext context) {
        ServerPlayer player = context.player();
        VtuberRoleData component = RoleData.getNullable(VtuberRoleData.class, player);
        if (component == null) return false;
        if (component.getDisguise() == VtuberRoleData.BLOOD_FOX) {
            VtuberRoleSupport.leaveAnimalForm(player);
            return true;
        }
        if (!context.skillReady()) {
            return false;
        }
        component.setDisguise(VtuberRoleData.BLOOD_FOX);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1,
                false, false, true));
        return true;
    }

    public static void tickBloodFox(ServerPlayer player, SREGameWorldComponent game) {
        if (!game.isRole(player, ModRoles.BLOOD_FOX)) {
            return;
        }
        long lastConsume = RoleData.getNullable(VtuberRoleData.class, player).bloodFoxLastConsume;

        if (player.level().getGameTime() - lastConsume >= 20L * 90L && player.tickCount % 20 == 0) {
            SREPlayerMoodComponent.KEY.get(player).addMood(-0.0033F);
        }
        VtuberRoleData component = RoleData.getNullable(VtuberRoleData.class, player);
        if (component.getDisguise() != VtuberRoleData.BLOOD_FOX) {
            return;
        }
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
        if (mood.getMood() < 0.5F) {
            VtuberRoleSupport.leaveAnimalForm(player);
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.blood_fox.low_san_exit"), true);
            return;
        }
        if (player.tickCount % 20 == 0) {
            mood.addMood(-0.005F);
            if (mood.getMood() < 0.5F) {
                VtuberRoleSupport.leaveAnimalForm(player);
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.blood_fox.low_san_exit"), true);
            }
        }
    }

    public static boolean allowAnimalFormDeath(Player victim) {
        if (!(victim instanceof ServerPlayer player)) {
            return true;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        VtuberRoleData component = RoleData.getNullable(VtuberRoleData.class, player);
        if (component != null && game.isRole(player, ModRoles.BLOOD_FOX)
                    && component.getDisguise() == VtuberRoleData.BLOOD_FOX) {
            player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.5F));
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.blood_fox.fatal_immune"), true);
            return false;
        }
        return true;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        tickBloodFox(player, game);
        player.removeEffect(MobEffects.BLINDNESS);
    }

}
