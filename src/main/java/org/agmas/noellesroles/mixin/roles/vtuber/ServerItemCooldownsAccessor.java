package org.agmas.noellesroles.mixin.roles.vtuber;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ServerItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerItemCooldowns.class)
public interface ServerItemCooldownsAccessor {
    @Accessor("player") ServerPlayer noellesroles$getCooldownOwner();
}
