package io.wifi.starrailexpress.mixin.client.gui;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.Window;

import io.wifi.starrailexpress.client.StreamingSpectatorClient;

@Mixin(Window.class)
public class ModifyWindowWidth {
    @WrapMethod(method = "getGuiScaledWidth")
    public int sre$moveGui(Operation<Integer> original) {
        int originalValue = original.call();
        if (StreamingSpectatorClient.isActive()) {
            return (int) ((float) originalValue / 4f * 3f);
        }
        return originalValue;
    }
}
