package org.agmas.noellesroles.mixin.roles.courier;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.agmas.noellesroles.content.entity.PigeonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.agmas.noellesroles.content.item.KnifeItem")
public class PigeonKnifeTargetMixin {
    @Inject(method = "getKnifeTarget", at = @At("HEAD"), cancellable = true)
    private static void allowPigeonTarget(Player user, CallbackInfoReturnable<HitResult> cir) {
        HitResult result = ProjectileUtil.getHitResultOnViewVector(user,
                entity -> entity instanceof PigeonEntity, 4f);
        // 只在原检测没找到玩家目标时才替换为信鸽
        if (result.getType() != HitResult.Type.MISS) {
            cir.setReturnValue(result);
        }
    }
}
