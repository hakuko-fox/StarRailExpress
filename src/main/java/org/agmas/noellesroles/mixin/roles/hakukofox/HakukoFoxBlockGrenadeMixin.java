package org.agmas.noellesroles.mixin.roles.hakukofox;

import io.wifi.starrailexpress.content.item.GrenadeItem;
import io.wifi.starrailexpress.content.item.StickyGrenadeItem;
import io.wifi.starrailexpress.content.item.TimedGrenadeItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {GrenadeItem.class, StickyGrenadeItem.class, TimedGrenadeItem.class})
public abstract class HakukoFoxBlockGrenadeMixin {

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockGrenadeInBeastForm(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (user instanceof Player player) {
            HakukoFoxPlayerComponent comp = HakukoFoxPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (comp != null && comp.isBeastFormActive()) {
                player.displayClientMessage(Component.translatable("skill.noellesroles.hakukofox.no_weapon"), true);
                ci.cancel();
            }
        }
    }
}
