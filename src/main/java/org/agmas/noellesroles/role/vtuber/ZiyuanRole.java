package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.Noellesroles;

/** 紫苑：稀有老人角色，开局掷一次自杀概率。 */
public class ZiyuanRole extends NormalRole {
    public ZiyuanRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
        setDefaultEnableChance(100);
    }
    @Override public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        var data = RoleData.getNullable(VtuberRoleData.class, player);
        if (data == null) return;
        long now = player.level().getGameTime();
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(Noellesroles.id("ziyuan_speed"))) speed.addTransientModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(Noellesroles.id("ziyuan_speed"), -0.8D,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (!data.ziyuanRolled) {
            data.ziyuanRolled = true;
            data.ziyuanWillDie = player.getRandom().nextFloat() < 0.8F;
            data.ziyuanDeathAt = now + 180 * 20L;
        }
        if (data.ziyuanWillDie && now >= data.ziyuanDeathAt) GameUtils.forceKillPlayer(player, true, null, Noellesroles.id("ziyuan_old_age"));
    }
    @Override public void onRemove(ServerPlayer player) {
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(Noellesroles.id("ziyuan_speed"));
    }
    @Override public void onFinishQuest(Player player, String quest) {
        super.onFinishQuest(player, quest);
        VtuberTraits.catTaskBonus(player);
    }
}
