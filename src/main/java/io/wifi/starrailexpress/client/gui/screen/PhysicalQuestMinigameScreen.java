package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

public class PhysicalQuestMinigameScreen extends Screen {
    public enum Kind {
        EXTINGUISH("extinguish_fire"),
        PACHINKO("pachinko"),
        MIX_DRINK("mix_drink"),
        BALLOON_PUMP("balloon_pump"),
        THROW_BALL("throw_ball");

        final String id;
        Kind(String id) { this.id = id; }
    }

    private static final int W = 430;
    private static final int H = 270;
    private static final int PANEL = 0xEE101722;
    private static final int LINE = 0xFF526172;
    private static final int WHITE = 0xFFEAF0F8;
    private static final int MUTED = 0xFF9AA8B8;
    private static final int GREEN = 0xFF63D471;
    private static final int YELLOW = 0xFFFFD166;

    private final Runnable onSuccess;
    private final Kind kind;
    private int ticks;
    private int successTicks = -1;

    private float fire = 1f;
    private boolean extinguisherHeld;
    private double lastMouseX;
    private double lastMouseY;

    private float plunger = 0f;
    private boolean plungerHeld;
    private boolean pachinkoBallActive;
    private float px, py, pvx, pvy;
    private int pachinkoScore;

    private final List<DragItem> ingredients = new ArrayList<>();
    private DragItem dragging;
    private boolean cupLoaded;
    private int shakeTurns;
    private float lastShakeY;
    private boolean pouring;
    private int pourTicks;

    private float balloon = 0.18f;
    private boolean pumpHeld;
    private float pumpY;

    private boolean birdHeld;
    private boolean birdFlying;
    private float bx, by, bvx, bvy;
    private int stableInBucketTicks;

    public PhysicalQuestMinigameScreen(BlockPos pos, Runnable onSuccess, Kind kind) {
        super(Component.translatable("minigame.starrailexpress." + kind.id));
        this.onSuccess = onSuccess;
        this.kind = kind;
    }

    @Override
    protected void init() {
        ingredients.clear();
        dragging = null;
        cupLoaded = false;
        shakeTurns = 0;
        lastShakeY = 0;
        pouring = false;
        pourTicks = 0;
        fire = 1f;
        pachinkoScore = 0;
        pachinkoBallActive = false;
        balloon = 0.18f;
        pumpY = top() + 90;
        birdHeld = false;
        birdFlying = false;
        stableInBucketTicks = 0;
        int l = left(), t = top();
        ingredients.add(new DragItem(new ItemStack(Items.ICE), l + 55, t + 190));
        ingredients.add(new DragItem(new ItemStack(Items.KELP), l + 105, t + 190));
        ingredients.add(new DragItem(new ItemStack(Items.GOLDEN_APPLE), l + 155, t + 190));
        bx = l + W - 65;
        by = t + 96;
    }

