package org.agmas.noellesroles.mixin.roles.vtuber;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ServerItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 根据实际使用武器的玩家调整服务端冷却，并沿用原版同步。 */
@Mixin(ItemCooldowns.class)
public abstract class VtuberWeaponCooldownMixin {
    @ModifyVariable(method = "addCooldown", at = @At("HEAD"), argsOnly = true)
    private int noellesroles$adjustWeaponCooldown(int ticks, Item item, int originalTicks) {
        if (!((Object) this instanceof ServerItemCooldownsAccessor accessor)) return ticks;
        return org.agmas.noellesroles.role.vtuber.VtuberWeaponCooldowns.adjust(
                accessor.noellesroles$getCooldownOwner(), item, ticks);
    }
}
