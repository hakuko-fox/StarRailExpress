package org.agmas.noellesroles.mixin.client.roles.bodymaker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.client.KeyMapping;
import org.agmas.noellesroles.client.widget.BodymakerRoleWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 防止葬仪在输入角色名时误关物品栏的Mixin
 */
@Mixin(LimitedHandledScreen.class)
public class BodymakerDoNotCloseMixin {

    @WrapOperation(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;matchesKey(II)Z", ordinal = 0))
    boolean doNotCloseInventory(KeyMapping instance, int keyCode, int scanCode, Operation<Boolean> original) {
        // 如果正在输入角色名，不允许关闭物品栏
        if (BodymakerRoleWidget.stopClosing) {
            return false;
        }
        return original.call(instance, keyCode, scanCode);
    }

    @Inject(method = "close", at = @At("HEAD"))
    void resetStopClosing(CallbackInfo ci) {
        BodymakerRoleWidget.stopClosing = false;
    }
}
