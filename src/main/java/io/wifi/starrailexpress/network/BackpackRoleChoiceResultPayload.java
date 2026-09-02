package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server result for a role choice request. */
public record BackpackRoleChoiceResultPayload(boolean success, String messageKey, String roleId)
        implements CustomPacketPayload {
    public static final Type<BackpackRoleChoiceResultPayload> ID =
            new Type<>(SRE.id("backpack_role_choice_result"));
    public static final StreamCodec<FriendlyByteBuf, BackpackRoleChoiceResultPayload> CODEC =
            CustomPacketPayload.codec(BackpackRoleChoiceResultPayload::write,
                    BackpackRoleChoiceResultPayload::new);

    private BackpackRoleChoiceResultPayload(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readUtf(192), buffer.readUtf(192));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(success);
        buffer.writeUtf(messageKey == null ? "" : messageKey, 192);
        buffer.writeUtf(roleId == null ? "" : roleId, 192);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
