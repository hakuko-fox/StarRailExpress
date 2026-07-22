package org.agmas.noellesroles.mixin.client.roles.hakukofox;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.agmas.noellesroles.client.HakukoFoxDisguiseRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class HakukoFoxSelfRenderMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean noellesroles$renderSelfWhileDisguised(Camera camera, Operation<Boolean> original) {
        if (original.call(camera)) {
            return true;
        }
        return camera.getEntity() instanceof AbstractClientPlayer player
                && HakukoFoxDisguiseRenderer.shouldDisguise(player);
    }
}
