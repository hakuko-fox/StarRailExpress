package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.game.modes.repair.RepairEventSystem;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;

public record RepairStationActionC2SPacket(BlockPos blockPos, boolean greatHit) implements CustomPacketPayload {
    public static final Type<RepairStationActionC2SPacket> ID = new Type<>(Noellesroles.id("repair_station_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairStationActionC2SPacket> CODEC = StreamCodec
            .ofMember(RepairStationActionC2SPacket::encode, RepairStationActionC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeBoolean(greatHit);
    }

    public static RepairStationActionC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairStationActionC2SPacket(buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairStationActionC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (player.distanceToSqr(payload.blockPos().getCenter()) > 36.0D) {
            return;
        }
        if (player.level().getBlockEntity(payload.blockPos()) instanceof RepairStationBlockEntity station) {
            String activeRole = ModComponents.REPAIR_ROLES.get(player).activeRole;
            int amount = payload.greatHit() ? 6 : 3;
            if ("mechanic".equals(activeRole)) {
                amount += payload.greatHit() ? 3 : 2;
            }
            amount += RepairEventSystem.repairProgressBonus((net.minecraft.server.level.ServerLevel) player.level());
            if (station.addProgress(amount)) {
                int reward = (payload.greatHit() ? 8 : 3)
                        + RepairEventSystem.repairCoinBonus((net.minecraft.server.level.ServerLevel) player.level());
                if (station.isCompleted()) {
                    reward += 75;
                }
                RepairModeState.awardCoins(player, reward, payload.greatHit() ? "repair_coin_source.perfect" : "repair_coin_source.calibration");
            if (station.addProgress(amount)) {
                int reward = payload.greatHit() ? 8 : 3;
                if (station.isCompleted()) {
                    reward += 75;
                }
                SREPlayerShopComponent.KEY.get(player).addToBalance(reward);
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.coin_reward", reward).withStyle(ChatFormatting.GOLD), true);
                if (station.isJammed()) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.repair.station_jammed_hint").withStyle(ChatFormatting.RED), true);
                }
                RepairGameplayEffects.burst((net.minecraft.server.level.ServerLevel) player.level(), payload.blockPos().getX() + 0.5D, payload.blockPos().getY() + 0.8D, payload.blockPos().getZ() + 0.5D, payload.greatHit() ? 2 : 0);
                RepairModeState.addNeutralTaskProgress(player, "archivist", 1, 5);
                RepairModeState.addNeutralTaskProgress(player, "saboteur", payload.greatHit() ? 2 : 1, 6);
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.progress",
                        station.getProgress()).withStyle(payload.greatHit() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
            }
        }
    }
}
