package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;

public record RepairRolePurchaseC2SPacket(String roleId) implements CustomPacketPayload {
    public static final Type<RepairRolePurchaseC2SPacket> ID = new Type<>(Noellesroles.id("repair_role_purchase"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairRolePurchaseC2SPacket> CODEC = StreamCodec
            .ofMember(RepairRolePurchaseC2SPacket::encode, RepairRolePurchaseC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(roleId);
    }

    public static RepairRolePurchaseC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairRolePurchaseC2SPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairRolePurchaseC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        var component = ModComponents.REPAIR_ROLES.get(player);
        var shop = SREPlayerShopComponent.KEY.get(player);
        RepairRoleDefinition.byId(payload.roleId()).ifPresent(role -> {
            if (component.owns(role)) {
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_already_owned").withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            if (shop.balance < RepairRoleDefinition.UNLOCK_PRICE) {
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_not_enough_money", RepairRoleDefinition.UNLOCK_PRICE).withStyle(ChatFormatting.RED), true);
                return;
            }
            shop.addToBalance(-RepairRoleDefinition.UNLOCK_PRICE);
            component.unlock(role);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_purchased", role.displayName()).withStyle(ChatFormatting.GREEN), true);
        });
    }
}
