package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;

/**
 * 通用自定义职业 HUD
 */
public class CustomRoleHud {

    public static void registerForRole(CustomRoleData data, net.minecraft.resources.ResourceLocation roleId) {
        final boolean hasAbility = data.enableAbility;
        final String englishId = data.englishId;
        if (!hasAbility) return;

        RoleHudRenderCallback.EVENT.register(roleId, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(client.player);
            int cooldownTicks = ability.cooldown;
            var font = client.font;

            // 渲染在屏幕中央偏下位置，确保可见
            int screenWidth = guiGraphics.guiWidth();
            int textY = guiGraphics.guiHeight() / 2 + 40;

            if (cooldownTicks <= 0) {
                Component readyText = Component.translatable("hud.sre.custom_role.skill_ready");
                guiGraphics.drawString(font, readyText, (screenWidth - font.width(readyText)) / 2, textY, 0x55FF55);
            } else {
                double seconds = cooldownTicks / 20.0;
                Component cooldownText = Component.translatable("hud.sre.custom_role.skill_cooldown",
                    String.format("%.1f", seconds));
                guiGraphics.drawString(font, cooldownText, (screenWidth - font.width(cooldownText)) / 2, textY, 0xFF5555);
            }
        });
    }

    /** 从配置中注册所有自定义角色的HUD */
    public static void registerAllFromConfig() {
        CustomRoleConfig config = CustomRoleConfig.loadFromDefaultPath();
        for (CustomRoleData data : config.roles) {
            if (data.enableAbility) {
                net.minecraft.resources.ResourceLocation roleId =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("customrole", data.englishId);
                // 直接传入 data 对象，避免每帧读文件
                registerForRole(data, roleId);
            }
        }
    }
}
