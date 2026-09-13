package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;

public final class VtuberRoleItems {
    private VtuberRoleItems() {}
    public static boolean giveInitialItems(Player player, SRERole role) {
        if (role == ModRoles.HAKUKO_FOX) { HakukoFoxRole.giveInitialItems(player); return true; }
        if (role == ModRoles.EVERLY) { EverlyRole.giveInitialItems(player); return true; }
        if (role == ModRoles.HOSHIZORA) { HoshizoraRole.giveInitialItems(player); return true; }
        if (role == ModRoles.SHENWU_BINGFENG) { ShenwuBingfengRole.giveInitialItems(player); return true; }
        if (role == ModRoles.YOZORA) { YozoraRole.giveInitialItems(player); return true; }
        if (role == ModRoles.XIANMIAO) { XianmiaoRole.giveInitialItems(player); return true; }
        if (role == ModRoles.YUZU_FENGLING) { YuzuFenglingRole.giveInitialItems(player); return true; }
        if (role == ModRoles.JUKA) { JukaRole.giveInitialItems(player); return true; }
        if (role == ModRoles.BAIYU) { BaiyuRole.giveInitialItems(player); return true; }
        if (role == ModRoles.AYERS) { AyersRole.giveInitialItems(player); return true; }
        return false;
    }
}
