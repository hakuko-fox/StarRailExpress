package org.agmas.noellesroles.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.VtuberRoleMenuC2SPacket;

public class VtuberPlayerSelectScreen extends Screen {
    private static final int COLUMNS = 4;
    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 20;
    private final int requiredSelections;
    private final boolean includeSelf;
    private final List<UUID> selected = new ArrayList<>();

    public VtuberPlayerSelectScreen(int requiredSelections, boolean includeSelf) {
        super(Component.translatable("screen.noellesroles.vtuber_player_select.title", requiredSelections));
        this.requiredSelections = requiredSelections;
        this.includeSelf = includeSelf;
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        List<AbstractClientPlayer> candidates = minecraft.level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .filter(player -> includeSelf || player != minecraft.player)
                .toList();
        int startX = (width - COLUMNS * (BUTTON_WIDTH + 6) + 6) / 2;
        int startY = 55;
        for (int index = 0; index < candidates.size(); index++) {
            AbstractClientPlayer candidate = candidates.get(index);
            boolean chosen = selected.contains(candidate.getUUID());
            Component label = chosen
                    ? Component.translatable("screen.noellesroles.vtuber_player_select.selected",
                            candidate.getDisplayName())
                    : candidate.getDisplayName();
            int x = startX + index % COLUMNS * (BUTTON_WIDTH + 6);
            int y = startY + index / COLUMNS * (BUTTON_HEIGHT + 6);
            addRenderableWidget(Button.builder(label, button -> toggle(candidate.getUUID()))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        Button confirm = Button.builder(Component.translatable("screen.noellesroles.vtuber_player_select.confirm"),
                button -> submit()).bounds(width / 2 - 104, height - 34, 100, 20).build();
        confirm.active = selected.size() == requiredSelections;
        addRenderableWidget(confirm);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 4, height - 34, 100, 20).build());
    }

    private void toggle(UUID uuid) {
        if (!selected.remove(uuid) && selected.size() < requiredSelections) {
            selected.add(uuid);
        }
        rebuildButtons();
    }

    private void submit() {
        if (selected.size() != requiredSelections) {
            return;
        }
        ClientPlayNetworking.send(new VtuberRoleMenuC2SPacket(selected.get(0),
                selected.size() > 1 ? selected.get(1) : null));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 25, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("screen.noellesroles.vtuber_player_select.count",
                        selected.size(), requiredSelections),
                width / 2, 39, 0xBBBBBB);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