    @Override
    public void tick() {
        ticks++;
        if (successTicks >= 0 && ++successTicks > 14) {
            minecraft.setScreen(null);
            return;
        }
        switch (kind) {
            case PACHINKO -> tickPachinko();
            case MIX_DRINK -> { if (pouring && ++pourTicks > 36) complete(); }
            case THROW_BALL -> tickThrowBall();
            default -> {}
        }
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();
        g.fill(l, t, l + W, t + H, PANEL);
        g.renderOutline(l, t, W, H, LINE);
        g.drawString(font, title, l + 12, t + 10, WHITE);
        switch (kind) {
            case EXTINGUISH -> renderExtinguish(g, l, t, mouseX, mouseY);
            case PACHINKO -> renderPachinko(g, l, t);
            case MIX_DRINK -> renderMixDrink(g, l, t);
            case BALLOON_PUMP -> renderBalloon(g, l, t);
            case THROW_BALL -> renderThrowBall(g, l, t, mouseX, mouseY);
        }
        if (successTicks >= 0) {
            g.fill(l, t, l + W, t + H, 0xAA102414);
            g.drawCenteredString(font, Component.translatable("minigame.starrailexpress.common.done"), width / 2, t + 128, GREEN);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderExtinguish(GuiGraphics g, int l, int t, int mx, int my) {
        int cx = l + 190, cy = t + 140;
        for (int i = 0; i < 4; i++) {
            int r = (int) (22 + fire * (42 - i * 5));
            int c = i % 2 == 0 ? 0xFFFF6B22 : 0xFFFFD166;
            fillCircle(g, cx + (i - 2) * 10, cy - i * 8, r, c);
        }
        ItemStack ext = new ItemStack(ModItems.EXTINGUISHER);
        g.renderItem(ext, l + 340, t + 118);
        if (extinguisherHeld) {
            g.renderItem(ext, mx - 8, my - 8);
            g.fill(mx - 40, my - 4, cx, cy, 0x99DDEEFF);
        }
        bar(g, l + 110, t + 224, 210, fire, 0xFFFF6B22);
    }

    private void renderPachinko(GuiGraphics g, int l, int t) {
        g.drawString(font, Component.literal(pachinkoScore + " / 20"), l + 20, t + 36, YELLOW);
        int[] xs = {92, 138, 184, 230, 276, 322};
        int[] scores = {5, 10, 15, 15, 10, 5};
        for (int row = 0; row < 4; row++) {
            for (int i = 0; i <= row + 1; i++) fillCircle(g, l + 100 + i * 62 - row * 18, t + 70 + row * 38, 7, LINE);
        }
        for (int i = 0; i < xs.length; i++) g.drawCenteredString(font, Component.literal(String.valueOf(scores[i])), l + xs[i], t + 238, WHITE);
        g.fill(l + 356, t + 72, l + 366, t + 220, 0xFF3C4652);
        g.fill(l + 350, (int)(t + 94 + plunger * 96), l + 372, (int)(t + 112 + plunger * 96), 0xFFB58B4A);
        if (!pachinkoBallActive) fillCircle(g, l + 322, t + 82, 8, 0xFFE6EEF8);
        else fillCircle(g, Math.round(px), Math.round(py), 8, 0xFFE6EEF8);
    }

    private void renderMixDrink(GuiGraphics g, int l, int t) {
        for (DragItem item : ingredients) g.renderItem(item.stack, Math.round(item.x), Math.round(item.y));
        cup(g, l + 270, t + 92, 48, 80, cupLoaded ? 0x663DDC84 : 0);
        cup(g, l + 340, t + 112, 36, 58, pouring ? 0x66FFD54F : 0);
        if (pouring) g.fill(l + 316, t + 132, l + 346, t + 136 + Math.min(28, pourTicks), 0xCCFFD54F);
        g.drawCenteredString(font, Component.literal(shakeTurns + " / 3"), l + 294, t + 72, YELLOW);
        if (dragging != null) g.renderItem(dragging.stack, (int)lastMouseX - 8, (int)lastMouseY - 8);
    }

    private void renderBalloon(GuiGraphics g, int l, int t) {
        fillCircle(g, l + 150, t + 132, 48, 0x44FF6B91);
        g.renderOutline(l + 102, t + 84, 96, 96, 0x99FFADC1);
        fillCircle(g, l + 150, t + 132, Math.round(48 * balloon), 0xFFFF5C8A);
        g.fill(l + 250, t + 78, l + 296, t + 218, 0xFF3D4652);
        g.fill(l + 238, (int)pumpY, l + 308, (int)pumpY + 12, 0xFFE8EEF8);
        g.fill(l + 271, t + 55, l + 278, (int)pumpY, 0xFFE8EEF8);
        bar(g, l + 110, t + 226, 210, balloon, 0xFFFF5C8A);
    }

    private void renderThrowBall(GuiGraphics g, int l, int t, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(l + 80, t + 150, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45));
        g.fill(-30, -20, 30, 20, 0xFF8A5A32);
        g.renderOutline(-30, -20, 60, 40, 0xFFB78248);
        g.pose().popPose();
        g.fill(l + 330, t + 170, l + 390, t + 220, 0xFF7B5536);
        float rx = birdHeld ? mx : bx, ry = birdHeld ? my : by;
        fillCircle(g, Math.round(rx), Math.round(ry), 15, 0xFFFF4E45);
        fillCircle(g, Math.round(rx + 6), Math.round(ry - 5), 4, WHITE);
        fillCircle(g, Math.round(rx + 7), Math.round(ry - 5), 2, 0xFF111111);
    }

    private void tickPachinko() {
        if (!pachinkoBallActive) return;
        px += pvx; py += pvy; pvy += 0.22f;
        int l = left(), t = top();
        if (px < l + 25 || px > l + 330) pvx *= -0.75f;
        for (int row = 0; row < 4; row++) {
            for (int i = 0; i <= row + 1; i++) {
                float ox = l + 100 + i * 62 - row * 18, oy = t + 70 + row * 38;
                if (dist(px, py, ox, oy) < 16) { pvx += (px - ox) * 0.06f; pvy = -Math.abs(pvy) * 0.55f; }
            }
        }
        if (py > t + 230) {
            int slot = Mth.clamp((int)((px - (l + 70)) / 52), 0, 5);
            pachinkoScore += new int[]{5,10,15,15,10,5}[slot];
            pachinkoBallActive = false;
            if (pachinkoScore >= 20) complete();
        }
    }

    private void tickThrowBall() {
        if (!birdFlying) return;
        bx += bvx; by += bvy; bvy += 0.25f; bvx *= 0.995f; bvy *= 0.995f;
        int l = left(), t = top();
        boolean inBucket = bx > l + 35 && bx < l + 125 && by > t + 110 && by < t + 185;
        if (inBucket) {
            bvx *= 0.86f; bvy *= -0.35f;
            stableInBucketTicks = Math.abs(bvx) + Math.abs(bvy) < 1.2f ? stableInBucketTicks + 1 : 0;
            if (stableInBucketTicks > 30) complete();
        }
        if (by > t + H + 30 || bx < l - 50) init();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || successTicks >= 0) return super.mouseClicked(mouseX, mouseY, button);
        int l = left(), t = top();
        switch (kind) {
            case EXTINGUISH -> extinguisherHeld = inRect(mouseX, mouseY, l + 330, t + 106, 45, 45);
            case PACHINKO -> plungerHeld = inRect(mouseX, mouseY, l + 340, t + 70, 45, 155);
            case MIX_DRINK -> {
                for (DragItem item : ingredients) if (!item.used && inRect(mouseX, mouseY, item.x, item.y, 24, 24)) { dragging = item; break; }
            }
            case BALLOON_PUMP -> pumpHeld = inRect(mouseX, mouseY, l + 238, pumpY, 70, 18);
            case THROW_BALL -> {
                if (!birdFlying && dist((float)mouseX, (float)mouseY, bx, by) < 22) birdHeld = true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        int l = left(), t = top();
        if (kind == Kind.EXTINGUISH && extinguisherHeld && mouseX < l + 250 && mouseDown()) {
            fire -= 0.012f;
            if (fire <= 0) complete();
        }
        if (kind == Kind.PACHINKO && plungerHeld) {
            plunger = Mth.clamp(((float)mouseY - (t + 94)) / 96f, 0f, 1f);
        }
        if (kind == Kind.MIX_DRINK && dragging == null && cupLoaded && inRect(mouseX, mouseY, l + 260, t + 80, 70, 100)) {
            if (Math.abs((float)mouseY - lastShakeY) > 42) { shakeTurns++; lastShakeY = (float)mouseY; }
        }
        if (kind == Kind.BALLOON_PUMP && pumpHeld) {
            float old = pumpY;
            pumpY = Mth.clamp((float)mouseY, t + 55, t + 155);
            if (pumpY - old > 10) balloon = Math.min(1f, balloon + 0.09f);
            if (balloon >= 0.96f) complete();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int l = left(), t = top();
        if (kind == Kind.PACHINKO && plungerHeld && !pachinkoBallActive) {
            pachinkoBallActive = true; px = l + 322; py = t + 82; pvx = -2.5f - plunger * 3.5f; pvy = -1f - plunger * 2f; plunger = 0;
        }
        if (kind == Kind.MIX_DRINK && dragging != null) {
            if (inRect(mouseX, mouseY, l + 252, t + 72, 90, 120)) { dragging.used = true; cupLoaded = ingredients.stream().allMatch(i -> i.used); }
            dragging = null;
        } else if (kind == Kind.MIX_DRINK && cupLoaded && shakeTurns >= 3 && inRect(mouseX, mouseY, l + 330, t + 104, 70, 90)) {
            pouring = true;
        }
        if (kind == Kind.THROW_BALL && birdHeld) {
            birdHeld = false; birdFlying = true; bvx = (bx - (float)mouseX) * 0.16f; bvy = (by - (float)mouseY) * 0.16f;
        }
        extinguisherHeld = false; plungerHeld = false; pumpHeld = false;
        return true;
    }

    private boolean mouseDown() { return true; }

    private void complete() {
        if (successTicks >= 0) return;
        successTicks = 0;
        onSuccess.run();
    }

    private static boolean inRect(double x, double y, double rx, double ry, double rw, double rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }
    private static float dist(float ax, float ay, float bx, float by) { float dx = ax - bx, dy = ay - by; return (float)Math.sqrt(dx * dx + dy * dy); }

    private void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int hw = (int)Math.sqrt(r * r - y * y);
            g.fill(cx - hw, cy + y, cx + hw + 1, cy + y + 1, color);
        }
    }

    private void bar(GuiGraphics g, int x, int y, int w, float value, int color) {
        g.fill(x, y, x + w, y + 8, 0xFF25303C);
        g.fill(x, y, x + Math.round(w * Mth.clamp(value, 0f, 1f)), y + 8, color);
        g.renderOutline(x, y, w, 8, LINE);
    }

    private void cup(GuiGraphics g, int x, int y, int w, int h, int liquid) {
        g.fill(x, y, x + w, y + 4, WHITE);
        g.fill(x + 6, y + h, x + w - 6, y + h + 4, WHITE);
        g.fill(x + 5, y + 4, x + 9, y + h, WHITE);
        g.fill(x + w - 9, y + 4, x + w - 5, y + h, WHITE);
        if (liquid != 0) g.fill(x + 10, y + h - 28, x + w - 10, y + h - 6, liquid);
    }

    private static final class DragItem {
        final ItemStack stack;
        final float x;
        final float y;
        boolean used;
        DragItem(ItemStack stack, float x, float y) { this.stack = stack; this.x = x; this.y = y; }
    }
}
