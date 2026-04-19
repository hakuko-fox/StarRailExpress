package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin {
    @WrapMethod(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V")
    protected void tmm$disableNameTags(AbstractClientPlayer abstractClientPlayerEntity, Component text,
            PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, float f, Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(abstractClientPlayerEntity, text, matrixStack, vertexConsumerProvider, i, f);
        }
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void tmm$psychoSkinTexture(
            AbstractClientPlayer abstractClientPlayerEntity, CallbackInfoReturnable<ResourceLocation> cir) {
        if (SREClient.gameComponent == null)
            return;
        var result = OnGettingPlayerSkin.EVENT.invoker().onGetSkin(abstractClientPlayerEntity);
        if (result == OnGettingPlayerSkin.PlayerSkinResult.DEFAULT) {
            return;
        } else if (result != null && result != OnGettingPlayerSkin.PlayerSkinResult.SKIP) {
            cir.setReturnValue(result.texture);
            cir.cancel();
            return;
        }
        // 获取普通状态下职业皮肤
        SRERole role = SREClient.gameComponent.getRole(abstractClientPlayerEntity.getUUID());
        PlayerSkin.Model model = abstractClientPlayerEntity.getSkin().model();
        boolean isSLIM = (model == PlayerSkin.Model.SLIM);
        if (role != null) {
            ResourceLocation rolenormalskinresult = role.getNormalSkin(abstractClientPlayerEntity, isSLIM);
            if (rolenormalskinresult != null) {
                cir.setReturnValue(rolenormalskinresult);
                cir.cancel();
                return;
            }
        }
        // 获取疯魔状态下职业皮肤
        if (SREClient.PLAYER_PSYCHO_CACHE.getOrDefault(abstractClientPlayerEntity.getUUID(), false)) {
            String suffix = isSLIM ? "_thin" : "";
            ResourceLocation texture = SRE.watheId("textures/entity/psycho" + suffix + ".png");
            if (role != null) {
                var res = role.getPsychoSkin(abstractClientPlayerEntity, isSLIM);
                if (res != null) {
                    texture = res;
                }
            }
            cir.setReturnValue(texture);
            cir.cancel();
        }
    }

    @ModifyVariable(method = "renderHand", at = @At("STORE"), ordinal = 0)
    private ResourceLocation tmm$psychoArmTexture(ResourceLocation skinTexture) {
        if (Minecraft.getInstance().player != null && SREClient.localPlayerPsychoActive) {
            PlayerSkin.Model model = Minecraft.getInstance().player.getSkin().model();
            String suffix = model == PlayerSkin.Model.SLIM ? "_thin" : "";
            return SRE.watheId("textures/entity/psycho" + suffix + ".png");
        }
        return skinTexture;
    }
}
