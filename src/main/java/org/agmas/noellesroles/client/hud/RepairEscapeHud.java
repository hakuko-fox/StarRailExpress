package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.List;

public final class RepairEscapeHud {
    private static final int PANEL_WIDTH = 186;
    private static final int PANEL_HEIGHT = 78;
    private static final int MAP_SIZE = 92;
    private static final int SCAN_RADIUS = 30;
    private static long nextScanTick = -1;
    private static final List<MapBlip> cachedBlockBlips = new ArrayList<>();
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
        var component = ModComponents.REPAIR_ROLES.get(player);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long tick = client.level.getGameTime();
        float pulse = 0.5F + 0.5F * Mth.sin(tick * 0.12F);

        renderStatusPanel(graphics, player, component.activeRole, 12, height - PANEL_HEIGHT - 16, pulse);
        renderObjectivePanel(graphics, component, 12, height - PANEL_HEIGHT - 82, pulse);
        renderEventPanel(graphics, component, width / 2 - 120, 18, pulse, tick);
        renderMiniMap(graphics, player, width - MAP_SIZE - 18, height - MAP_SIZE - 18, tick, pulse);
        renderCombatCues(graphics, width, height, tick);
        renderCoinToasts(graphics, width, height, tick);
    }

    public static void pushCoinToast(int amount, String sourceKey) {
        Minecraft client = Minecraft.getInstance();
        long tick = client.level == null ? 0L : client.level.getGameTime();
        coinToasts.add(new CoinToast(amount, Component.translatable(sourceKey), tick));
        if (coinToasts.size() > 5) {
            coinToasts.removeFirst();
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35F, 0.35F));
    }

    public static void pushCombatCue(int kind, int entityId, double x, double y, double z) {
        Minecraft client = Minecraft.getInstance();
        long tick = client.level == null ? 0L : client.level.getGameTime();
        combatCues.add(new CombatCue(kind, entityId, x, y, z, tick));
        if (combatCues.size() > 8) {
            combatCues.removeFirst();
        }
        if (kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.HIT
                || kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.DOWNED) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_CRIT, 0.85F, 0.25F));
        }
        renderMiniMap(graphics, player, width - MAP_SIZE - 18, height - MAP_SIZE - 18, tick, pulse);
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

    private static void renderStatusPanel(FakeGuiGraphics graphics, LocalPlayer player, String activeRole, int x, int y,
            float pulse) {
        Minecraft client = Minecraft.getInstance();
        int accent = roleAccent(activeRole);
        drawGlassPanel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, accent, pulse);

        float healthPct = Mth.clamp(player.getHealth() / Math.max(1.0F, player.getMaxHealth()), 0.0F, 1.0F);
        drawOrb(graphics, x + 26, y + 38, 20, 0xFF3A0B18, 0xFFFF4D6D, healthPct, pulse);
        Component hp = Component.literal(Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth()))
                .withStyle(ChatFormatting.RED);
        graphics.drawCenteredString(client.font, hp, x + 26, y + 62, 0xFFFFFFFF);

        RepairRoleDefinition role = RepairRoleDefinition.byId(activeRole).orElse(null);
        Component roleName = role == null ? Component.translatable("hud.noellesroles.repair.awaiting_role")
                : role.displayName();
        graphics.drawString(client.font, roleName.copy().withStyle(ChatFormatting.BOLD), x + 54, y + 13, 0xFFFFFFFF);
        graphics.drawString(client.font, skillLine(activeRole), x + 54, y + 29, 0xFFE5F6FF);

        var component = ModComponents.REPAIR_ROLES.get(player);
        if (role != null && role.faction == RepairRoleDefinition.Faction.NEUTRAL) {
            int needed = neutralGoal(activeRole);
            float taskPct = needed <= 0 ? 0.0F : Mth.clamp(component.neutralTaskProgress / (float) needed, 0.0F, 1.0F);
            drawNeonBar(graphics, x + 54, y + 51, 118, 8, taskPct, 0xFF705000, 0xFFFFD166);
            Component task = Component.translatable("hud.noellesroles.repair.neutral_task", component.neutralTaskProgress,
                    needed);
            graphics.drawString(client.font, task, x + 56, y + 62, 0xFFFFE7A3);
        } else {
            float stamina = player.getFoodData().getFoodLevel() / 20.0F;
            drawNeonBar(graphics, x + 54, y + 51, 118, 8, stamina, 0xFF0E3855, 0xFF4CC9F0);
            graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.vitals"), x + 56, y + 62,
                    0xFFAAD8FF);
        }
    }

    private static void renderObjectivePanel(FakeGuiGraphics graphics, org.agmas.noellesroles.component.RepairRolePlayerComponent component,
            int x, int y, float pulse) {
        Minecraft client = Minecraft.getInstance();
        int width = PANEL_WIDTH;
        int height = 56;
        drawGlassPanel(graphics, x, y, width, height, component.gatesPowered ? 0xFF80FF72 : 0xFFFFD166, pulse);
        graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.objective")
                .withStyle(ChatFormatting.BOLD), x + 10, y + 8, 0xFFFFFFFF);
        float stationPct = Mth.clamp(component.completedStations / (float) RepairModeState.REQUIRED_REPAIRED_STATIONS, 0.0F, 1.0F);
        drawNeonBar(graphics, x + 10, y + 24, width - 20, 7, stationPct, 0xFF3B2A00,
                component.gatesPowered ? 0xFF80FF72 : 0xFFFFD166);
        Component stationText = Component.translatable("hud.noellesroles.repair.stations", component.completedStations,
                RepairModeState.REQUIRED_REPAIRED_STATIONS);
        graphics.drawString(client.font, stationText, x + 12, y + 36, 0xFFFFE7A3);
        Component pressure = component.gatesPowered
                ? Component.translatable("hud.noellesroles.repair.gates_powered")
                : Component.translatable("hud.noellesroles.repair.pressure", component.downedAllies,
                        component.activeTrialPrisoners);
        graphics.drawString(client.font, pressure, x + 84, y + 36, component.gatesPowered ? 0xFFB9FFB0 : 0xFFFFB4C1);
        if (component.nearestTrialProgress > 0) {
            int seconds = Math.max(0, (RepairModeState.TRIAL_EXECUTION_TICKS - component.nearestTrialProgress) / 20);
            graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.self_trial", seconds),
                    x + 10, y + 48, 0xFFFF6B6B);
        }
    }

    private static Component skillLine(String activeRole) {
        if (activeRole == null || activeRole.isEmpty()) {
            return Component.translatable("hud.noellesroles.repair.skill.selecting");
        }
        return Component.translatable("hud.noellesroles.repair.skill." + activeRole);
    }

    private static int neutralGoal(String activeRole) {
        return switch (activeRole) {
            case "archivist" -> 5;
            case "saboteur" -> 6;
            case "collector" -> 3;
            default -> 0;
        };
    }

    private static int roleAccent(String activeRole) {
        return RepairRoleDefinition.byId(activeRole).map(role -> switch (role.faction) {
            case SURVIVOR -> 0xFF4CC9F0;
            case HUNTER -> 0xFFFF4D6D;
            case NEUTRAL -> 0xFFFFD166;
        }).orElse(0xFF9BF6FF);
    }

    private static void renderMiniMap(FakeGuiGraphics graphics, LocalPlayer player, int x, int y, long tick, float pulse) {
        Minecraft client = Minecraft.getInstance();
        int centerX = x + MAP_SIZE / 2;
        int centerY = y + MAP_SIZE / 2;
        drawRadarShell(graphics, x, y, pulse);
        if (tick >= nextScanTick) {
            rescanBlocks(player, tick + 20);
        }

        float yaw = (player.getYRot() + 180.0F) * Mth.DEG_TO_RAD;
        int sweepX = centerX + (int) (Math.sin((tick * 0.08F)) * (MAP_SIZE / 2 - 9));
        graphics.fill(Math.min(centerX, sweepX), centerY - 1, Math.max(centerX, sweepX), centerY + 1, 0x664CC9F0);
        graphics.fill(centerX - 1, centerY, centerX + 1, centerY + 1, 0xAAFFFFFF);
        int noseX = centerX + (int) (Math.sin(yaw) * 7);
        int noseY = centerY - (int) (Math.cos(yaw) * 7);
        drawDiamond(graphics, centerX, centerY, 5, 0xFFFFFFFF);
        drawDiamond(graphics, noseX, noseY, 3, 0xFFBDEBFF);

        for (MapBlip blip : cachedBlockBlips) {
            drawRelativeBlip(graphics, player, centerX, centerY, blip.x, blip.z, blip.color, blip.size);
        }
        for (Player other : client.level.players()) {
            if (other == player || other.isSpectator()) {
                continue;
            }
            double dx = other.getX() - player.getX();
            double dz = other.getZ() - player.getZ();
            if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                continue;
            }
            int color = other.isCrouching() ? 0xFF8D99AE : 0xFFEAF2FF;
            drawRelativeBlip(graphics, player, centerX, centerY, other.getX(), other.getZ(), color, 3);
        }
        graphics.drawCenteredString(client.font, Component.translatable("hud.noellesroles.repair.minimap"), centerX,
                y + MAP_SIZE + 3, 0xFFBDEBFF);
    }

    private static void rescanBlocks(LocalPlayer player, long nextTick) {
        nextScanTick = nextTick;
        cachedBlockBlips.clear();
        if (player.level() == null) {
            return;
        }
        BlockPos origin = player.blockPosition();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx += 2) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz += 2) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = player.level().getBlockState(pos);
                    if (state.is(ModBlocks.REPAIR_STATION)) {
                        cachedBlockBlips.add(new MapBlip(pos.getX() + 0.5D, pos.getZ() + 0.5D, 0xFF4CC9F0, 3));
                    } else if (state.is(ModBlocks.REPAIR_EXIT_GATE)) {
                        cachedBlockBlips.add(new MapBlip(pos.getX() + 0.5D, pos.getZ() + 0.5D, 0xFF80FF72, 4));
                    } else if (state.is(ModBlocks.HUNTER_CAGE)) {
                        cachedBlockBlips.add(new MapBlip(pos.getX() + 0.5D, pos.getZ() + 0.5D, 0xFFFF4D6D, 4));
                    } else if (state.is(ModBlocks.REPAIR_SUPPLY_CRATE)) {
                        cachedBlockBlips.add(new MapBlip(pos.getX() + 0.5D, pos.getZ() + 0.5D, 0xFFFFD166, 3));
                    } else if (state.is(ModBlocks.REPAIR_PALLET)) {
                        cachedBlockBlips.add(new MapBlip(pos.getX() + 0.5D, pos.getZ() + 0.5D, 0xFFB08968, 2));
                    } else if (state.is(ModBlocks.HUNTER_SNARE)) {
                        cachedBlockBlips.add(new MapBlip(pos.getX() + 0.5D, pos.getZ() + 0.5D, 0xFFFF006E, 3));
                    }
                }
            }
        }
    }

    private static void drawRelativeBlip(FakeGuiGraphics graphics, LocalPlayer player, int centerX, int centerY,
            double worldX, double worldZ, int color, int size) {
        double dx = worldX - player.getX();
        double dz = worldZ - player.getZ();
        double scale = (MAP_SIZE / 2.0D - 10.0D) / SCAN_RADIUS;
        int px = centerX + (int) Mth.clamp(dx * scale, -MAP_SIZE / 2 + 9, MAP_SIZE / 2 - 9);
        int py = centerY + (int) Mth.clamp(dz * scale, -MAP_SIZE / 2 + 9, MAP_SIZE / 2 - 9);
        drawDiamond(graphics, px, py, size, color);
        graphics.fill(px - size - 1, py, px + size + 1, py + 1, (color & 0x00FFFFFF) | 0x55000000);
    }


    private static void renderEventPanel(FakeGuiGraphics graphics, org.agmas.noellesroles.component.RepairRolePlayerComponent component,
            int x, int y, float pulse, long tick) {
        if (component.currentEventKey == null || component.currentEventKey.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        int width = 240;
        int height = 48;
        int danger = Mth.clamp(component.currentEventDanger, 0, 100);
        int accent = danger >= 80 ? 0xFFFF1744 : danger >= 55 ? 0xFFFFD166 : 0xFF4CC9F0;
        drawGlassPanel(graphics, x, y, width, height, accent, pulse);
        graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.event.active")
                .withStyle(ChatFormatting.BOLD), x + 10, y + 7, 0xFFFFFFFF);
        graphics.drawString(client.font, Component.translatable(component.currentEventKey), x + 72, y + 7, accent);
        int seconds = Math.max(0, component.currentEventTicks / 20);
        graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.event.timer", seconds),
                x + width - 56, y + 7, 0xFFE5F6FF);
        drawNeonBar(graphics, x + 10, y + 24, 92, 7, danger / 100.0F, 0xFF2B0710, 0xFFFF1744);
        graphics.drawString(client.font, Component.translatable("hud.noellesroles.repair.event.danger", danger),
                x + 12, y + 35, 0xFFFFB4C1);
        graphics.drawString(client.font, Component.translatable(component.currentEventRewardKey), x + 112, y + 28, 0xFFFFE7A3);
        int sparkX = x + 12 + (int) ((Mth.sin(tick * 0.22F) * 0.5F + 0.5F) * (width - 28));
        drawDiamond(graphics, sparkX, y + 44, 3, (accent & 0x00FFFFFF) | 0xDD000000);
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
                case org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.ATTACK -> 0xFACC15;
                case org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.DOWNED -> 0xDC2626;
                case org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.REVIVED -> 0x22C55E;
                default -> 0xFB7185;
            };
            int color = (alpha << 24) | cueColor;
            int radius = 13 + (int) (pct * 28.0F);
            if (cue.kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.ATTACK) {
                graphics.fill(centerX - radius, centerY - 18, centerX + radius, centerY - 15, color);
                graphics.fill(centerX + radius / 2, centerY - 30, centerX + radius / 2 + 3, centerY + 4, color);
            } else {
                graphics.fill(centerX - radius, centerY - radius / 2, centerX + radius, centerY - radius / 2 + 2, color);
                graphics.fill(centerX - radius, centerY + radius / 2, centerX + radius, centerY + radius / 2 + 2, color);
                graphics.fill(centerX - radius, centerY - radius / 2, centerX - radius + 2, centerY + radius / 2, color);
                graphics.fill(centerX + radius, centerY - radius / 2, centerX + radius + 2, centerY + radius / 2, color);
            }
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
            float ease = 1.0F - (1.0F - in) * (1.0F - in);
            int toastWidth = 168;
            int x = width / 2 - toastWidth / 2 + (int) ((1.0F - ease) * 34.0F);
            int y = height / 4 + index * 26 - (int) ((1.0F - out) * 12.0F);
            int a = (int) (alpha * 255.0F);
            int gold = (a << 24) | 0xFFD166;
            int back = ((int) (alpha * 190.0F) << 24) | 0x17110A;
            graphics.fill(x + 4, y + 4, x + toastWidth + 4, y + 24, ((int) (alpha * 90.0F) << 24));
            graphics.fill(x, y, x + toastWidth, y + 22, back);
            graphics.fill(x, y, x + 4, y + 22, gold);
            float sparkle = 0.5F + 0.5F * Mth.sin((tick + index * 7) * 0.35F);
            drawDiamond(graphics, x + 16, y + 11, 5 + (int) (sparkle * 2), gold);
            graphics.drawString(client.font, Component.literal("+" + toast.amount + " ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    x + 30, y + 4, (a << 24) | 0xFFE6A800);
            graphics.drawString(client.font, toast.source, x + 86, y + 5, (a << 24) | 0xFFF8E9B0);
            index++;
        }
    }

    private static void drawGlassPanel(FakeGuiGraphics graphics, int x, int y, int width, int height, int accent,
            float pulse) {
        graphics.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x66000000);
        graphics.fill(x, y, x + width, y + height, 0xCC071018);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x9913222E);
        graphics.fill(x, y, x + width, y + 2, accent);
        graphics.fill(x, y + height - 2, x + width, y + height, (accent & 0x00FFFFFF) | ((int) (120 + pulse * 80) << 24));
        graphics.fill(x + 6, y + 6, x + 8, y + height - 6, (accent & 0x00FFFFFF) | 0x99000000);
    }

    private static void drawOrb(FakeGuiGraphics graphics, int cx, int cy, int radius, int backColor, int fillColor,
            float pct, float pulse) {
        for (int r = radius; r > 0; r--) {
            int alpha = 20 + (int) (90 * (r / (float) radius));
            graphics.fill(cx - r, cy - r / 2, cx + r, cy + r / 2, (backColor & 0x00FFFFFF) | (alpha << 24));
        }
        int fill = (int) (radius * 2 * pct);
        graphics.fill(cx - radius + 2, cy + radius - fill, cx + radius - 2, cy + radius - 2, fillColor);
        int glow = ((int) (pulse * 110) << 24) | (fillColor & 0x00FFFFFF);
        graphics.fill(cx - radius - 2, cy - 1, cx + radius + 2, cy + 1, glow);
        graphics.fill(cx - 1, cy - radius - 2, cx + 1, cy + radius + 2, glow);
    }

    private static void drawNeonBar(FakeGuiGraphics graphics, int x, int y, int width, int height, float pct,
            int backColor, int fillColor) {
        graphics.fill(x, y, x + width, y + height, 0xAA05090D);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, backColor);
        int filled = (int) ((width - 2) * Mth.clamp(pct, 0.0F, 1.0F));
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fillColor);
        graphics.fill(x + 1, y + height - 2, x + 1 + filled, y + height - 1, 0xBBFFFFFF);
    }

    private static void drawRadarShell(FakeGuiGraphics graphics, int x, int y, float pulse) {
        int centerX = x + MAP_SIZE / 2;
        int centerY = y + MAP_SIZE / 2;
        graphics.fill(x + 4, y + 5, x + MAP_SIZE + 4, y + MAP_SIZE + 5, 0x66000000);
        graphics.fill(x, y, x + MAP_SIZE, y + MAP_SIZE, 0xC0061018);
        graphics.fill(x + 4, y + 4, x + MAP_SIZE - 4, y + MAP_SIZE - 4, 0x8A0D2430);
        graphics.fill(centerX - 1, y + 8, centerX + 1, y + MAP_SIZE - 8, 0x554CC9F0);
        graphics.fill(x + 8, centerY - 1, x + MAP_SIZE - 8, centerY + 1, 0x554CC9F0);
        int ring = (int) (18 + pulse * 16);
        graphics.fill(centerX - ring, centerY - ring, centerX + ring, centerY - ring + 1, 0x664CC9F0);
        graphics.fill(centerX - ring, centerY + ring, centerX + ring, centerY + ring + 1, 0x664CC9F0);
        graphics.fill(centerX - ring, centerY - ring, centerX - ring + 1, centerY + ring, 0x664CC9F0);
        graphics.fill(centerX + ring, centerY - ring, centerX + ring + 1, centerY + ring, 0x664CC9F0);
        graphics.fill(x, y, x + 14, y + 2, 0xFF4CC9F0);
        graphics.fill(x + MAP_SIZE - 14, y + MAP_SIZE - 2, x + MAP_SIZE, y + MAP_SIZE, 0xFF4CC9F0);
    }

    private static void drawDiamond(FakeGuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int i = 0; i <= radius; i++) {
            int span = radius - i;
            graphics.fill(cx - span, cy - i, cx + span + 1, cy - i + 1, color);
            graphics.fill(cx - span, cy + i, cx + span + 1, cy + i + 1, color);
        }
    }

    private record MapBlip(double x, double z, int color, int size) {
    }

    private record CoinToast(int amount, Component source, long createdTick) {
    }

    private record CombatCue(int kind, int entityId, double x, double y, double z, long createdTick) {
    }
}
