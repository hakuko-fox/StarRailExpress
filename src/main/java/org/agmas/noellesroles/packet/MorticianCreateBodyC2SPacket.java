package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public class MorticianCreateBodyC2SPacket implements CustomPacketPayload {

    public static final Type<MorticianCreateBodyC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mortician_create_body"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorticianCreateBodyC2SPacket> CODEC = StreamCodec.composite(
            ByteCodecs.UUID,
            MorticianCreateBodyC2SPacket::targetUuid,
            ByteCodecs.STRING_UTF8,
            MorticianCreateBodyC2SPacket::deathReason,
            ByteCodecs.STRING_UTF8,
            MorticianCreateBodyC2SPacket::roleId,
            MorticianCreateBodyC2SPacket::new
    );

    private final UUID targetUuid;
    private final String deathReason;
    private final String roleId;

    public MorticianCreateBodyC2SPacket(UUID targetUuid, String deathReason, String roleId) {
        this.targetUuid = targetUuid;
        this.deathReason = deathReason;
        this.roleId = roleId;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String deathReason() {
        return deathReason;
    }

    public String roleId() {
        return roleId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
