package org.agmas.noellesroles.mixin.roles.raven;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 渡鸦猎杀期间持刀时，防止对非目标任务造成左键击退/近战伤害。
 * 仅允许通过刀刺系统击杀正确目标。
 */
@Mixin(ServerPlayer.class)
public abstract class RavenKnifeLeftClickMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void noellesroles$cancelRavenKnifeAttack(Entity target, CallbackInfo ci) {
        ServerPlayer attacker = (ServerPlayer) (Object) this;

        if (!(target instanceof Player targetPlayer)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        if (!gameWorld.isRole(attacker, ModRoles.RAVEN)) {
            return;
        }

        if (!attacker.getItemInHand(InteractionHand.MAIN_HAND).is(TMMItems.KNIFE)) {
            return;
        }

        RavenPlayerComponent raven = ModComponents.RAVEN.get(attacker);
        if (!raven.isHunting()) {
            return;
        }

        // Only allow melee attack on a valid kill target
        if (!raven.canKill(targetPlayer)) {
            ci.cancel();
        }
    }
}
