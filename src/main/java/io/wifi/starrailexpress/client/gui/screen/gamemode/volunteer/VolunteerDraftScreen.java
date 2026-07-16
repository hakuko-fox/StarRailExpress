package io.wifi.starrailexpress.client.gui.screen.gamemode.volunteer;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.screen.WithParentScreenPauseScreen;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.starrailexpress.content.vote.client.VolunteerCache;
import io.wifi.starrailexpress.game.modes.funny.volunteer.VolunteerDraftState.Phase;
import io.wifi.starrailexpress.network.packet.VolunteerCommitC2SPacket;
import io.wifi.starrailexpress.network.packet.VolunteerDraftSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

public class VolunteerDraftScreen extends Screen {

    private static final int PAD = 12;
    private static final int GAP = 8;
    private static final int CARD_W = 160;
    private static final int CARD_H = 48;
    private static final int ARROW_BTN_SIZE = 16;

    private static final int BG_TOP = 0xF018120A;
    private static final int BG_BOTTOM = 0xF0061018;
    private static final int PANEL_BG_TOP = 0xD81A1008;
    private static final int PANEL_BG_BOTTOM = 0xD80B1722;
    private static final int BORDER = 0xFF8B6914;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int BLUE = 0xFF5EB7D8;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;

    private List<String> currentOrder; // 当前排序的职业 ID 列表
    private int volunteerCount;
    private Button[][] moveButtons; // [志愿位][0=上,1=下,2=随机]
    private Button submitButton;

    // 拖拽状态
    private int draggingIndex = -1;
    private int dragOffsetX, dragOffsetY;

    // 结果阶段数据
    private Map<UUID, String> finalRoles = Map.of();
    private String myFinalRoleId = "";

    public VolunteerDraftScreen() {
        super(Component.translatable("gui.sre.volunteer.title").withStyle(ChatFormatting.GOLD));
    }

    // ========== 初始化 ==========
    @Override
    protected void init() {
        super.init();
        volunteerCount = VolunteerCache.getVolunteerCount();
        if (currentOrder == null || currentOrder.size() != volunteerCount) {
            currentOrder = new ArrayList<>(VolunteerCache.getMyCandidates());
            while (currentOrder.size() < volunteerCount) {
                currentOrder.add("random");
            }
        }
        this.clearWidgets();
        if (VolunteerCache.getPhase() == Phase.COMMIT) {
            buildCommitWidgets();
        }
    }

    private void buildCommitWidgets() {
        moveButtons = new Button[volunteerCount][3];
        for (int i = 0; i < volunteerCount; i++) {
            final int idx = i;
            moveButtons[i][0] = Button.builder(Component.literal("↑"), btn -> moveUp(idx))
                    .bounds(0, 0, ARROW_BTN_SIZE, ARROW_BTN_SIZE).build();
            moveButtons[i][1] = Button.builder(Component.literal("↓"), btn -> moveDown(idx))
                    .bounds(0, 0, ARROW_BTN_SIZE, ARROW_BTN_SIZE).build();
            moveButtons[i][2] = Button.builder(Component.literal("?"), btn -> toggleRandom(idx))
                    .bounds(0, 0, ARROW_BTN_SIZE, ARROW_BTN_SIZE)
                    .tooltip(Tooltip.create(Component.translatable("gui.sre.volunteer.random_tip")))
                    .build();
            addRenderableWidget(moveButtons[i][0]);
            addRenderableWidget(moveButtons[i][1]);
            addRenderableWidget(moveButtons[i][2]);
        }
        submitButton = Button.builder(Component.translatable("gui.sre.volunteer.submit"), btn -> onSubmit())
                .bounds(width / 2 - 50, height - 40, 100, 20).build();
        addRenderableWidget(submitButton);
    }

