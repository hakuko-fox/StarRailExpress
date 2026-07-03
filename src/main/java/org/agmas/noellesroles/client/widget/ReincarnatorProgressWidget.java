package org.agmas.noellesroles.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.client.hud.roles.ReincarnatorIcons;
import org.agmas.noellesroles.game.roles.neutral.reincarnator.ReincarnatorCauses;
import org.agmas.noellesroles.game.roles.neutral.reincarnator.ReincarnatorPlayerComponent;

/**
 * 背包内的轮回者进度面板：阶段 / 所需数量、标准死因格子（已得高亮、未得暗化）、诱导解锁状态。
 */
public class ReincarnatorProgressWidget extends AbstractWidget {

    private static final int COLS = 4;
    private static final int CELL = 18;

    public ReincarnatorProgressWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("hud.noellesroles.reincarnator.panel_title"));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ReincarnatorPlayerComponent comp = ReincarnatorPlayerComponent.KEY.maybeGet(client.player).orElse(null);
        if (comp == null) return;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // 背景
        graphics.fill(x, y, x + w, y + h, 0xC0101010);
        graphics.fill(x, y, x + w, y + 1, 0xFF8A2BE2);

        int textY = y + 4;
        graphics.drawString(client.font,
                Component.translatable("hud.noellesroles.reincarnator.panel_title"), x + 4, textY, 0xFFBB66FF, true);
        textY += 12;
        graphics.drawString(client.font,
                Component.translatable("hud.noellesroles.reincarnator.progress",
                        comp.deathCausesSeen.size(), comp.requiredCauses, comp.getStage()),
                x + 4, textY, 0xFFFFFFFF, false);
        textY += 12;
        Component lure = comp.isLureUnlocked()
                ? Component.translatable("hud.noellesroles.reincarnator.lure_ready")
                : Component.translatable("hud.noellesroles.reincarnator.lure_locked", comp.getLureUnlockStage());
        graphics.drawString(client.font, lure, x + 4, textY,
                comp.isLureUnlocked() ? 0xFF55FF55 : 0xFFFF5555, false);
        textY += 14;

        // 标准死因格子
        int gx = x + 4;
        int gy = textY;
        int i = 0;
        for (ResourceLocation cause : ReincarnatorCauses.DISPLAY_CAUSES) {
            int cx = gx + (i % COLS) * CELL;
            int cy = gy + (i / COLS) * CELL;
            Item item = ReincarnatorIcons.icon(cause);
            graphics.renderFakeItem(item.getDefaultInstance(), cx, cy);
            if (!comp.deathCausesSeen.contains(cause)) {
                graphics.fill(cx, cy, cx + 16, cy + 16, 0xB0000000);
            }
            i++;
        }

        // 额外（非标准）已收集死因，追加在下方
        for (ResourceLocation cause : comp.deathCausesSeen) {
            if (ReincarnatorCauses.DISPLAY_CAUSES.contains(cause)) continue;
            int cx = gx + (i % COLS) * CELL;
            int cy = gy + (i / COLS) * CELL;
            graphics.renderFakeItem(ReincarnatorIcons.icon(cause).getDefaultInstance(), cx, cy);
            i++;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
    }
}
