package net.exmo.sre.mod_whitelist.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/**
 * S2C：JAR 密钥认证挑战。服务端在玩家加入时下发一次性随机 nonce，
 * 客户端须用嵌入密钥 + 自身 jar 摘要计算 HMAC 应答（见 JarAuthCore）。
 */
public record JarAuthChallengePayload(String nonce) implements CustomPacketPayload {

    public static final Type<JarAuthChallengePayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "jar_auth_challenge"));

    public static final StreamCodec<FriendlyByteBuf, JarAuthChallengePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, JarAuthChallengePayload::nonce,
            JarAuthChallengePayload::new);

    @Override
    public Type<JarAuthChallengePayload> type() {
        return ID;
    }
}
