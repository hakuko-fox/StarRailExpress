package org.agmas.noellesroles.mixin.roles.hakukofox2;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.hakukofox2.Hakukofox2PlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class Hakukofox2AttackBlockMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockAttackInBeastForm2(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (Hakukofox2PlayerComponent.isDisguised(self)) {
            ci.cancel();
        }
    }
}