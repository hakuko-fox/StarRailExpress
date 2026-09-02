package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to select or cancel the pending role choice. */
public record BackpackRoleChoicePayload(String action, String roleId) implements CustomPacketPayload {
    public static final Type<BackpackRoleChoicePayload> ID = new Type<>(SRE.id("backpack_role_choice"));
    public static final StreamCodec<FriendlyByteBuf, BackpackRoleChoicePayload> CODEC =
            CustomPacketPayload.codec(BackpackRoleChoicePayload::write, BackpackRoleChoicePayload::new);

    private BackpackRoleChoicePayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(16), buffer.readUtf(192));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(action == null ? "" : action, 16);
        buffer.writeUtf(roleId == null ? "" : roleId, 192);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
