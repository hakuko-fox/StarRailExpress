package org.agmas.noellesroles.mixin.roles.hakukofox;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class HakukoFoxEyeHeightMixin {

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions noellesroles$lowerEyeToFox(EntityDimensions dimensions, Pose pose) {
        Player self = (Player) (Object) this;
        if (!HakukoFoxPlayerComponent.isDisguised(self)) {
            return dimensions;
        }
        float eyeHeight = Math.min(dimensions.eyeHeight(), 0.6F);
        return dimensions.withEyeHeight(eyeHeight);
    }
}
