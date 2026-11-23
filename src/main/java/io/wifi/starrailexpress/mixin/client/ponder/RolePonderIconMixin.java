package io.wifi.starrailexpress.mixin.client.ponder;

import io.wifi.starrailexpress.client.ponder.RolePonderIsolation;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RegisteredObjectsHelper.class, remap = false)
public class RolePonderIconMixin {
    @Inject(method = "getItemOrBlock", at = @At("HEAD"), cancellable = true)
    private static void sre$useRoleTutorialIcon(ResourceLocation id, CallbackInfoReturnable<ItemLike> cir) {
        // PonderUI builds its icon from the component ID, including virtual role components.
        if (id != null && RolePonderIsolation.isSyntheticBinding(id.toString())) {
            cir.setReturnValue(Items.PAPER);
        }
    }
}
