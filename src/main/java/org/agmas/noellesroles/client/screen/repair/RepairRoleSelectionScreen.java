package org.agmas.noellesroles.client.screen.repair;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;
import org.agmas.noellesroles.packet.RepairRolePurchaseC2SPacket;
import org.agmas.noellesroles.packet.RepairRoleSelectC2SPacket;

import java.util.List;

public class RepairRoleSelectionScreen extends Screen {
    private final RepairRoleDefinition.Faction faction;
    private final long endTick;
    private final List<String> playerNames;
    private RepairRoleDefinition previewRole;

    public RepairRoleSelectionScreen(String faction, long endTick, List<String> playerNames) {
        super(Component.translatable("screen.noellesroles.repair_roles.title"));
        this.faction = RepairRoleDefinition.Faction.valueOf(faction.toUpperCase());
        this.endTick = endTick;
        this.playerNames = playerNames;
    }

    @Override
    protected void init() {
        int startX = width / 2 - 160;
        int y = height / 2 - 45;
        int index = 0;
        for (RepairRoleDefinition role : RepairRoleDefinition.byFaction(faction)) {
            int x = startX + index * 108;
            addRenderableWidget(Button.builder(role.displayName(), button -> {
                previewRole = role;
                if (owns(role)) {
                    ClientPlayNetworking.send(new RepairRoleSelectC2SPacket(role.id));
                } else {
                    ClientPlayNetworking.send(new RepairRolePurchaseC2SPacket(role.id));
                }
            }).bounds(x, y + 72, 96, 20).build());
            index++;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(width / 2 - 190, height / 2 - 105, width / 2 + 190, height / 2 + 115, 0xD0121218);
        long remaining = Math.max(0, (endTick - (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0)) / 20);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 94, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.noellesroles.repair_roles.timer", remaining),
                width / 2, height / 2 - 80, 0xFFFFD166);
        graphics.drawString(font, Component.translatable("screen.noellesroles.repair_roles.team", faction.displayName()),
                width / 2 - 180, height / 2 - 62, 0xFFBDEBFF);
        drawPlayerModels(graphics, mouseX, mouseY);
        drawRoleCards(graphics);
        drawPreview(graphics);

    }

    private void drawPlayerModels(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        int x = width / 2 - 170;
        int y = height / 2 + 88;
        if (client.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 20, y - 48, x + 68, y + 6, 28,
                    0.1F, mouseX, mouseY, client.player);
        }
        int textY = height / 2 + 76;
        graphics.drawString(font, Component.translatable("screen.noellesroles.repair_roles.players"), width / 2 + 56,
                textY, 0xFFCCCCCC);
        for (int i = 0; i < Math.min(6, playerNames.size()); i++) {
            graphics.drawString(font, Component.literal(playerNames.get(i)), width / 2 + 56, textY + 12 + i * 10,
                    0xFFAAAAAA);
        }
    }

    private void drawRoleCards(GuiGraphics graphics) {
        int startX = width / 2 - 160;
        int y = height / 2 - 45;
        int index = 0;
        for (RepairRoleDefinition role : RepairRoleDefinition.byFaction(faction)) {
            int x = startX + index * 108;
            boolean owned = owns(role);
            boolean selected = isSelected(role);
            graphics.fill(x, y, x + 96, y + 66, selected ? 0xFF244B37 : 0xFF20242C);
            graphics.drawString(font, role.displayName(), x + 6, y + 8, owned ? 0xFFFFFFFF : 0xFF888888);
            graphics.drawWordWrap(font, role.description(), x + 6, y + 22, 84, 0xFFB8C0C8);
            graphics.drawString(font, owned ? Component.translatable("screen.noellesroles.repair_roles.owned")
                    : Component.translatable("screen.noellesroles.repair_roles.price", RepairRoleDefinition.UNLOCK_PRICE),
                    x + 6, y + 56, owned ? 0xFF7CFC98 : 0xFFFF6B6B);
            index++;
        }
    }

    private void drawPreview(GuiGraphics graphics) {
        if (previewRole == null) {
            return;
        }
        graphics.drawString(font, previewRole.displayName().copy().withStyle(ChatFormatting.GOLD), width / 2 - 180,
                height / 2 + 28, 0xFFFFFFFF);
        graphics.drawWordWrap(font, previewRole.description(), width / 2 - 180, height / 2 + 42, 210, 0xFFCCCCCC);
    }

    private boolean owns(RepairRoleDefinition role) {
        if (minecraft == null || minecraft.player == null) {
            return role.starter;
        }
        return ModComponents.REPAIR_ROLES.get(minecraft.player).owns(role);
    }

    private boolean isSelected(RepairRoleDefinition role) {
        if (minecraft == null || minecraft.player == null) {
            return role.starter;
        }
        return ModComponents.REPAIR_ROLES.get(minecraft.player).selectedRole(faction) == role;
    }
}
