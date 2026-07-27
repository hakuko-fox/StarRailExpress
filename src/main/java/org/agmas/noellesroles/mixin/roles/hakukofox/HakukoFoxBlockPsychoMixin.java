package org.agmas.noellesroles.mixin.roles.hakukofox;

import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SREPlayerPsychoComponent.class, remap = false)
public abstract class HakukoFoxBlockPsychoMixin {

    @Shadow
    public abstract net.minecraft.world.entity.player.Player getPlayer();

    @Inject(method = "startPsycho_time", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockPsychoInBeastForm(int time, int armour, CallbackInfoReturnable<Boolean> cir) {
        var player = getPlayer();
        HakukoFoxPlayerComponent comp = HakukoFoxPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp != null && comp.isBeastFormActive()) {
            player.displayClientMessage(Component.translatable("skill.noellesroles.hakukofox.no_weapon"), true);
            cir.setReturnValue(false);
        }
    }
}
