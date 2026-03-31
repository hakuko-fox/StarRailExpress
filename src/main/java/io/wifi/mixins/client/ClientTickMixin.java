package io.wifi.mixins.client;

import io.wifi.utils.client.betterrender.OptimizedTextRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the text renderer dirty once per game tick so HUD
 * computation only runs at 20 Hz instead of every render frame.
 */
@Mixin(Minecraft.class)
public class ClientTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        OptimizedTextRenderer.INSTANCE.markTickDirty();
    }
}