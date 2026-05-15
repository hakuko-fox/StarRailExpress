package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record RepairCombatFeedbackS2CPacket(int kind, int entityId, double x, double y, double z)
        implements CustomPacketPayload {
    public static final int ATTACK = 0;
    public static final int HIT = 1;
    public static final int DOWNED = 2;
    public static final int REVIVED = 3;
    public static final Type<RepairCombatFeedbackS2CPacket> ID = new Type<>(Noellesroles.id("repair_combat_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairCombatFeedbackS2CPacket> CODEC = StreamCodec
            .ofMember(RepairCombatFeedbackS2CPacket::encode, RepairCombatFeedbackS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(kind);
        buf.writeVarInt(entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static RepairCombatFeedbackS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairCombatFeedbackS2CPacket(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