    private void updateButtonPositions() {
        if (moveButtons == null)
            return;
        int startY = 70;
        int startX = width / 2 - CARD_W / 2;
        for (int i = 0; i < volunteerCount; i++) {
            int y = startY + i * (CARD_H + GAP);
            moveButtons[i][0].setPosition(startX + CARD_W + 4, y);
            moveButtons[i][0].visible = i > 0;
            moveButtons[i][1].setPosition(startX + CARD_W + 4, y + CARD_H - ARROW_BTN_SIZE);
            moveButtons[i][1].visible = i < volunteerCount - 1;
            moveButtons[i][2].setPosition(startX - 24, y + (CARD_H - ARROW_BTN_SIZE) / 2);
        }
        if (submitButton != null) {
            submitButton.setPosition(width / 2 - 50, height - 40);
        }
    }

    public void updateResult() {
        if (VolunteerCache.getPhase() == Phase.RESULT) {
            finalRoles = VolunteerCache.getFinalRoles();
            myFinalRoleId = VolunteerCache.getMyFinalRole();
        }
    }

    // ========== 每帧更新 ==========
    @Override
    public void tick() {
        super.tick();
        if (VolunteerCache.getPhase() == Phase.COMMIT) {
            updateButtonPositions();
        } else if (VolunteerCache.getPhase() == Phase.RESULT && finalRoles.isEmpty()) {
            updateResult();
        }
    }

    // ========== 交互逻辑 ==========
    private void onSubmit() {
        List<Integer> prefs = new ArrayList<>();
        List<String> originals = VolunteerCache.getMyCandidates();
        for (String id : currentOrder) {
            if (id.equals("random")) {
                prefs.add(-1);
            } else {
                int idx = originals.indexOf(id);
                prefs.add(idx >= 0 ? idx : -1);
            }
        }
        ClientPlayNetworking.send(new VolunteerCommitC2SPacket(prefs));
        submitButton.active = false;
        if (moveButtons != null) {
            for (Button[] row : moveButtons) {
                for (Button btn : row)
                    btn.active = false;
            }
        }
    }

    private void moveUp(int index) {
        if (index > 0 && index < volunteerCount) {
            Collections.swap(currentOrder, index, index - 1);
        }
    }

    private void moveDown(int index) {
        if (index < volunteerCount - 1) {
            Collections.swap(currentOrder, index, index + 1);
        }
    }

    private void toggleRandom(int index) {
        if (currentOrder.get(index).equals("random")) {
            List<String> originals = VolunteerCache.getMyCandidates();
            if (index < originals.size()) {
                currentOrder.set(index, originals.get(index));
            }
        } else {
            currentOrder.set(index, "random");
        }
    }

    // ========== 渲染 ==========
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        drawTitleBar(g);

