package org.agmas.noellesroles.mixin.client.roles.hakukofox;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LevelRenderer.class)
public abstract class HakukoFoxPOVManualRenderMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/LightTexture;F)V",
                    shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void noellesroles$renderPOVBody(PoseStack poseStack, float tickDelta, long limitTime, boolean renderBlockOutline,
            Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
            net.minecraft.client.renderer.culling.Frustum frustum,
            CallbackInfo ci) {
        if (!(this.minecraft.cameraEntity instanceof Fox) || this.minecraft.player == null) return;

        double x = this.minecraft.player.xo + (this.minecraft.player.getX() - this.minecraft.player.xo) * tickDelta - camera.getPosition().x;
        double y = this.minecraft.player.yo + (this.minecraft.player.getY() - this.minecraft.player.yo) * tickDelta - camera.getPosition().y;
        double z = this.minecraft.player.zo + (this.minecraft.player.getZ() - this.minecraft.player.zo) * tickDelta - camera.getPosition().z;
        int light = LevelRenderer.getLightColor(this.minecraft.level, this.minecraft.player.blockPosition());
        MultiBufferSource.BufferSource bufferSource = this.minecraft.renderBuffers().bufferSource();
        this.minecraft.getEntityRenderDispatcher().render(this.minecraft.player, x, y, z, this.minecraft.player.getYRot(), tickDelta, poseStack, bufferSource, light);
    }
}
