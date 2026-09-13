package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

public class AyersRole extends NormalRole {
    public AyersRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
    }

    public static void giveInitialItems(Player player) {
        if (!SREItemUtils.hasItem(player, TMMItems.REVOLVER)) {
            RoleUtils.insertOrDropItem(player, TMMItems.REVOLVER.getDefaultInstance());
        }
    }

    public static void tickAyers(ServerPlayer player, SREGameWorldComponent game, long now) {
        if (!game.isRole(player, ModRoles.AYERS)) {
            return;
        }
        UUID id = player.getUUID();
        if (RoleData.getNullable(VtuberRoleData.class, player).ayersFastMode == null) {
            RoleData.getNullable(VtuberRoleData.class, player).ayersFastMode = true;
            RoleData.getNullable(VtuberRoleData.class, player).ayersNextSwitch = now + 20L * 30L;
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    20 * 30 + 5, 0, false, false, true));
            return;
        }
        if (now < RoleData.getNullable(VtuberRoleData.class, player).ayersNextSwitch) {
            return;
        }
        boolean fast = !RoleData.getNullable(VtuberRoleData.class, player).ayersFastMode;
        RoleData.getNullable(VtuberRoleData.class, player).ayersFastMode = fast;
        RoleData.getNullable(VtuberRoleData.class, player).ayersNextSwitch = now + 20L * 30L;
        player.removeEffect(fast ? MobEffects.MOVEMENT_SLOWDOWN : MobEffects.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(fast ? MobEffects.MOVEMENT_SPEED : MobEffects.MOVEMENT_SLOWDOWN,
                20 * 30 + 5, 0, false, false, true));
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        tickAyers(player, game, now);
    }

}
