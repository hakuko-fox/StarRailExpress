package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.MovingPlatformConfigC2SPacket;
import org.jetbrains.annotations.NotNull;

/**
 * 移动方块配置 GUI（创造模式专用）
 * 可设置：移动距离（1-50格）、移动速度（0.01-1.0）、碰撞箱大小（0.5-3.0）
 */
public class MovingPlatformConfigScreen extends Screen {

    private final BlockPos blockPos;
    private final int currentDistance;
    private final double currentSpeed;
    private final double currentCollisionSize;

    private EditBox distanceInput;
    private EditBox speedInput;
    private EditBox collisionInput;
    private Button saveButton;

    public MovingPlatformConfigScreen(BlockPos blockPos, int distance, double speed, double collisionSize) {
        super(Component.literal("移动方块配置"));
        this.blockPos = blockPos;
        this.currentDistance = distance;
        this.currentSpeed = speed;
        this.currentCollisionSize = collisionSize;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int startY = height / 2 - 60;

        // 距离输入
        distanceInput = new EditBox(font, centerX - 80, startY, 60, 20, Component.literal("距离"));
        distanceInput.setValue(String.valueOf(currentDistance));
        addRenderableWidget(distanceInput);

        // 速度输入
        speedInput = new EditBox(font, centerX - 80, startY + 30, 60, 20, Component.literal("速度"));
        speedInput.setValue(String.valueOf(currentSpeed));
        addRenderableWidget(speedInput);

        // 碰撞箱大小输入
        collisionInput = new EditBox(font, centerX - 80, startY + 60, 60, 20, Component.literal("碰撞箱"));
        collisionInput.setValue(String.valueOf(currentCollisionSize));
        addRenderableWidget(collisionInput);

        // 保存按钮
        saveButton = Button.builder(Component.literal("保存"), btn -> saveConfig())
                .bounds(centerX - 30, startY + 90, 60, 20).build();
        addRenderableWidget(saveButton);
    }

    private void saveConfig() {
        int distance;
        double speed, collisionSize;
        try {
            distance = Integer.parseInt(distanceInput.getValue());
            speed = Double.parseDouble(speedInput.getValue());
            collisionSize = Double.parseDouble(collisionInput.getValue());
        } catch (NumberFormatException e) {
            return;
        }

        distance = Math.clamp(distance, 1, 50);
        speed = Math.clamp(speed, 0.01, 1.0);
        collisionSize = Math.clamp(collisionSize, 0.5, 3.0);

        ClientPlayNetworking.send(new MovingPlatformConfigC2SPacket(blockPos, distance, speed, collisionSize));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);

        int centerX = width / 2;
        int startY = height / 2 - 60;

        g.drawCenteredString(font, title, centerX, startY - 20, 0xFFFFFF);
        g.drawString(font, "移动距离 (1-50):", centerX - 80, startY - 12, 0xAAAAAA);
        g.drawString(font, "格", centerX - 14, startY + 6, 0x888888);
        g.drawString(font, "移动速度 (0.01-1.0):", centerX - 80, startY + 18, 0xAAAAAA);
        g.drawString(font, "碰撞箱 (0.5-3.0):", centerX - 80, startY + 48, 0xAAAAAA);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
