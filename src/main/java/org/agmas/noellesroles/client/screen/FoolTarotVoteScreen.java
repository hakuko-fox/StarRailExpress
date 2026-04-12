package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.roles.fool.FoolPlayerComponent;
import org.agmas.noellesroles.roles.fool.FoolTarotVoteC2SPacket;

import java.util.List;
import java.util.UUID;

public class FoolTarotVoteScreen extends Screen {
    private final List<UUID> candidates;
    private final int durationSeconds;
    private long openTime;
    private boolean voteSubmitted;

    public FoolTarotVoteScreen(List<UUID> candidates, int durationSeconds) {
        super(Component.translatable("screen.noellesroles.fool.vote.title"));
        this.candidates = List.copyOf(candidates);
        this.durationSeconds = durationSeconds;
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = Util.getMillis();

        int columns = this.candidates.size() > 4 ? 2 : 1;
        int rows = Math.max(1, (int) Math.ceil(this.candidates.size() / (double) columns));
        int buttonWidth = 140;
        int buttonHeight = 20;
        int horizontalGap = 12;
        int verticalGap = 8;
        int totalWidth = columns * buttonWidth + (columns - 1) * horizontalGap;
        int startX = (this.width - totalWidth) / 2;
        int startY = this.height / 2 - (rows * (buttonHeight + verticalGap)) / 2 + 12;

        for (int index = 0; index < this.candidates.size(); index++) {
            UUID candidateUuid = this.candidates.get(index);
            int column = index % columns;
            int row = index / columns;
            int x = startX + column * (buttonWidth + horizontalGap);
            int y = startY + row * (buttonHeight + verticalGap);
            this.addRenderableWidget(Button.builder(resolvePlayerName(candidateUuid), button -> submitVote(candidateUuid))
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build());
        }
    }

    private void submitVote(UUID candidateUuid) {
        this.voteSubmitted = true;
        ClientPlayNetworking.send(new FoolTarotVoteC2SPacket(candidateUuid));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private Component resolvePlayerName(UUID playerUuid) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            PlayerInfo playerInfo = client.getConnection().getPlayerInfo(playerUuid);
            if (playerInfo != null) {
                return Component.literal(playerInfo.getProfile().getName());
            }
        }
        String fallback = playerUuid.toString();
        return Component.literal(fallback.substring(0, Math.min(8, fallback.length())));
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int elapsedSeconds = (int) ((Util.getMillis() - this.openTime) / 1000L);
        int remainSeconds = Math.max(0, this.durationSeconds - elapsedSeconds);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, this.height / 2 - 78, 0xF4E3A1);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.fool.vote.subtitle"),
                centerX, this.height / 2 - 62, 0xE0E0E0);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.fool.vote.remaining", remainSeconds),
                centerX, this.height / 2 - 46, 0xC9B46E);
    }
}