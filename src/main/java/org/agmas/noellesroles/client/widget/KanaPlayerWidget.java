/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.VtuberRoleMenuC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 十七夜佳奈的玩家头像选择组件。 */
public final class KanaPlayerWidget extends Button {
    private final PlayerInfo targetInfo;
    private Component displayText = Component.empty();
    private List<net.minecraft.util.FormattedCharSequence> cachedLines = new ArrayList<>();

    public KanaPlayerWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo targetInfo) {
        super(x, y, 16, 16, Component.nullToEmpty(targetInfo.getProfile().getName()), button ->
                ClientPlayNetworking.send(new VtuberRoleMenuC2SPacket(targetInfo.getProfile().getId(), null)),
                DEFAULT_NARRATION);
        this.targetInfo = targetInfo;
        updateDisplayText();
    }

    private void updateDisplayText() {
        if (SREClient.gameComponent == null) {
            return;
        }
        var role = SREClient.gameComponent.getRole(targetInfo.getProfile().getId());
        if (role != null && ModRoles.isVisibleKillerTeammate(role)) {
            setDisplayText(Component.translatable("hud.general.killer_friend").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.blitSprite(ShopEntry.Type.POISON.getTexture(), getX() - 7, getY() - 7, 30, 30);
        PlayerFaceRenderer.draw(graphics, targetInfo.getSkin().texture(), getX(), getY(), 16);

        if (isHovered()) {
            drawSlotHighlight(graphics, getX(), getY());
            Component playerName = Component.nullToEmpty(targetInfo.getProfile().getName());
            graphics.renderTooltip(Minecraft.getInstance().font, playerName,
                    getX() - 4 - Minecraft.getInstance().font.width(playerName) / 2, getY() - 9);
        }

        renderDisplayText(graphics);
    }

    private void drawSlotHighlight(GuiGraphics graphics, int x, int y) {
        int color = -1862287543;
        graphics.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        graphics.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        graphics.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }

    private void setDisplayText(Component text) {
        this.displayText = text;
        this.cachedLines.clear();
    }

    private void renderDisplayText(GuiGraphics graphics) {
        if (displayText.getString().isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        if (cachedLines.isEmpty()) {
            cachedLines = font.split(displayText, 50);
        }
        int startY = getY() + getHeight() + 4;
        for (int index = 0; index < cachedLines.size(); index++) {
            var line = cachedLines.get(index);
            int lineWidth = font.width(line);
            int x = getX() + (getWidth() - lineWidth) / 2;
            int y = startY + index * (font.lineHeight + 1);
            graphics.fill(x - 2, y - 1, x + lineWidth + 2, y + font.lineHeight + 1, 0x80000000);
            graphics.drawString(font, line, x, y, 0xFFFFFF, true);
        }
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        // Player name is shown in the hover tooltip, matching Party Killer.
    }
}
