package org.agmas.noellesroles.mixin.roles.hakukofox;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class HakukoFoxAttackBlockMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockAttackInBeastForm(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (HakukoFoxPlayerComponent.isDisguised(self)
                || (self.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)
                        && SREGameWorldComponent.KEY.get(self.level()).isRole(self, ModRoles.YOZORA))) {
            ci.cancel();
        }
    }
}
