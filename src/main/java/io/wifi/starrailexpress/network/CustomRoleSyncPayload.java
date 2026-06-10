package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 自定义职业同步 Payload（服务端 → 客户端）
 * 只包含 Payload 定义，接收器在客户端 CustomRoleClientNetwork 中注册
 */
public class CustomRoleSyncPayload implements CustomPacketPayload {
    public static final Type<CustomRoleSyncPayload> TYPE = new Type<>(SRE.id("custom_role_sync"));
    public static final StreamCodec<FriendlyByteBuf, CustomRoleSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CustomRoleSyncPayload::jsonContent,
            CustomRoleSyncPayload::new);

    private final String jsonContent;

    public CustomRoleSyncPayload(String jsonContent) {
        this.jsonContent = jsonContent;
    }

    public String jsonContent() {
        return jsonContent;
    }

    @Override
    public Type<CustomRoleSyncPayload> type() {
        return TYPE;
    }
}
