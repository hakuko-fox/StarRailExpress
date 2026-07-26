package org.agmas.noellesroles.mixin.roles.hakukofox;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SRERole.class)
public abstract class HakukoFoxBlockWeaponMixin {

    @Inject(method = "onUseGun", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockGun(Player player, CallbackInfoReturnable<Boolean> cir) {
        HakukoFoxPlayerComponent comp = HakukoFoxPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp != null && comp.isBeastFormActive()) {
            player.displayClientMessage(Component.translatable("skill.noellesroles.hakukofox.no_weapon"), true);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onUseKnife", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockKnife(Player player, CallbackInfoReturnable<Boolean> cir) {
        HakukoFoxPlayerComponent comp = HakukoFoxPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp != null && comp.isBeastFormActive()) {
            player.displayClientMessage(Component.translatable("skill.noellesroles.hakukofox.no_weapon"), true);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onUseDerringer", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockDerringer(Player player, CallbackInfoReturnable<Boolean> cir) {
        HakukoFoxPlayerComponent comp = HakukoFoxPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp != null && comp.isBeastFormActive()) {
            player.displayClientMessage(Component.translatable("skill.noellesroles.hakukofox.no_weapon"), true);
            cir.setReturnValue(false);
        }
    }
}
