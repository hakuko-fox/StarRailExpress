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

/** 语殇：近身社恐与睡眠理智管理。 */
public class YushangRole extends NormalRole {
    public YushangRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);

    }
    @Override public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        var data = RoleData.getNullable(VtuberRoleData.class, player);
        if (data == null) return;
        long now = player.level().getGameTime();
        boolean nearby = player.serverLevel().players().stream().anyMatch(other -> other != player
                && GameUtils.isPlayerAliveAndSurvival(other) && player.distanceToSqr(other) <= 4.0D);
        if (nearby) for (var effect : java.util.List.of(ModEffects.TURN_BANED, ModEffects.VOICE_SILENCE))
                player.addEffect(new MobEffectInstance(effect, 2, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.TRUE_SKIN_OBSERVER, 5, 0, false, false, false));
        if (player.isSleeping()) data.sleepDeadline = now + 60 * 20L;
        if (player.tickCount % 20 == 0) {
            if (player.isSleeping()) SREPlayerMoodComponent.KEY.get(player).addMood(0.02F);
            else if (now >= data.sleepDeadline) SREPlayerMoodComponent.KEY.get(player).addMood(-0.01F);
        }
    }

}
