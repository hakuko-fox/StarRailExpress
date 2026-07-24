package org.agmas.noellesroles.mixin.roles.hakukofox;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class HakukoFoxDeathImmunityMixin {

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void noellesroles$nineLivesImmunity(DamageSource damageSource, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (player.level().isClientSide) return;
        var comp = HakukoFoxPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp != null && comp.tryUseNineLives()) {
            player.setHealth(20.0F);
            ci.cancel();
        }
    }
}
