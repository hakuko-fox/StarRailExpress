package net.exmo.sre.mod_whitelist.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/**
 * C2S：JAR 密钥认证应答。
 *
 * @param hasKey   客户端 jar 是否带有嵌入密钥（未签名 jar 直接如实上报）
 * @param response HMAC-SHA256(key, digest|nonce|version) 的 hex；无密钥时为空串
 * @param version  客户端模组版本（须与服务端一致）
 */
public record JarAuthResponsePayload(boolean hasKey, String response, String version)
        implements CustomPacketPayload {

    public static final Type<JarAuthResponsePayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "jar_auth_response"));

    public static final StreamCodec<FriendlyByteBuf, JarAuthResponsePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, JarAuthResponsePayload::hasKey,
            ByteBufCodecs.STRING_UTF8, JarAuthResponsePayload::response,
            ByteBufCodecs.STRING_UTF8, JarAuthResponsePayload::version,
            JarAuthResponsePayload::new);

    @Override
    public Type<JarAuthResponsePayload> type() {
        return ID;
    }
}
