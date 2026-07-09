package io.wifi.starrailexpress.mixin.client.gui; // 替换为你的包名

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;

import io.wifi.starrailexpress.client.StreamingSpectatorClient;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MouseHandler.class)
public abstract class MoveMouseHandlerMixin {
    @Shadow
    private double xpos;
    @Unique
    private double originalXpos; // 保存原始物理坐标，用于视角移动

    /** 计算左侧面板宽度（物理像素） */
    private static int leftOffset() {
        Window window = Minecraft.getInstance().getWindow();
        int screenWidth = window.getWidth();
        return (int) ((double) screenWidth / 4);
    }

    /**
     * 在 onMove 中，将存储的 xpos 变为偏移后的 GUI 坐标，
     * 同时保存原始值用于视角移动。
     */
    @Inject(method = "onMove", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;xpos:D", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void offsetStoredXpos(long window, double x, double y, CallbackInfo ci) {
        if (StreamingSpectatorClient.isActive()) {
            this.originalXpos = this.xpos; // 此时 xpos 已经是原始值（刚赋值）
            this.xpos = originalXpos - leftOffset();
        }
    }

    // 更简洁的做法：在 handleAccumulatedMovement 开头保存当前 xpos，
    // 然后在需要的地方替换回 originalXpos。
    @Unique
    private double backupXpos;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void fixXposForMovement(CallbackInfo ci) {
        if (StreamingSpectatorClient.isActive()) {
            backupXpos = this.xpos;
            this.xpos = this.originalXpos; // 视角计算期间使用原始坐标
        }
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("RETURN"))
    private void restoreXposAfterMovement(CallbackInfo ci) {
        if (StreamingSpectatorClient.isActive()) {
            this.xpos = backupXpos;
        }
    }

}