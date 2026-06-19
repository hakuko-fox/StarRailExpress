package io.wifi.starrailexpress.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.data.PlayerPlushSkinCache;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.client.model.entity.CustomPlayerPlushModel;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义玩家 plush 的物品渲染器：让手持 / 背包 / 掉落物也按绑定玩家的皮肤渲染同一个 plush 模型。
 * <p>
 * 配套物品模型为 {@code builtin/entity}（见 {@code custom_player_plush} 物品模型），各场景的朝向 / 缩放
 * 由该模型的 {@code display} 变换负责；这里只把模型画在 {@code [0,1]^3}（与方块渲染同一套坐标变换）。
 */
public class CustomPlayerPlushItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private CustomPlayerPlushModel model;

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        if (this.model == null) {
            this.model = new CustomPlayerPlushModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(TMMModelLayers.CUSTOM_PLAYER_PLUSH));
        }
        String name = stack.get(SREDataComponentTypes.PLUSH_PLAYER);
        ResourceLocation texture = PlayerPlushSkinCache.getTexture(name);

        poseStack.pushPose();
        // 与方块实体渲染器相同：把模型放进 [0,1]^3（脚在 y=0、头顶 y=1，居中），ModelPart 内部已 ÷16
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.scale(1.0F, -1.0F, 1.0F);
        // 手持时（第一/第三人称）让脸朝向玩家：模型正面默认 +z（背对持有者），转 180° 面向玩家
        if (isHandContext(mode)) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        this.model.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static boolean isHandContext(ItemDisplayContext mode) {
        return mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || mode == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }
}
