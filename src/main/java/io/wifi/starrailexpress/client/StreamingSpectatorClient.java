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
            addWrapped(lines, font, Component.literal("挂播").withStyle(ChatFormatting.BOLD), wrapWidth,
                    0xFFF3DEAD, 0);
            addWrapped(lines, font, Component.literal("正在寻找可挂播目标..."), wrapWidth, 0xFFD6DCE5, 0);
        } else {
            buildTargetLines(lines, font, wrapWidth, target);
        }

        int maxLines = Math.max(3, (screenHeight - y - 24) / 11);
        boolean truncated = lines.size() > maxLines;
        int visibleLines = Math.min(lines.size(), maxLines);
        int height = 18 + visibleLines * 11 + (truncated ? 12 : 0);

        drawPanel(guiGraphics, x, y, width, height, 0xC4141822, 0x665F9FD7);
        int lineY = y + 10;
        for (int i = 0; i < visibleLines; i++) {
            HudLine line = lines.get(i);
            guiGraphics.drawString(font, line.text(), x + 12 + line.indent(), lineY, line.color(), false);
            lineY += 11;
        }
        if (truncated) {
            guiGraphics.drawString(font, Component.literal("..."), x + 12, lineY, 0xFFB8C0CA, false);
        }
    }

    private static void buildTargetLines(List<HudLine> lines, Font font, int wrapWidth, Player target) {
        addWrapped(lines, font,
                Component.literal("挂播: ").append(target.getDisplayName()).withStyle(ChatFormatting.BOLD),
                wrapWidth, 0xFFF3DEAD, 0);

        SRERole role = SREClient.gameComponent == null ? null : SREClient.gameComponent.getRole(target);
        if (role != null) {
            addWrapped(lines, font, role.getName().copy().withColor(role.color()), wrapWidth, 0xFFFFFFFF, 0);
            addDescription(lines, font, role.getSimpleDescription(), wrapWidth, 0xFFDDE3EA, 8);
        } else {
            addWrapped(lines, font, Component.literal("职业未知"), wrapWidth, 0xFFFFB0A8, 0);
        }

        addSpacer(lines);
        addWrapped(lines, font, Component.literal("修饰符").withStyle(ChatFormatting.BOLD), wrapWidth, 0xFFE4CAA1, 0);
        if (SREClient.modifierComponent == null) {
            addWrapped(lines, font, Component.literal("暂无修饰符数据"), wrapWidth, 0xFFB8C0CA, 8);
            return;
        }

        List<SREModifier> modifiers = SREClient.modifierComponent.getDisplayableModifiers(target);
        modifiers.sort(Comparator.comparing(modifier -> modifier.identifier().toString()));
        if (modifiers.isEmpty()) {
            addWrapped(lines, font, Component.literal("无"), wrapWidth, 0xFFB8C0CA, 8);
            return;
        }

        for (SREModifier modifier : modifiers) {
            addWrapped(lines, font, modifier.getName(true), wrapWidth, 0xFFFFFFFF, 8);
            addDescription(lines, font, modifier.getSimpleDescription(), wrapWidth, 0xFFDDE3EA, 16);
        }
    }

    private static void addDescription(List<HudLine> lines, Font font, Component component, int wrapWidth, int color,
            int indent) {
        String raw = component.getString();
        if (raw.isBlank()) {
            return;
        }
        for (String part : raw.split("\\\\n|\\n")) {
            if (part.isBlank()) {
                addSpacer(lines);
            } else {
                addWrapped(lines, font, Component.literal(part), wrapWidth, color, indent);
            }
        }
    }

    private static void addWrapped(List<HudLine> lines, Font font, Component component, int width, int color,
            int indent) {
        for (FormattedCharSequence sequence : font.split(component, Math.max(40, width - indent))) {
            lines.add(new HudLine(sequence, color, indent));
        }
    }

    private static void addSpacer(List<HudLine> lines) {
        lines.add(new HudLine(FormattedCharSequence.EMPTY, 0x00FFFFFF, 0));
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
        clearCameraBinding(client);
    }

    private static void clearCameraBinding(Minecraft client) {
        if (cameraBound && client.player != null && client.getCameraEntity() != client.player) {
            client.setCameraEntity(client.player);
        }
        cameraBound = false;
    }

    private static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int fill, int border) {
        guiGraphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x44000000);
        guiGraphics.fill(x, y, x + width, y + height, fill);
        guiGraphics.fill(x, y, x + width, y + 1, border);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, border);
        guiGraphics.fill(x, y, x + 1, y + height, border);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private record HudLine(FormattedCharSequence text, int color, int indent) {
    }
}
