package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 自定义职业服务端网络处理
 * 负责在服务端启动和玩家加入时将自定义职业 JSON 配置发送给客户端
 */
public class CustomRoleServerNetwork {

    private static String cachedJsonContent = null;

    /**
     * 从当前存档读取 sre_custom_roles.json 并发送给所有玩家
     */
    public static void syncToAllPlayers(MinecraftServer server) {
        String json = readConfigJson(server);
        if (json == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new CustomRoleSyncPayload(json));
        }
    }

    /**
     * 发送给单个玩家（玩家加入时调用）
     */
    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        String json = readConfigJson(server);
        if (json == null) return;

        ServerPlayNetworking.send(player, new CustomRoleSyncPayload(json));
    }

    /**
     * 读取服务端存档中的 sre_custom_roles.json 文件内容
     * 带缓存，文件变更后重载时清除缓存
     */
    private static String readConfigJson(MinecraftServer server) {
        if (cachedJsonContent != null) {
            return cachedJsonContent;
        }
        try {
            Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path configPath = worldPath.resolve("sre_custom_roles.json");
            if (Files.exists(configPath)) {
                cachedJsonContent = Files.readString(configPath, StandardCharsets.UTF_8);
                return cachedJsonContent;
            }
        } catch (IOException e) {
            SRE.LOGGER.error("[CustomRole] Failed to read sre_custom_roles.json for sync", e);
        }
        // 没有自定义职业配置，不发空包
        return null;
    }

    /**
     * 清除缓存（重载角色配置后调用）
     */
    public static void clearCache() {
        cachedJsonContent = null;
    }
}
