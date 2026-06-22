package org.agmas.noellesroles.mixin.roles.courier;

import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.content.entity.PigeonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KnifeStabPayload.Receiver.class)
public class PigeonKnifePayloadMixin {
    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void handlePigeonTarget(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (player.serverLevel().getEntity(payload.target()) instanceof PigeonEntity pigeon) {
            pigeon.hurt(player.damageSources().playerAttack(player), 20.0F);
            ci.cancel();
        }
    }
}
