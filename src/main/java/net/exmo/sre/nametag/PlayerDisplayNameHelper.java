/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.nametag;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

public final class PlayerDisplayNameHelper {
    private static final String OFFLINE_NICKNAMES_MOD_ID = "nickname";

    private PlayerDisplayNameHelper() {
    }

    public static Component compose(Player player, Component fallbackName) {
        Component nickname = player instanceof ServerPlayer serverPlayer
                ? getOfflineNickname(serverPlayer)
                : null;
        Component baseName = nickname != null ? nickname : fallbackName;
        MutableComponent title = NameTagInventoryComponent.KEY.get(player).generate();
        if (title == null) {
            return baseName;
        }
        return Component.literal("[")
                .append(title.copy())
                .append("] ")
                .append(baseName.copy());
    }

    public static Component composeForTabList(ServerPlayer player, Component fallbackName) {
        Component nickname = getOfflineNickname(player);
        MutableComponent title = NameTagInventoryComponent.KEY.get(player).generate();
        Component composedName = fallbackName;
        if (nickname != null || title != null) {
            Component baseName = nickname != null
                    ? nickname
                    : fallbackName != null ? fallbackName : player.getName();
            composedName = title == null
                    ? baseName
                    : Component.literal("[")
                            .append(title.copy())
                            .append("] ")
                            .append(baseName.copy());
        }

        if (!player.isSpectator()) {
            return composedName;
        }

        Component baseName = composedName != null ? composedName : player.getName();
        return Component.empty()
                .append(Component.translatable("starrailexpress.tag.spectator"))
                .append(" ")
                .append(baseName.copy());
    }

    public static void syncTabListDisplayName(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                List.of(player));
        player.getServer().getPlayerList().broadcastAll(packet);
    }

    private static Component getOfflineNickname(ServerPlayer player) {
        if (!FabricLoader.getInstance().isModLoaded(OFFLINE_NICKNAMES_MOD_ID) || !player.hasCustomName()) {
            return null;
        }
        Component customName = player.getCustomName();
        return customName == null ? null : customName.copy();
    }
}
