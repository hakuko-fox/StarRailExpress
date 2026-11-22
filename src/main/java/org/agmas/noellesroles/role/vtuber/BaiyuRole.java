package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

public class BaiyuRole extends NormalRole {
    public BaiyuRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.BAIYU,
                RoleSkill.skill(SRE.id("baiyu_record"), "skill.noellesroles.baiyu.record", context ->
                BaiyuRole.useBaiyuExamine(context.player()))
                .cooldownSeconds(30).showOnHud(true).build());
    }

    public static void giveInitialItems(Player player) {
        if (!SREItemUtils.hasItem(player, TMMItems.REVOLVER)) {
            RoleUtils.insertOrDropItem(player, TMMItems.REVOLVER.getDefaultInstance());
        }
    }

    public static boolean useBaiyuExamine(ServerPlayer player) {
        if (RoleData.getNullable(VtuberRoleData.class, player) == null) return false;
        var hit = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof PlayerBodyEntity, 5.0F);
        if (!(hit instanceof EntityHitResult entityHit)
                    || !(entityHit.getEntity() instanceof PlayerBodyEntity body)) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.baiyu.no_target"), true);
            return false;
        }
        body.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false, false));
        var role = body.getComponent().playerRole;
        Component roleText = org.agmas.noellesroles.utils.RoleUtils.getRoleName(role);
        Component reasonText = Component.translatable("death_reason." + body.getDeathReason().replace(':', '.'));
        if (body.hasEffect(org.agmas.noellesroles.init.ModEffects.FOX_BODY_HIDDEN)) {
            roleText = Component.translatable("message.noellesroles.vtuber.fox_hidden");
            reasonText = roleText;
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.baiyu.corpse_result",
                body.getComponent().getOwnerName(), roleText, reasonText), false);
        return true;
    }

    public static void displayBaiyuDeathReason(ServerPlayer player, PlayerBodyEntity body) {
        String reason = body.getDeathReason();
        Component reasonText = reason == null || reason.isBlank()
                ? Component.translatable("message.death_reason.null")
                : Component.translatable("death_reason." + reason.replace(':', '.'));
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.baiyu.result", reasonText), false);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
    }
}
