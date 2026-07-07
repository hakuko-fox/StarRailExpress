package net.exmo.sre.mod_whitelist.client.network;

import net.exmo.sre.mod_whitelist.ModWhitelist;
import net.exmo.sre.mod_whitelist.common.auth.JarAuthCore;
import net.exmo.sre.mod_whitelist.common.network.JarAuthChallengePayload;
import net.exmo.sre.mod_whitelist.common.network.JarAuthResponsePayload;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.concurrent.CompletableFuture;

/**
 * JAR 密钥认证（客户端）：收到服务端挑战后，在后台线程计算自身 jar 摘要与
 * HMAC 应答（首次计算需要遍历 jar，避免卡渲染线程），随后回传。
 * 未签名的 jar 会如实上报 hasKey=false，由服务端决定是否放行。
 */
@Environment(EnvType.CLIENT)
public final class JarAuthClientHandler {

    private JarAuthClientHandler() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(JarAuthChallengePayload.ID, (payload, context) -> {
            String nonce = payload.nonce();
            CompletableFuture.runAsync(() -> {
                String response = JarAuthCore.computeResponse(nonce, ModWhitelist.MOD_VERSION);
                boolean hasKey = JarAuthCore.hasKey() && response != null;
                JarAuthResponsePayload reply = new JarAuthResponsePayload(
                        hasKey, hasKey ? response : "", ModWhitelist.MOD_VERSION);
                context.client().execute(() -> {
                    if (ClientPlayNetworking.canSend(JarAuthResponsePayload.ID)) {
                        ClientPlayNetworking.send(reply);
                    }
                });
            }).exceptionally(e -> {
                MWLogger.LOGGER.error("[JarAuth] Failed to answer challenge", e);
                return null;
            });
        });
    }
}
