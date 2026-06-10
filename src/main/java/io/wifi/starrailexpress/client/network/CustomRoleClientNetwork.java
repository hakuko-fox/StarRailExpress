package io.wifi.starrailexpress.client.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.customrole.CustomRoleData;
import io.wifi.starrailexpress.network.CustomRoleSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义职业客户端网络处理
 * 接收服务端发来的自定义职业 JSON 配置，解析后存储在内存中
 */
public class CustomRoleClientNetwork {

    private static final Gson GSON = new GsonBuilder().create();

    /** 客户端内存中缓存的原始 JSON 字符串 */
    private static String syncedJson = null;

    /** 客户端内存中缓存的已解析角色数据列表 */
    private static final List<CustomRoleData> syncedRoles = new ArrayList<>();

    /**
     * 注册客户端接收器
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CustomRoleSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                String json = payload.jsonContent();
                if (json != null && !json.isEmpty()) {
                    syncedJson = json;
                    syncedRoles.clear();
                    try {
                        // 解析 JSON 并存入内存
                        com.google.gson.JsonObject root = GSON.fromJson(json, com.google.gson.JsonObject.class);
                        if (root.has("roles")) {
                            for (var element : root.getAsJsonArray("roles")) {
                                CustomRoleData data = GSON.fromJson(element, CustomRoleData.class);
                                if (data != null && data.englishId != null) {
                                    syncedRoles.add(data);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        });
    }

    /** 获取同步过来的原始 JSON 字符串 */
    public static String getSyncedJson() {
        return syncedJson;
    }

    /** 获取同步过来的已解析角色数据 */
    public static List<CustomRoleData> getSyncedRoles() {
        return new ArrayList<>(syncedRoles);
    }

    /** 根据 englishId 查找角色数据 */
    public static CustomRoleData getSyncedRole(String englishId) {
        for (CustomRoleData data : syncedRoles) {
            if (data.englishId.equals(englishId)) {
                return data;
            }
        }
        return null;
    }

    /** 清理缓存（离开服务器时） */
    public static void clearCache() {
        syncedJson = null;
        syncedRoles.clear();
    }
}
