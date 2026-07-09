package io.wifi.starrailexpress.mixin.client.gui;

import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;

import io.wifi.starrailexpress.client.StreamingSpectatorClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

@Mixin(value = GameRenderer.class, priority = 1)
public class MoveRenderArea {
    @Unique
    private boolean sre$haveShifted = false;

    /**
     * 在世界渲染前将视口限制为右侧 3/4 区域，
     * 手部渲染也会跟随此视口，因此左侧不会出现世界/手臂画面。
     */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void offsetWorldViewport(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (StreamingSpectatorClient.isActive()) {
            Window window = Minecraft.getInstance().getWindow();
            int leftOffset = window.getWidth() / 4;
            RenderSystem.viewport(leftOffset, 0, window.getWidth() - leftOffset, window.getHeight());
        }
    }

    /**
     * 世界及手部渲染结束后恢复全屏视口，避免影响后续 GUI 等渲染。
     */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void restoreWorldViewport(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (StreamingSpectatorClient.isActive()) {
            Window window = Minecraft.getInstance().getWindow();
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;translation(FFF)Lorg/joml/Matrix4f;", shift = At.Shift.AFTER))
    public void sre$startRender(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        if (StreamingSpectatorClient.isActive()) {
            sre$haveShifted = true;
            Window window = Minecraft.getInstance().getWindow();

            int magicVarInt = (int) ((double) window.getWidth() / window.getGuiScale());
            int screenWidth = (double) window.getWidth() / window.getGuiScale() > (double) magicVarInt ? magicVarInt + 1
                    : magicVarInt;
            int leftOffset = screenWidth / 4;

            // 模型视图矩阵向右平移
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.translate(leftOffset, 0, 0);
            RenderSystem.applyModelViewMatrix();
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private void restoreGui(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        if (sre$haveShifted) {
            RenderSystem.disableScissor();
        }
    }
}
