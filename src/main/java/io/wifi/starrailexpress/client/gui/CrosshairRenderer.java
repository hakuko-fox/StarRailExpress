package io.wifi.starrailexpress.client.gui;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
// import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

// import net.minecraft.world.item.ItemCooldowns;
// import net.minecraft.world.item.ItemStack;
// import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public class CrosshairRenderer {
    private static final ResourceLocation CROSSHAIR = SRE.watheId("hud/crosshair");
    // private static final ResourceLocation KNIFE_ATTACK =
    // SRE.watheId("hud/knife_attack");
    // private static final ResourceLocation KNIFE_PROGRESS =
    // SRE.watheId("hud/knife_progress");
    // private static final ResourceLocation KNIFE_BACKGROUND =
    // SRE.watheId("hud/knife_background");
    // private static final ResourceLocation BAT_ATTACK =
    // SRE.watheId("hud/bat_attack");
    // private static final ResourceLocation BAT_PROGRESS =
    // SRE.watheId("hud/bat_progress");
    // private static final ResourceLocation BAT_BACKGROUND =
    // SRE.watheId("hud/bat_background");

    public static void renderCrosshair(@NotNull Minecraft client, @NotNull LocalPlayer player, GuiGraphics context,
            DeltaTracker tickCounter) {
        if (!client.options.getCameraType().isFirstPerson())
            return;
        RenderSystem.enableBlend();

        context.pose().pushPose();
        {
            RenderSystem.blendFuncSeparate(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ONE_MINUS_SRC_COLOR,
                    SourceFactor.ONE, DestFactor.ZERO);
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f - 1.5f, context.guiHeight() / 2f - 1.5f, 0);
            context.blitSprite(CROSSHAIR, 0, 0, 3, 3);
            context.pose().popPose();
            {
                // --- 2. 攻击指示器（手绘） ---
                float f = player.getAttackStrengthScale(0.0F);
                boolean fullAttack = false;
                if (f >= 1.0F) {
                    fullAttack = player.getCurrentItemAttackStrengthDelay() > 5.0F;
                }

                // 指示器的位置（与原版一致）
                int barX = context.guiWidth() / 2 - 8;
                int barY = context.guiHeight() / 2 - 7 + 16;
                int barWidth = 16;
                int barHeight = 4;

                // 2.1 绘制背景条（半透明白色，透明度 30%）
                int bgColor = 0x4CFFFFFF; // ARGB: 30% 白色
                context.fill(barX, barY, barX + barWidth, barY + barHeight, bgColor);
                if (f >= 1.0F) {
                    // 2.2 根据状态绘制进度
                    if (fullAttack) {
                        // // 满攻击：绘制完整白色条（或自定义颜色）
                        // int fullColor = 0xCC000000; // 纯白
                        // context.fill(barX, barY, barX + barWidth, barY + barHeight, fullColor);
                        // 不绘制
                    } else if (f < 1.0F) {
                        // 冷却中：计算进度宽度（0~16）
                        int progressWidth = (int) (f * barWidth);
                        // 保证至少 1 像素，避免进度为 0 时看不到
                        progressWidth = Math.max(progressWidth, 1);
                        // 进度颜色：半透明白色，透明度 80%
                        int progressColor = 0xCCFFFFFF;
                        context.fill(barX, barY, barX + progressWidth, barY + barHeight, progressColor);
                    }
                }
            }
        }
        context.pose().popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}