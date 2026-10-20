package org.agmas.noellesroles.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.VtuberRoleMenuC2SPacket;

public class VtuberPlayerSelectScreen extends Screen {
    private static final int COLUMNS = 4;
    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 20;
    private final int requiredSelections;
    private final boolean includeSelf;
    private final List<UUID> selected = new ArrayList<>();
    private Button confirmButton;
    private int page;
    private List<UUID> roster = List.of();

    private long cooldownSeconds() {
        return minecraft == null || minecraft.player == null ? 0L
                : io.wifi.starrailexpress.api.data.RoleData.getOptional(org.agmas.noellesroles.role_data.vtuber.VtuberRoleData.class, minecraft.player)
                        .map(org.agmas.noellesroles.role_data.vtuber.VtuberRoleData::getMenuCooldownSeconds).orElse(0L);
    }

    @Override
    public void tick() {
        List<UUID> current = candidates().stream().map(p -> p.getProfile().getId()).toList();
        if (!current.equals(roster)) {
            rebuildButtons();
            return;
        }
        boolean ready = cooldownSeconds() == 0L;
        for (var child : children()) {
            if (child instanceof PlayerTargetButton button) button.active = ready;
        }
        if (confirmButton != null) confirmButton.active = ready && selected.size() == requiredSelections;
    }

    public VtuberPlayerSelectScreen(int requiredSelections, boolean includeSelf) {
        super(Component.translatable("screen.noellesroles.vtuber_player_select.title", requiredSelections));
        this.requiredSelections = requiredSelections;
        this.includeSelf = includeSelf;
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private List<PlayerInfo> candidates() {
        if (minecraft == null || minecraft.player == null
                || minecraft.getConnection() == null || SREClient.gameComponent == null) {
            return List.of();
        }
        return minecraft.getConnection().getOnlinePlayers().stream()
                .filter(player -> player.getGameMode() == GameType.ADVENTURE
                        || player.getGameMode() == GameType.SURVIVAL)
                .filter(player -> SREClient.gameComponent.getRole(player.getProfile().getId()) != null)
                .filter(player -> includeSelf || !player.getProfile().getId().equals(minecraft.player.getUUID()))
                .sorted(Comparator.comparing(player -> player.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void rebuildButtons() {
        clearWidgets();
        List<PlayerInfo> candidates = candidates();
        roster = candidates.stream().map(p -> p.getProfile().getId()).toList();
        selected.removeIf(uuid -> candidates.stream().noneMatch(p -> p.getProfile().getId().equals(uuid)));
        int columns = Math.max(1, Math.min(COLUMNS, (width - 12) / (BUTTON_WIDTH + 6)));
        int rows = Math.max(1, (height - 115) / (BUTTON_HEIGHT + 6));
        int pageSize = columns * rows;
        int pages = Math.max(1, (candidates.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages - 1);
        int startX = (width - columns * (BUTTON_WIDTH + 6) + 6) / 2;
        int startY = 55;
        int first = page * pageSize;
        for (int index = first; index < Math.min(candidates.size(), first + pageSize); index++) {
            PlayerInfo candidate = candidates.get(index);
            boolean chosen = selected.contains(candidate.getProfile().getId());
            int x = startX + (index - first) % columns * (BUTTON_WIDTH + 6);
            int y = startY + (index - first) / columns * (BUTTON_HEIGHT + 6);
            addRenderableWidget(new PlayerTargetButton(x, y, candidate, chosen));
        }
        if (pages > 1) {
            Button previous = Button.builder(Component.literal("<"), button -> { page--; rebuildButtons(); })
                    .bounds(width / 2 - 104, height - 60, 100, 20).build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.literal(">"), button -> { page++; rebuildButtons(); })
                    .bounds(width / 2 + 4, height - 60, 100, 20).build();
            next.active = page + 1 < pages;
            addRenderableWidget(next);
        }
        Button confirm = Button.builder(Component.translatable("screen.noellesroles.vtuber_player_select.confirm"),
                button -> submit()).bounds(width / 2 - 104, height - 34, 100, 20).build();
        confirm.active = selected.size() == requiredSelections;
        confirmButton = confirm;
        addRenderableWidget(confirm);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 4, height - 34, 100, 20).build());
        tick();
    }

    private void toggle(UUID uuid) {
        if (cooldownSeconds() > 0L) return;
        if (!selected.remove(uuid) && selected.size() < requiredSelections) {
            selected.add(uuid);
        }
        rebuildButtons();
    }

    private void submit() {
        if (cooldownSeconds() > 0L || selected.size() != requiredSelections) {
            return;
        }
        ClientPlayNetworking.send(new VtuberRoleMenuC2SPacket(selected.get(0),
                selected.size() > 1 ? selected.get(1) : null));
        onClose();
    }

    private final class PlayerTargetButton extends Button {
        private final PlayerInfo candidate;
        private final boolean chosen;

        private PlayerTargetButton(int x, int y, PlayerInfo candidate, boolean chosen) {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.empty(),
                    button -> toggle(candidate.getProfile().getId()), DEFAULT_NARRATION);
            this.candidate = candidate;
            this.chosen = chosen;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.renderWidget(graphics, mouseX, mouseY, delta);
            PlayerFaceRenderer.draw(graphics, candidate.getSkin().texture(), getX() + 2, getY() + 2, 16);
            long seconds = cooldownSeconds();
            if (seconds > 0L) {
                graphics.fill(getX() + 2, getY() + 2, getX() + 18, getY() + 18, 0xAA000000);
                graphics.drawCenteredString(font, Long.toString(seconds), getX() + 10, getY() + 6, 0xFFFFFF);
            }
            Component name = Component.nullToEmpty(candidate.getProfile().getName());
            graphics.drawString(font, name, getX() + 22, getY() + 6, chosen ? 0x55FF55 : 0xFFFFFF, true);
        }
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
