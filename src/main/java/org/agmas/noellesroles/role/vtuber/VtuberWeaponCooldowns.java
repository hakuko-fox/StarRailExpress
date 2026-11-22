package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import java.util.WeakHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.role.ModRoles;

/** 武器冷却的角色规则与分身命中标记。 */
public final class VtuberWeaponCooldowns {
    private static final Map<ServerPlayer, Long> DECOY_HITS = new WeakHashMap<>();
    private VtuberWeaponCooldowns() {}
    public static void hitDecoy(ServerPlayer player) { DECOY_HITS.put(player, player.level().getGameTime()); }
    public static int adjust(ServerPlayer player, Item item, int ticks) {
        if (!VtuberRoleSupport.isWeapon(item)) return ticks;
        Long hitAt = DECOY_HITS.remove(player);
        var game = SREGameWorldComponent.KEY.get(player.level());
        var role = game.getRole(player);
        return org.agmas.noellesroles.game.roles.vtuber.VtuberV21Rules.weaponCooldown(ticks,
                game.isRunning() && role != null ? role.identifier().getPath() : "",
                AyersRole.isEvil(player), hitAt != null && hitAt == player.level().getGameTime());
    }
}
