package org.agmas.noellesroles.mixin.roles.adventurer;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocent.adventurer.AdventurerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts environmental force-kills for the Adventurer role.
 * When an Adventurer would die from an environmental reason, an immunity charge
 * is consumed instead, and the death is cancelled.
 */
@Mixin(value = GameMode.class, remap = false)
public abstract class AdventurerDeathImmunityMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;Z"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/resources/ResourceLocation;Z)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void noellesroles$adventurerDeathImmunity(
            Player victim, boolean spawnBody, Player _killer,
            ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
        if (!forceDeath) return;
        if (!AdventurerPlayerComponent.isEnvironmentalDeath(deathReason)) return;
        if (!(victim instanceof ServerPlayer sp)) return;

        var gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorld == null || !gameWorld.isRole(sp, ModRoles.ADVENTURER)) return;

        AdventurerPlayerComponent adv = ModComponents.ADVENTURER.get(sp);
        if (adv == null) return;

        if (adv.consumeImmunity(deathReason)) {
            ci.cancel();
        }
    }
}
