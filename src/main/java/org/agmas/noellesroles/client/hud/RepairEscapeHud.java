package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.RepairRolePlayerComponent;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.List;

public final class RepairEscapeHud {
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "starrailexpress", "textures/gui/waiting_inventory.png");
    private static final int PANEL_SRC_W = 176;
    private static final int PANEL_SRC_H = 166;
    private static final int PANEL_TEX_SIZE = 256;
    private static final int MAIN_W = 180;
    private static final int MAIN_H = 112;
    private static final int EVENT_W = 190;
    private static final int EVENT_H = 42;
    private static final int SLOT = 16;

    private static final List<CoinToast> coinToasts = new ArrayList<>();
    private static final List<CombatCue> combatCues = new ArrayList<>();

    private RepairEscapeHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || SREClient.gameComponent == null
                || !SREClient.gameComponent.isRunning() || !isRepairRole()) {
            return;
        }

        LocalPlayer player = client.player;
        RepairRolePlayerComponent component = ModComponents.REPAIR_ROLES.get(player);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long tick = client.level.getGameTime();

        int panelX = 8;
        int panelY = Math.max(8, height - MAIN_H - 8);
        renderMainPanel(graphics, player, component, panelX, panelY);
        renderEventPanel(graphics, component, Mth.clamp(width / 2 - EVENT_W / 2, 8, width - EVENT_W - 8), 8, tick);
        renderCombatCues(graphics, width, height, tick);
        renderCoinToasts(graphics, width, height, tick);
    }

    public static void pushCoinToast(int amount, String sourceKey) {
        Minecraft client = Minecraft.getInstance();
        long tick = client.level == null ? 0L : client.level.getGameTime();
        coinToasts.add(new CoinToast(amount, Component.translatable(sourceKey), tick));
        if (coinToasts.size() > 5) {
            coinToasts.remove(0);
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35F, 0.35F));
    }

    public static void pushCombatCue(int kind, int entityId, double x, double y, double z) {
        Minecraft client = Minecraft.getInstance();
        long tick = client.level == null ? 0L : client.level.getGameTime();
        combatCues.add(new CombatCue(kind, entityId, x, y, z, tick));
        if (combatCues.size() > 8) {
            combatCues.remove(0);
        }
        if (kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.HIT
                || kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.DOWNED) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_CRIT, 0.85F, 0.25F));
        }
    }

    private static boolean isRepairRole() {
        ResourceLocation roleId = SREClient.getCachedPlayerRole() == null ? null : SREClient.getCachedPlayerRole().identifier();
        if (roleId == null) {
            return false;
        }
        return roleId.equals(ModRoles.REPAIR_SURVIVOR_ID)
                || roleId.equals(ModRoles.REPAIR_HUNTER_ID)
                || roleId.equals(ModRoles.REPAIR_NEUTRAL_ID)
                || roleId.getPath().startsWith("repair_");
    }

    private static void renderMainPanel(FakeGuiGraphics graphics, LocalPlayer player,
            RepairRolePlayerComponent component, int x, int y) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        RepairRoleDefinition role = RepairRoleDefinition.byId(component.activeRole).orElse(null);
        int accent = roleAccent(component.activeRole);

        drawWoodPanel(graphics, x, y, MAIN_W, MAIN_H);
        drawCornerOrnaments(graphics, x, y, MAIN_W, MAIN_H, accent);

        Component roleName = role == null ? Component.translatable("hud.noellesroles.repair.awaiting_role")
                : role.displayName();
        drawFitted(graphics, font, roleName.copy().withStyle(ChatFormatting.BOLD), x + 12, y + 8, MAIN_W - 24,
                0xFFFFE6A3);
        drawFitted(graphics, font, skillLine(component.activeRole), x + 12, y + 20, MAIN_W - 24, 0xFFE9C46A);

        float healthPct = Mth.clamp(player.getHealth() / Math.max(1.0F, player.getMaxHealth()), 0.0F, 1.0F);
        drawPixelBar(graphics, x + 12, y + 36, 76, 6, healthPct, 0xFF3B120A, 0xFFE85D4F);
        graphics.drawString(font, Component.literal("HP " + Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth())),
                x + 94, y + 34, 0xFFFFD7A0);

        if (role != null && role.faction == RepairRoleDefinition.Faction.NEUTRAL) {
            int needed = component.neutralTaskNeeded > 0 ? component.neutralTaskNeeded : neutralGoal(component.activeRole);
            float taskPct = needed <= 0 ? 0.0F : Mth.clamp(component.neutralTaskProgress / (float) needed, 0.0F, 1.0F);
            drawPixelBar(graphics, x + 12, y + 49, 76, 6, taskPct, 0xFF3A2600, 0xFFFFC857);
            graphics.drawString(font, Component.translatable("hud.noellesroles.repair.neutral_task",
                    component.neutralTaskProgress, needed), x + 94, y + 47, 0xFFFFE8A3);
        } else {
            float stamina = Mth.clamp(player.getFoodData().getFoodLevel() / 20.0F, 0.0F, 1.0F);
            drawPixelBar(graphics, x + 12, y + 49, 76, 6, stamina, 0xFF1E2611, 0xFF9CCC65);
            graphics.drawString(font, Component.translatable("hud.noellesroles.repair.vitals"), x + 94, y + 47,
                    0xFFD9C7A3);
        }

        float stationPct = Mth.clamp(component.completedStations / (float) RepairModeState.REQUIRED_REPAIRED_STATIONS,
                0.0F, 1.0F);
        drawPixelBar(graphics, x + 12, y + 64, MAIN_W - 24, 7, stationPct, 0xFF301F12,
                component.gatesPowered ? 0xFF8DCC7D : 0xFFE9C46A);
        graphics.drawString(font, Component.translatable("hud.noellesroles.repair.stations",
                component.completedStations, RepairModeState.REQUIRED_REPAIRED_STATIONS), x + 12, y + 75,
                0xFFFFE8A3);

        Component pressure = component.gatesPowered
                ? Component.translatable("hud.noellesroles.repair.gates_powered")
                : Component.translatable("hud.noellesroles.repair.pressure", component.downedAllies,
                        component.activeTrialPrisoners);
        int pressureColor = component.gatesPowered ? 0xFFB7E4A6 : 0xFFF2A65A;
        if (component.nearestTrialProgress > 0) {
            int seconds = Math.max(0, (RepairModeState.TRIAL_EXECUTION_TICKS - component.nearestTrialProgress) / 20);
            pressure = Component.translatable("hud.noellesroles.repair.self_trial", seconds);
            pressureColor = 0xFFFF8A80;
        }
        drawFitted(graphics, font, pressure, x + 76, y + 75, MAIN_W - 88, pressureColor);

        renderSlots(graphics, font, x + 12, y + 91, component, role, healthPct, stationPct);
    }

    private static void renderSlots(FakeGuiGraphics graphics, Font font, int x, int y,
            RepairRolePlayerComponent component, RepairRoleDefinition role, float healthPct, float stationPct) {
        int[] colors = {
                healthPct < 0.35F ? 0xFFE85D4F : 0xFFA7C957,
                stationPct >= 1.0F ? 0xFFA7C957 : 0xFFE9C46A,
                component.gatesPowered ? 0xFFA7C957 : 0xFFB08968,
                component.downedAllies > 0 ? 0xFFE85D4F : 0xFFB08968,
                component.activeTrialPrisoners > 0 ? 0xFFFF8A80 : 0xFFB08968,
                role == null ? 0xFF8C6A42 : roleAccent(component.activeRole),
                component.neutralTaskProgress > 0 ? 0xFFFFC857 : 0xFFB08968,
                component.currentEventKey == null || component.currentEventKey.isEmpty() ? 0xFF8C6A42 : 0xFFE85D4F
        };
        String[] labels = {"HP", "FX", "GT", "DN", "TR", "SK", "TK", "EV"};
        for (int i = 0; i < labels.length; i++) {
            int sx = x + i * (SLOT + 3);
            drawSlot(graphics, sx, y, SLOT, colors[i]);
            int tx = sx + SLOT / 2 - font.width(labels[i]) / 2;
            graphics.drawString(font, Component.literal(labels[i]), tx, y + 4, 0xFFFFE6A3);
        }
    }

    private static void renderEventPanel(FakeGuiGraphics graphics, RepairRolePlayerComponent component,
            int x, int y, long tick) {
        if (component.currentEventKey == null || component.currentEventKey.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        int danger = Mth.clamp(component.currentEventDanger, 0, 100);
        int accent = danger >= 80 ? 0xFFE85D4F : danger >= 55 ? 0xFFFFC857 : 0xFFA7C957;

        drawWoodPanel(graphics, x, y, EVENT_W, EVENT_H);
        drawCornerOrnaments(graphics, x, y, EVENT_W, EVENT_H, accent);
        graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.event.active")
                .withStyle(ChatFormatting.BOLD), x + 10, y + 7, 0xFFFFE6A3);
        drawFitted(graphics, client.font, Component.translatable(component.currentEventKey), x + 66, y + 7,
                EVENT_W - 112, accent);
        int seconds = Math.max(0, component.currentEventTicks / 20);
        graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.event.timer", seconds),
                x + EVENT_W - 46, y + 7, 0xFFD9C7A3);
        drawPixelBar(graphics, x + 10, y + 24, 72, 6, danger / 100.0F, 0xFF30130D, 0xFFE85D4F);
        graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.event.danger", danger),
                x + 88, y + 21, 0xFFFFCFA6);
        drawFitted(graphics, client.font, Component.translatable(component.currentEventRewardKey), x + 88, y + 31,
                EVENT_W - 98, 0xFFFFE8A3);

        int sparkX = x + 8 + (int) ((Mth.sin(tick * 0.2F) * 0.5F + 0.5F) * (EVENT_W - 16));
        graphics.fill(sparkX, y + EVENT_H - 5, sparkX + 2, y + EVENT_H - 3, accent);
    }

    private static Component skillLine(String activeRole) {
        if (activeRole == null || activeRole.isEmpty()) {
            return Component.translatable("hud.noellesroles.repair.skill.selecting");
        }
        return Component.translatable("hud.noellesroles.repair.skill." + activeRole);
    }

    private static int neutralGoal(String activeRole) {
        return switch (activeRole) {
            case "archivist" -> RepairModeState.ARCHIVIST_TASK_NEEDED;
            case "saboteur" -> RepairModeState.SABOTEUR_TASK_NEEDED;
            case "collector" -> RepairModeState.COLLECTOR_TASK_NEEDED;
            default -> 0;
        };
    }

    private static int roleAccent(String activeRole) {
        return RepairRoleDefinition.byId(activeRole).map(role -> switch (role.faction) {
            case SURVIVOR -> 0xFF7BC4A6;
            case HUNTER -> 0xFFE85D4F;
            case NEUTRAL -> 0xFFFFC857;
        }).orElse(0xFFE9C46A);
    }

    private static void renderCombatCues(FakeGuiGraphics graphics, int width, int height, long tick) {
        combatCues.removeIf(cue -> tick - cue.createdTick > 24);
        int centerX = width / 2;
        int centerY = height / 2;
        for (CombatCue cue : List.copyOf(combatCues)) {
            long age = tick - cue.createdTick;
            float pct = Mth.clamp(age / 24.0F, 0.0F, 1.0F);
            int alpha = (int) ((1.0F - pct) * 180.0F);
            int cueColor = switch (cue.kind) {
                case org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.ATTACK -> 0xE9C46A;
                case org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.DOWNED -> 0xE85D4F;
                case org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.REVIVED -> 0x7BC4A6;
                default -> 0xF2A65A;
            };
            int color = (alpha << 24) | cueColor;
            int radius = 12 + (int) (pct * 24.0F);
            graphics.fill(centerX - radius, centerY - 18, centerX + radius, centerY - 15, color);
            graphics.fill(centerX - radius, centerY + 15, centerX + radius, centerY + 18, color);
            graphics.fill(centerX - radius, centerY - 18, centerX - radius + 3, centerY + 18, color);
            graphics.fill(centerX + radius - 3, centerY - 18, centerX + radius, centerY + 18, color);
        }
    }

    private static void renderCoinToasts(FakeGuiGraphics graphics, int width, int height, long tick) {
        Minecraft client = Minecraft.getInstance();
        coinToasts.removeIf(toast -> tick - toast.createdTick > 70);
        int index = 0;
        for (CoinToast toast : List.copyOf(coinToasts)) {
            long age = tick - toast.createdTick;
            float in = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
            float out = Mth.clamp((70 - age) / 14.0F, 0.0F, 1.0F);
            float alpha = in * out;
            int toastWidth = 154;
            int x = width - toastWidth - 10;
            int y = height / 4 + index * 24 - (int) ((1.0F - out) * 10.0F);
            int a = (int) (alpha * 255.0F);
            graphics.fill(x + 2, y + 3, x + toastWidth + 2, y + 22, ((int) (alpha * 95.0F) << 24));
            graphics.fill(x, y, x + toastWidth, y + 20, ((int) (alpha * 210.0F) << 24) | 0x201006);
            graphics.fill(x, y, x + toastWidth, y + 2, (a << 24) | 0xE9C46A);
            graphics.drawString(client.font, Component.literal("+" + toast.amount + " coins")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x + 8, y + 5, (a << 24) | 0xFFE6A3);
            drawFitted(graphics, client.font, toast.source, x + 74, y + 5, toastWidth - 82, (a << 24) | 0xFFF0DCA8);
            index++;
        }
    }

    private static void drawWoodPanel(FakeGuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x66000000);
        graphics.blit(PANEL_TEXTURE, x, y, width, height, 0.0F, 0.0F, PANEL_SRC_W, PANEL_SRC_H,
                PANEL_TEX_SIZE, PANEL_TEX_SIZE);
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x5A1D0D05);
        graphics.fill(x, y, x + width, y + 2, 0xFFD8A442);
        graphics.fill(x, y + height - 2, x + width, y + height, 0xFF6B3E16);
        graphics.fill(x, y, x + 2, y + height, 0xFFD8A442);
        graphics.fill(x + width - 2, y, x + width, y + height, 0xFF6B3E16);
    }

    private static void drawCornerOrnaments(FakeGuiGraphics graphics, int x, int y, int width, int height, int accent) {
        int gold = (accent & 0x00FFFFFF) | 0xFF000000;
        graphics.fill(x + 7, y + 6, x + 32, y + 8, gold);
        graphics.fill(x + 7, y + 6, x + 9, y + 25, gold);
        graphics.fill(x + 12, y + 11, x + 24, y + 13, 0xFFFFD77A);
        graphics.fill(x + 12, y + 11, x + 14, y + 20, 0xFFFFD77A);

        graphics.fill(x + width - 32, y + height - 8, x + width - 7, y + height - 6, gold);
        graphics.fill(x + width - 9, y + height - 25, x + width - 7, y + height - 6, gold);
        graphics.fill(x + width - 24, y + height - 13, x + width - 12, y + height - 11, 0xFFFFD77A);
        graphics.fill(x + width - 14, y + height - 20, x + width - 12, y + height - 11, 0xFFFFD77A);
    }

    private static void drawPixelBar(FakeGuiGraphics graphics, int x, int y, int width, int height, float pct,
            int backColor, int fillColor) {
        graphics.fill(x, y, x + width, y + height, 0xFF120804);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, backColor);
        int filled = (int) ((width - 2) * Mth.clamp(pct, 0.0F, 1.0F));
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fillColor);
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0x88FFFFFF);
    }

    private static void drawSlot(FakeGuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + size, 0xFF32160A);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF170905);
        graphics.fill(x + 2, y + size - 4, x + size - 2, y + size - 2, color);
        graphics.fill(x, y, x + size, y + 1, 0xFFE2B15A);
        graphics.fill(x, y, x + 1, y + size, 0xFFE2B15A);
        graphics.fill(x, y + size - 1, x + size, y + size, 0xFF5A3212);
        graphics.fill(x + size - 1, y, x + size, y + size, 0xFF5A3212);
    }

    private static void drawFitted(FakeGuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth,
            int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) {
            while (value.length() > 1 && font.width(value + "..") > maxWidth) {
                value = value.substring(0, value.length() - 1);
            }
            value = value + "..";
        }
        graphics.drawString(font, Component.literal(value), x, y, color);
    }

    private record CoinToast(int amount, Component source, long createdTick) {
    }

    private record CombatCue(int kind, int entityId, double x, double y, double z, long createdTick) {
    }
}
