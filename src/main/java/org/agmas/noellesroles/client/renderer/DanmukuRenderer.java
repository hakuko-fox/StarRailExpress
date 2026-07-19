package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.DanmukuEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

public class DanmukuRenderer extends EntityRenderer<DanmukuEntity> {

    private final ItemRenderer itemRenderer;

    public DanmukuRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(DanmukuEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
            return;
        }

        poseStack.pushPose();

        // 使物品始终面向摄像机（就像掉落物或物品展示框一样）
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        // 缩放可保留，也可以按需调整
        poseStack.scale(1.4f, 1.4f, 1.4f);

        this.itemRenderer.renderStatic(
                ModItems.DANMUKU.getDefaultInstance(),
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.level(),
                entity.getId());

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(DanmukuEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}