        Phase phase = VolunteerCache.getPhase();
        if (phase == Phase.COMMIT) {
            drawTimer(g);
            drawCards(g, mouseX, mouseY);
            if (draggingIndex >= 0)
                drawDraggedCard(g, mouseX, mouseY);
        } else if (phase == Phase.ADJUST) {
            drawWaiting(g);
        } else if (phase == Phase.RESULT) {
            drawResult(g, mouseX, mouseY);
        }
    }

    private void drawTitleBar(GuiGraphics g) {
        g.drawString(font, title, PAD, 10, GOLD, false);
        Component hint = Component.translatable("gui.sre.volunteer.sort_hint");
        g.drawCenteredString(font, hint, width / 2, 18, MUTED);
    }

    private void drawTimer(GuiGraphics g) {
        int remaining = VolunteerCache.getSmoothRemainingTime();
        int seconds = remaining / 20;
        String timeStr = String.format("%d:%02d", seconds / 60, seconds % 60);
        int color = seconds <= 10 ? RED : seconds <= 30 ? 0xFFFFAA33 : BLUE;
        Component timer = Component.literal(timeStr).withStyle(style -> style.withColor(color).withBold(true));
        g.drawCenteredString(font, timer, width / 2, 38, color);
    }

    private void drawCards(GuiGraphics g, int mouseX, int mouseY) {
        int startY = 70;
        int startX = width / 2 - CARD_W / 2;
        for (int i = 0; i < volunteerCount; i++) {
            if (i == draggingIndex)
                continue;
            int y = startY + i * (CARD_H + GAP);
            boolean hover = inside(mouseX, mouseY, startX, y, CARD_W, CARD_H);
            drawCard(g, startX, y, CARD_W, CARD_H, i, hover);
        }
    }

    private void drawCard(GuiGraphics g, int x, int y, int w, int h, int index, boolean hover) {
        int border = hover ? GOLD : 0xFF5A4530;
        int bgTop = hover ? 0xFF2B2112 : 0xFF1A1008;
        int bgBottom = hover ? 0xFF112536 : 0xFF0B1722;
        g.fillGradient(x, y, x + w, y + h, bgTop, bgBottom);
        g.renderOutline(x, y, w, h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, hover ? GOLD : 0x33FFE8C0);

        String roleId = currentOrder.get(index);
        if (roleId.equals("random")) {
            g.drawCenteredString(font, Component.translatable("gui.sre.volunteer.random"), x + w / 2, y + h / 2 - 4,
                    GOLD);
        } else {
            SRERole role = getRoleByPath(roleId);
            if (role != null) {
                Component name = RoleUtils.getRoleName(role).withColor(role.getColor());
                g.drawCenteredString(font, name, x + w / 2, y + 8, role.getColor() | 0xFF000000);
                Component faction = getFactionText(role);
                g.drawCenteredString(font, faction, x + w / 2, y + 24,
                        faction.getStyle().getColor() != null ? faction.getStyle().getColor().getValue() : TEXT);
            } else {
                g.drawCenteredString(font, roleId, x + w / 2, y + h / 2 - 4, TEXT);
            }
        }
        Component pos = Component.literal("#" + (index + 1)).withStyle(ChatFormatting.GRAY);
        g.drawString(font, pos, x + 4, y + 4, GOLD);
    }

    private void drawDraggedCard(GuiGraphics g, int mouseX, int mouseY) {
        int x = mouseX - dragOffsetX;
        int y = mouseY - dragOffsetY;
        drawCard(g, x, y, CARD_W, CARD_H, draggingIndex, true);
    }

    private void drawWaiting(GuiGraphics g) {
        int remaining = VolunteerCache.getSmoothRemainingTime();
        int sec = remaining / 20;
        Component waitText = Component.translatable("gui.sre.volunteer.calculating", sec);
        g.drawCenteredString(font, waitText, width / 2, height / 2, TEXT);
        String time = String.format("%d:%02d", sec / 60, sec % 60);
        g.drawCenteredString(font, time, width / 2, height / 2 + 16, GOLD);
    }

    private void drawResult(GuiGraphics g, int mouseX, int mouseY) {
        // 左侧玩家列表
        int leftX = PAD;
        int leftY = PAD + 22;
        int panelW = Math.max(160, (int) (width * 0.25));
        int panelH = height - leftY - PAD;
        g.fillGradient(leftX, leftY, leftX + panelW, leftY + panelH, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(leftX, leftY, panelW, panelH, BORDER);
        g.drawString(font, Component.translatable("gui.sre.volunteer.result_list"), leftX + PAD, leftY + PAD, GOLD);
        int y = leftY + PAD + 20;
        for (Map.Entry<UUID, String> e : finalRoles.entrySet()) {
            String name = e.getKey().toString().substring(0, 8);
            SRERole r = getRoleByPath(e.getValue());
            String roleName = r != null ? RoleUtils.getRoleName(r).getString() : e.getValue();
            g.drawString(font, name + ": " + roleName, leftX + PAD, y, TEXT);
            y += 12;
        }

        // 右侧自己的职业详情
        SRERole myRole = getRoleByPath(myFinalRoleId);
        if (myRole != null) {
            int rightX = leftX + panelW + GAP;
            int rightW = width - rightX - PAD;
            int detailY = leftY;
            int detailH = panelH;
            g.fillGradient(rightX, detailY, rightX + rightW, detailY + detailH, PANEL_BG_TOP, PANEL_BG_BOTTOM);
            g.renderOutline(rightX, detailY, rightW, detailH, BORDER);

            Component name = RoleUtils.getRoleName(myRole).withColor(myRole.getColor());
            g.drawString(font, name, rightX + PAD, detailY + PAD, myRole.getColor() | 0xFF000000);
            Component faction = getFactionText(myRole);
            g.drawString(font, faction, rightX + rightW - PAD - font.width(faction), detailY + PAD,
                    faction.getStyle().getColor() != null ? faction.getStyle().getColor().getValue() : TEXT);

            int descY = detailY + PAD + 20;
            int descW = rightW - PAD * 2;
            List<FormattedCharSequence> lines = getRoleIntroLines(myRole, descW);
            for (int i = 0; i < Math.min(lines.size(), 12); i++) {
                g.drawString(font, lines.get(i), rightX + PAD, descY + i * 12, TEXT);
            }
        }
    }

    private List<FormattedCharSequence> getRoleIntroLines(SRERole role, int wrapW) {
        String key = "info.screen.roleid." + role.identifier().getPath();
        String raw = Language.getInstance().getOrDefault(key);
        if (raw.equals(key) || raw.isBlank()) {
            return font.split(RoleUtils.getRoleDescription(role), wrapW);
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String part : raw.split("\\\\n|\\n")) {
            lines.addAll(font.split(Component.literal(part), wrapW));
            lines.add(FormattedCharSequence.EMPTY);
        }
        return lines;
    }

    // ========== 鼠标事件 ==========
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && VolunteerCache.getPhase() == Phase.COMMIT) {
            int startY = 70;
            int startX = width / 2 - CARD_W / 2;
            for (int i = 0; i < volunteerCount; i++) {
                int y = startY + i * (CARD_H + GAP);
                if (inside(mx, my, startX, y, CARD_W, CARD_H)) {
                    draggingIndex = i;
                    dragOffsetX = (int) (mx - startX);
                    dragOffsetY = (int) (my - y);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingIndex >= 0) {
            int startY = 70;
            int startX = width / 2 - CARD_W / 2;
            int targetIndex = -1;
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < volunteerCount; i++) {
                int y = startY + i * (CARD_H + GAP);
                double cx = startX + CARD_W / 2.0;
                double cy = y + CARD_H / 2.0;
                double dist = Math.sqrt(Math.pow(mx - cx, 2) + Math.pow(my - cy, 2));
                if (dist < minDist && i != draggingIndex) {
                    minDist = dist;
                    targetIndex = i;
                }
            }
            if (targetIndex >= 0) {
                Collections.swap(currentOrder, draggingIndex, targetIndex);
            }
            draggingIndex = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return draggingIndex >= 0 || super.mouseDragged(mx, my, btn, dx, dy);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ========== 辅助方法 ==========
    private Component getFactionText(SRERole role) {
        if (role.isInnocent())
            return Component.translatable("display.type.role.innocent").withStyle(s -> s.withColor(0xFF44BB66));
        if (role.canUseKiller())
            return Component.translatable("display.type.role.killer").withStyle(s -> s.withColor(0xFFCC2233));
        if (role.isNeutrals())
            return Component.translatable("display.type.role.neutral_special").withStyle(s -> s.withColor(0xFFCCAA22));
        if (role.isVigilanteTeam())
            return Component.translatable("display.type.role.vigilante").withStyle(s -> s.withColor(0xFF22BBCC));
        return Component.literal("?").withStyle(ChatFormatting.GRAY);
    }

    private SRERole getRoleByPath(String path) {
        if (path == null || path.isBlank())
            return null;
        ResourceLocation id = ResourceLocation.tryParse(path);
        if (id != null) {
            SRERole role = TMMRoles.ROLES.get(id);
            if (role != null)
                return role;
        }
        for (String ns : List.of("noellesroles", "starrailexpress", "trainmurdermystery")) {
            id = ResourceLocation.fromNamespaceAndPath(ns, path);
            SRERole role = TMMRoles.ROLES.get(id);
            if (role != null)
                return role;
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(new WithParentScreenPauseScreen(this, true));
            return true;
        }
        if (NoellesrolesClient.roleIntroClientBind.matches(keyCode, scanCode)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String rolePath = RoleRotationCache.getSelectedRoles().get(mc.player.getUUID());
                SRERole role = getRoleByPath(rolePath);
                if (role != null) {
                    mc.setScreen(new org.agmas.noellesroles.client.screen.RoleIntroduceScreen(this, role));
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}