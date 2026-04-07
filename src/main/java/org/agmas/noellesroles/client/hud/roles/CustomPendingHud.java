package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;
import io.wifi.starrailexpress.api.SpecialGameModeRoles;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;

/**
 * CUSTOM PENDING HUD Mixin
 * 
 * 显示当前可以打开UI选择职业的信息
 */
public class CustomPendingHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(SpecialGameModeRoles.CUSTOM_PENDING.identifier(),
                (context, deltaTracker) -> {
                    Minecraft client = Minecraft.getInstance();
                    if (SREClient.isPlayerSpectator())
                        return;
                    // "display.type.role."
                    // killer
                    // innocent
                    // neutral
                    // neutral_for_killer
                    // vigilante
                    int roleType = CustomRoleGameModeTeamsPlayerComponent.KEY.get(client.player).getTeam();
                    Component teamName = Component.translatable("Unknown");
                    if (roleType == 1) {
                        teamName = Component.translatable("display.type.role.innocent");
                    } else if (roleType == 2) {
                        teamName = Component.translatable("display.type.role.neutral");
                    } else if (roleType == 3) {
                        teamName = Component.translatable("display.type.role.neutral_for_killer");
                    } else if (roleType == 4) {
                        teamName = Component.translatable("display.type.role.killer");
                    } else if (roleType == 5) {
                        teamName = Component.translatable("display.type.role.vigilante");
                    }
                    // 渲染位置 - 右下角
                    int screenWidth = client.getWindow().getGuiScaledWidth();
                    int screenHeight = client.getWindow().getGuiScaledHeight();
                    int x = screenWidth - 10; // 距离右边缘
                    int y = screenHeight - 10 - 15 * 3; // 距离底部4行

                    Font font = client.font;
                    {
                        Component tip = Component
                                .translatable("hud.custom_pending.keybind",
                                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                                .withStyle(ChatFormatting.GREEN);
                        context.drawString(font, tip, x - font.width(tip), y, 0xFFFFFFFF);
                    }

                    {
                        Component tip = Component
                                .translatable("hud.custom_pending.keybind",
                                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                                .withStyle(ChatFormatting.GREEN);
                        context.drawString(font, tip, x - font.width(tip), y + 15, 0xFFFFFFFF);
                    }

                    {
                        Component tip = Component.translatable("hud.custom_pending.myteam", teamName)
                                .withStyle(ChatFormatting.GREEN);
                        context.drawString(font, tip, x - font.width(tip), y + 15 * 2, 0xFFFFFFFF);
                    }
                    if (client.player.hasEffect(ModEffects.NO_COLLIDE)) {
                        int roleDrawLeft = 0;
                        // 显示技能冷却
                        int cdSeconds = roleDrawLeft / 20;
                        Component cdText = Component.translatable("hud.custom_pending.tip",
                                String.format("%d", cdSeconds));

                        // 红色文字表示抽取间隔
                        context.drawString(font, cdText, x - font.width(cdText), y + 15 * 3, CommonColors.RED);

                        Component tip = Component.translatable("hud.custom_pending.tip_2")
                                .withStyle(ChatFormatting.YELLOW);
                        context.drawString(font, tip, x - font.width(tip), y, 0xFFFFFFFF);
                    }

                });
    }
}