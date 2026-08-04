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

package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ModVersionPacket(String version) implements CustomPacketPayload {
    public static final Type<ModVersionPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "mod_version_check"));
    public static final StreamCodec<FriendlyByteBuf, ModVersionPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUtf(packet.version());
            },
            buf -> {
                return new ModVersionPacket(buf.readUtf());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<ModVersionPacket> {
        @Override
        public void receive(ModVersionPacket payload, ServerPlayNetworking.Context context) {
            ServerPlayer player = context.player();
            String clientVersion = payload.version();
            SRE.LOGGER.info("Server received response from client with mod version {}.", clientVersion);

            if (clientVersion != null && !SRE.modPacketVersion.equals(clientVersion)) {
                SRE.LOGGER.info(
                        "Server received response. But client's version {} doesn't match server's version {}",
                        clientVersion, SRE.modPacketVersion);
                player.connection.disconnect(Component.translatableWithFallback("message.kick.outdated_client",
                        "Version mismatch! Server version %s, your version %s.",
                        SRE.modPacketVersion, clientVersion));
            }
        }
    }
}