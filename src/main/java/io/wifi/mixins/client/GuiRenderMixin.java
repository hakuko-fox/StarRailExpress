package io.wifi.mixins.client;

import io.wifi.utils.client.betterrender.OptimizedTextRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Manages the frame lifecycle of OptimizedTextRenderer.
 *
 * Also gates HUD render logic to tick rate:
 * when isTickDirty() is false, the HUD lambda is skipped entirely
 * and the cached entries from the last tick are replayed instead.
 */
@Mixin(Gui.class)
public class GuiRenderMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics graphics, DeltaTracker partialTick, CallbackInfo ci) {
        OptimizedTextRenderer.INSTANCE.beginFrame(graphics);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphics graphics, DeltaTracker partialTick, CallbackInfo ci) {
        OptimizedTextRenderer.INSTANCE.endFrame();
    }
}