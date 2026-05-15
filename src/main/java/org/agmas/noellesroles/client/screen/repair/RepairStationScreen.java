package org.agmas.noellesroles.client.screen.repair;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;
import org.agmas.noellesroles.packet.RepairStationActionC2SPacket;

public class RepairStationScreen extends Screen {
    private final BlockPos blockPos;
    private int ticks;

    public RepairStationScreen(BlockPos blockPos) {
        super(Component.translatable("screen.noellesroles.repair_station.title"));
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.repair_station.repair"), button -> {
            ClientPlayNetworking.send(new RepairStationActionC2SPacket(blockPos, isMarkerInGreatZone()));
        }).bounds(width / 2 - 60, height / 2 + 42, 120, 20).build());
    }

    @Override
    public void tick() {
        ticks++;
        super.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int panelX = width / 2 - 118;
        int panelY = height / 2 - 70;
        graphics.fill(panelX, panelY, panelX + 236, panelY + 128, 0xD0101018);
        graphics.fill(panelX + 4, panelY + 4, panelX + 232, panelY + 124, 0x90212A35);
        graphics.drawCenteredString(font, title, width / 2, panelY + 12, 0xFFE8F7FF);

        int progress = getProgress();
        int barX = width / 2 - 90;
        int barY = height / 2 - 24;
        graphics.fill(barX, barY, barX + 180, barY + 16, 0xFF111111);
        graphics.fill(barX + 2, barY + 2, barX + 2 + (int) (176 * (progress / 100.0F)), barY + 14, 0xFF48D17A);
        graphics.drawCenteredString(font, Component.literal(progress + "%"), width / 2, barY + 4, 0xFFFFFFFF);

        int skillY = height / 2 + 10;
        graphics.fill(barX, skillY, barX + 180, skillY + 12, 0xFF090909);
        int greatStart = barX + 76;
        int greatEnd = barX + 104;
        graphics.fill(greatStart, skillY + 2, greatEnd, skillY + 10, 0xFF2BD66B);
        int markerX = barX + getMarkerOffset();
        int markerColor = isMarkerInGreatZone() ? 0xFFFFF176 : 0xFFFF7043;
        graphics.fill(markerX - 2, skillY - 4, markerX + 2, skillY + 16, markerColor);

        float pulse = 0.6F + 0.4F * Mth.sin((ticks + delta) * 0.25F);
        int glow = ((int) (pulse * 120.0F) << 24) | 0x00FFAA33;
        graphics.fill(panelX + 12, panelY + 90, panelX + 224, panelY + 92, glow);
        graphics.drawCenteredString(font,
                Component.translatable("screen.noellesroles.repair_station.help").withStyle(ChatFormatting.GRAY),
                width / 2, panelY + 100, 0xFFB0B8C0);
        super.render(graphics, mouseX, mouseY, delta);
    }

    private int getProgress() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.getBlockEntity(blockPos) instanceof RepairStationBlockEntity station) {
            return station.getProgress();
        }
        return 0;
    }

    private int getMarkerOffset() {
        return (int) (90 + Mth.sin(ticks * 0.18F) * 86);
    }

    private boolean isMarkerInGreatZone() {
        int offset = getMarkerOffset();
        return offset >= 76 && offset <= 104;
    }
}
