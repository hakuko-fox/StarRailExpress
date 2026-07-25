package org.agmas.noellesroles.mixin.client.roles.hakukofox;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.animal.Fox;
import org.agmas.noellesroles.client.HakukoFoxDisguiseRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class HakukoFoxSelfRenderMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean noellesroles$renderSelfWhileDisguised(Camera camera, Operation<Boolean> original) {
        if (original.call(camera)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.cameraEntity instanceof Fox fox && fox.getVariant() == Fox.Type.SNOW) {
                return false;
            }
            return true;
        }
        return camera.getEntity() instanceof AbstractClientPlayer player
                && HakukoFoxDisguiseRenderer.shouldDisguise(player);
    }
}
