package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.vtuber.VtuberV21Rules;
import org.agmas.noellesroles.role_data.vtuber.LafinaRoleData;

/** 北极熊：只改变显示尺寸，步行75%，疾跑200%，致命攻击延后结算。 */
public class LafinaRole extends NormalRole {
    public static final ResourceLocation SPEED_ID = Noellesroles.id("lafina_speed");
    public LafinaRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(LafinaRoleData::new);
    }
    @Override public boolean afterShieldAllowDeath(Player victim, Player killer, ResourceLocation reason, boolean spawnBody) {
        var data = RoleData.getNullable(LafinaRoleData.class, victim);
        if (data == null || GameUtils.isForcedDeathInProgress()) return true;
        String path = reason.getPath();
        boolean gun = path.contains("shot") || path.contains("bullet") || path.contains("gun") || path.contains("revolver") || path.contains("sniper");
        boolean knife = path.contains("knife");
        if (!gun && !knife) return true;
        long deadline = VtuberV21Rules.fatalDeadline(data.deathAt, GameUtils.getTicksFromGameStart(victim.level()), gun);
        if (data.deathAt == 0 || deadline < data.deathAt) {
            data.deathAt = deadline;
            data.killer = killer == null ? null : killer.getUUID();
            data.reason = reason;
        }
        victim.setHealth(Math.max(1.0F, victim.getHealth()));
        return false;
    }
    @Override public void serverTick(ServerPlayer player) {
        var data = RoleData.getNullable(LafinaRoleData.class, player);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(player)) return;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double amount = player.isSprinting() ? 1.0D : -0.25D;
            var existing = speed.getModifier(SPEED_ID);
            if (existing == null || existing.amount() != amount) {
                speed.removeModifier(SPEED_ID);
                speed.addTransientModifier(new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if (data.deathAt > 0 && GameUtils.getTicksFromGameStart(player.level()) >= data.deathAt) {
            var killer = data.killer == null ? null : player.getServer().getPlayerList().getPlayer(data.killer);
            data.deathAt = 0;
            GameUtils.forceKillPlayer(player, true, killer, data.reason);
        }
    }
    @Override public void onDeath(Player victim, boolean spawnBody, Player killer, ResourceLocation reason, boolean forced) {
        var data = RoleData.getNullable(LafinaRoleData.class, victim);
        if (data != null) data.deathAt = 0;
    }
    public static boolean isLafinaCharging(Player player) { return false; }
    public static void registerSkills() {}
    public static void registerEvents() {}
}
