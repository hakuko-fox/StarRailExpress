package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.network.StreamingSpectatorPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class StreamingSpectatorClient {
    private static boolean active;
    private static UUID targetUuid;
    private static int cameraMode = StreamingSpectatorPayload.CAMERA_NONE;
    private static boolean cameraBound;
    private static List<ItemStack> targetInventory = List.of();
    private static final float DESCRIPTION_SCALE = 0.6F;
    private static final float ITEM_SCALE = 0.75F;
    private static final int SLOT_SIZE = 12;
    private static final int SLOT_GAP = 2;
    private static final int INVENTORY_COLUMNS = 9;
    private static final int MAIN_INVENTORY_SLOTS = 36;
    private static final int PANEL_FILL = 0x78141822;
    private static final int PANEL_BORDER = 0x4A5F9FD7;

    private StreamingSpectatorClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StreamingSpectatorPayload.ID, (payload, context) ->
                context.client().execute(() -> applyPayload(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(StreamingSpectatorClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> getLockedCameraType());
    }

    private static void applyPayload(Minecraft client, StreamingSpectatorPayload payload) {
        UUID previousTarget = targetUuid;
        active = payload.active();
        targetUuid = payload.targetUuid();
        cameraMode = payload.cameraMode();
        targetInventory = copyInventory(payload.inventory());
        if (!active || targetUuid == null) {
            clearCameraBinding(client);
        } else if (!targetUuid.equals(previousTarget)) {
            clearCameraBinding(client);
        }
    }

    private static void tick(Minecraft client) {
        if (!active || client.player == null || client.level == null || targetUuid == null) {
            if (!active) {
                clearCameraBinding(client);
            }
            return;
        }

        Entity target = client.level.getPlayerByUUID(targetUuid);
        if (target == null) {
            clearCameraBinding(client);
            return;
        }
        if (client.getCameraEntity() != target) {
            client.setCameraEntity(target);
        }
        cameraBound = true;
    }

    public static void renderHud(GuiGraphics guiGraphics) {
        Minecraft client = Minecraft.getInstance();
        if (!active || client.options.hideGui || client.player == null || client.level == null) {
            return;
        }

        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int width = Mth.clamp(screenWidth / 3, 190, 310);
        int x = 10;
        int y = 34;
        int wrapWidth = width - 24;

        List<HudLine> lines = new ArrayList<>();
        Player target = targetUuid == null ? null : client.level.getPlayerByUUID(targetUuid);
        if (target == null) {
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.title").withStyle(ChatFormatting.BOLD),
                    wrapWidth, 0xFFF3DEAD, 0);
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.waiting"), wrapWidth, 0xFFD6DCE5, 0);
        } else {
            buildTargetLines(lines, font, wrapWidth, target);
        }

        int inventoryHeight = target == null ? 0 : inventoryHeight(font);
        int maxPanelHeight = Math.max(58, screenHeight - y - 24);
        int maxTextHeight = Math.max(0, maxPanelHeight - 18 - inventoryHeight);
        VisibleLines visible = collectVisibleLines(lines, maxTextHeight, font);
        int ellipsisHeight = visible.truncated() ? lineHeight(font, 1.0F) : 0;
        int height = 18 + visible.height() + ellipsisHeight + inventoryHeight;

        drawPanel(guiGraphics, x, y, width, height, PANEL_FILL, PANEL_BORDER);
        int lineY = y + 10;
        for (int i = 0; i < visible.count(); i++) {
            HudLine line = lines.get(i);
            drawLine(guiGraphics, font, line, x + 12 + line.indent(), lineY);
            lineY += line.height();
        }
        if (visible.truncated()) {
            guiGraphics.drawString(font, Component.literal("..."), x + 12, lineY, 0xFFB8C0CA, false);
            lineY += ellipsisHeight;
        }
        if (target != null) {
            renderInventory(guiGraphics, font, x + 12, lineY, wrapWidth);
        }
    }

    private static void buildTargetLines(List<HudLine> lines, Font font, int wrapWidth, Player target) {
        addWrapped(lines, font,
                Component.translatable("hud.sre.streaming_spectator.target", target.getDisplayName())
                        .withStyle(ChatFormatting.BOLD),
                wrapWidth, 0xFFF3DEAD, 0);

        SRERole role = SREClient.gameComponent == null ? null : SREClient.gameComponent.getRole(target);
        if (role != null) {
            addWrapped(lines, font, role.getName().copy().withColor(role.color()), wrapWidth, 0xFFFFFFFF, 0);
            addDescription(lines, font, role.getSimpleDescription(), wrapWidth, 0xFFDDE3EA, 8);
        } else {
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.unknown_role"), wrapWidth, 0xFFFFB0A8, 0);
        }

        addSpacer(lines);
        addWrapped(lines, font,
                Component.translatable("hud.sre.streaming_spectator.modifiers").withStyle(ChatFormatting.BOLD),
                wrapWidth, 0xFFE4CAA1, 0);
        if (SREClient.modifierComponent == null) {
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.modifiers_unavailable"),
                    wrapWidth, 0xFFB8C0CA, 8);
            return;
        }

        List<SREModifier> modifiers = SREClient.modifierComponent.getDisplayableModifiers(target);
        modifiers.sort(Comparator.comparing(modifier -> modifier.identifier().toString()));
        if (modifiers.isEmpty()) {
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.none"), wrapWidth, 0xFFB8C0CA, 8);
            return;
        }

        for (SREModifier modifier : modifiers) {
            addWrapped(lines, font, modifier.getName(true), wrapWidth, 0xFFFFFFFF, 8);
            addDescription(lines, font, modifier.getSimpleDescription(), wrapWidth, 0xFFDDE3EA, 16);
        }
    }

    private static void addDescription(List<HudLine> lines, Font font, Component component, int wrapWidth, int color,
            int indent) {
        if (component == null) {
            return;
        }
        String raw = component.getString();
        if (raw.isBlank()) {
            return;
        }
        for (String part : raw.split("\\\\n|\\n")) {
            if (part.isBlank()) {
                addSpacer(lines);
            } else {
                addWrapped(lines, font, Component.literal(part), wrapWidth, color, indent, DESCRIPTION_SCALE);
            }
        }
    }

    private static void addWrapped(List<HudLine> lines, Font font, Component component, int width, int color,
            int indent) {
        addWrapped(lines, font, component, width, color, indent, 1.0F);
    }

    private static void addWrapped(List<HudLine> lines, Font font, Component component, int width, int color,
            int indent, float scale) {
        int scaledWidth = Math.max(40, Mth.floor((width - indent) / scale));
        for (FormattedCharSequence sequence : font.split(component, scaledWidth)) {
            lines.add(new HudLine(sequence, color, indent, scale, lineHeight(font, scale)));
        }
    }

    private static void addSpacer(List<HudLine> lines) {
        lines.add(new HudLine(FormattedCharSequence.EMPTY, 0x00FFFFFF, 0, 1.0F, 5));
    }

    private static AllowOtherCameraType.ReturnCameraType getLockedCameraType() {
        if (!active || targetUuid == null) {
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        }
        return switch (cameraMode) {
            case StreamingSpectatorPayload.CAMERA_FIRST_PERSON -> AllowOtherCameraType.ReturnCameraType.FIRST_PERSON;
            case StreamingSpectatorPayload.CAMERA_THIRD_PERSON_BACK ->
                    AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            default -> AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        };
    }

    private static void clear(Minecraft client) {
        active = false;
        targetUuid = null;
        cameraMode = StreamingSpectatorPayload.CAMERA_NONE;
        targetInventory = List.of();
        clearCameraBinding(client);
    }

    private static void clearCameraBinding(Minecraft client) {
        if (cameraBound && client.player != null && client.getCameraEntity() != client.player) {
            client.setCameraEntity(client.player);
        }
        cameraBound = false;
    }

    private static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int fill, int border) {
        guiGraphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x22000000);
        guiGraphics.fill(x, y, x + width, y + height, fill);
        guiGraphics.fill(x, y, x + width, y + 1, border);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, border);
        guiGraphics.fill(x, y, x + 1, y + height, border);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private static VisibleLines collectVisibleLines(List<HudLine> lines, int maxHeight, Font font) {
        int count = 0;
        int height = 0;
        int ellipsisHeight = lineHeight(font, 1.0F);
        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            boolean hasMore = i < lines.size() - 1;
            int reserve = hasMore ? ellipsisHeight : 0;
            if (height + line.height() + reserve > maxHeight) {
                return new VisibleLines(count, height, true);
            }
            count++;
            height += line.height();
        }
        return new VisibleLines(count, height, false);
    }

    private static void drawLine(GuiGraphics guiGraphics, Font font, HudLine line, int x, int y) {
        if (line.scale() == 1.0F) {
            guiGraphics.drawString(font, line.text(), x, y, line.color(), false);
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(line.scale(), line.scale(), 1.0F);
        guiGraphics.drawString(font, line.text(), 0, 0, line.color(), false);
        guiGraphics.pose().popPose();
    }

    private static int lineHeight(Font font, float scale) {
        return Math.max(1, Mth.ceil(font.lineHeight * scale)) + 2;
    }

    private static int inventoryHeight(Font font) {
        return 4 + font.lineHeight + 4
                + (MAIN_INVENTORY_SLOTS / INVENTORY_COLUMNS) * (SLOT_SIZE + SLOT_GAP)
                + 4 + SLOT_SIZE;
    }

    private static void renderInventory(GuiGraphics guiGraphics, Font font, int x, int y, int width) {
        int cursorY = y + 4;
        guiGraphics.drawString(font,
                Component.translatable("hud.sre.streaming_spectator.inventory").withStyle(ChatFormatting.BOLD),
                x, cursorY, 0xFFE4CAA1, false);
        cursorY += font.lineHeight + 4;

        int gridWidth = INVENTORY_COLUMNS * SLOT_SIZE + (INVENTORY_COLUMNS - 1) * SLOT_GAP;
        int gridX = x + Math.max(0, (width - gridWidth) / 2);
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
            int row = slot / INVENTORY_COLUMNS;
            int col = slot % INVENTORY_COLUMNS;
            int inventoryIndex = slot < 27 ? slot + 9 : slot - 27;
            renderInventorySlot(guiGraphics, font, inventoryIndex,
                    gridX + col * (SLOT_SIZE + SLOT_GAP),
                    cursorY + row * (SLOT_SIZE + SLOT_GAP));
        }

        cursorY += (MAIN_INVENTORY_SLOTS / INVENTORY_COLUMNS) * (SLOT_SIZE + SLOT_GAP) + 4;
        int[] equipmentSlots = {39, 38, 37, 36, 40};
        int equipmentX = gridX + Math.max(0,
                (gridWidth - equipmentSlots.length * SLOT_SIZE - (equipmentSlots.length - 1) * SLOT_GAP) / 2);
        for (int i = 0; i < equipmentSlots.length; i++) {
            renderInventorySlot(guiGraphics, font, equipmentSlots[i],
                    equipmentX + i * (SLOT_SIZE + SLOT_GAP), cursorY);
        }
    }

    private static void renderInventorySlot(GuiGraphics guiGraphics, Font font, int stackIndex, int x, int y) {
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x55212935);
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + 1, 0x55D8E2EF);
        guiGraphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, 0x552B3340);
        guiGraphics.fill(x, y, x + 1, y + SLOT_SIZE, 0x55D8E2EF);
        guiGraphics.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x552B3340);

        ItemStack stack = getInventoryStack(stackIndex);
        if (stack.isEmpty()) {
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(ITEM_SCALE, ITEM_SCALE, 1.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.renderItemDecorations(font, stack, 0, 0);
        guiGraphics.pose().popPose();
    }

    private static ItemStack getInventoryStack(int index) {
        if (index < 0 || index >= targetInventory.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = targetInventory.get(index);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private static List<ItemStack> copyInventory(List<ItemStack> inventory) {
        if (inventory == null || inventory.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copy = new ArrayList<>(inventory.size());
        for (ItemStack stack : inventory) {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    private record HudLine(FormattedCharSequence text, int color, int indent, float scale, int height) {
    }

    private record VisibleLines(int count, int height, boolean truncated) {
    }
}
