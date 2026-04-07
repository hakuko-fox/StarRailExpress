package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.fourthroom.block.FourthRoomTableBlock;
import io.wifi.starrailexpress.fourthroom.block.FourthRoomTableBlockEntity;
import io.wifi.starrailexpress.fourthroom.effect.TableEffectEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.NotNull;

public class FourthRoomTableBlockEntityRenderer implements BlockEntityRenderer<FourthRoomTableBlockEntity> {

    private final ItemRenderer itemRenderer;

    public FourthRoomTableBlockEntityRenderer(BlockEntityRendererProvider.@NotNull Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(@NotNull FourthRoomTableBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = entity.getBlockState().hasProperty(FourthRoomTableBlock.FACING)
                ? entity.getBlockState().getValue(FourthRoomTableBlock.FACING)
                : Direction.NORTH;
        renderPile(entity, poseStack, bufferSource, packedOverlay, facing, TableEffectEvents.TableAnchor.DRAW_PILE,
                Math.max(1, (int) Math.ceil(entity.drawPileSize() / 4.0D)), "牌库 x" + entity.drawPileSize(), false);
        renderPile(entity, poseStack, bufferSource, packedOverlay, facing, TableEffectEvents.TableAnchor.DISCARD_PILE,
                Math.max(1, Math.min(6, entity.discardPileSize())), entity.topDiscardCardId().isBlank()
                        ? "弃牌堆 x" + entity.discardPileSize()
                        : entity.topDiscardCardId() + " x" + entity.discardPileSize(), true);
        renderSeatStatus(entity, poseStack, bufferSource, facing, TableEffectEvents.TableAnchor.SLOT_A,
                entity.seatAPlayerName(), entity.seatAPlayerUuid(), entity.seatAAlive(), entity.seatAHiddenIdentityCount());
        renderSeatStatus(entity, poseStack, bufferSource, facing, TableEffectEvents.TableAnchor.SLOT_B,
                entity.seatBPlayerName(), entity.seatBPlayerUuid(), entity.seatBAlive(), entity.seatBHiddenIdentityCount());
        renderRecentCards(entity, poseStack, bufferSource, facing);
        renderAnimatedCards(entity, poseStack, bufferSource, facing, packedOverlay);
        renderPulses(entity, poseStack, bufferSource, facing);
        renderTableLabels(entity, poseStack, bufferSource);
    }

    private void renderPile(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                            int packedOverlay, Direction facing, TableEffectEvents.TableAnchor anchor, int cards,
                            String label, boolean highlight) {
        Vec3 offset = anchor.localOffset(facing);
        int renderCards = Math.max(1, Math.min(cards, 6));
        for (int index = 0; index < renderCards; index++) {
            renderCard(entity, poseStack, bufferSource, 0.5D + offset.x, 0.935D + index * 0.011D,
                    0.5D + offset.z, 0.27F, highlight, 0.0F, packedOverlay);
        }
        renderBillboardText(poseStack, bufferSource, Component.literal(label), offset.x, 1.16D, offset.z,
                0xFFF1F1F1, 0.018F);
    }

    private void renderSeatStatus(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                                  Direction facing, TableEffectEvents.TableAnchor anchor, String playerName,
                                  String playerUuid, boolean alive, int hiddenIdentities) {
        if (playerName.isBlank()) {
            return;
        }
        Vec3 offset = anchor.localOffset(facing);
        int color = alive ? 0xFFF5ECD4 : 0xFF7F7F7F;
        if (!entity.activePlayerUuid().isBlank() && entity.activePlayerUuid().equals(playerUuid)) {
            color = 0xFFFFD66B;
        }
        renderBillboardText(poseStack, bufferSource, Component.literal(playerName), offset.x, 1.05D, offset.z, color,
                0.019F);
        renderBillboardText(poseStack, bufferSource,
                Component.literal("身份 x" + hiddenIdentities + (alive ? "" : " · 出局")), offset.x, 0.96D, offset.z,
                alive ? 0xFFB6D4FF : 0xFF969696, 0.016F);
    }

    private void renderRecentCards(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                                   Direction facing) {
        int count = entity.tableCards().size();
        for (int index = 0; index < count; index++) {
            FourthRoomTableBlockEntity.TableCardDisplay card = entity.tableCards().get(index);
            float spread = count <= 1 ? 0.0F : (index - (count - 1) / 2.0F) * 0.38F;
            float zRot = spread * -12.0F;
            renderCard(entity, poseStack, bufferSource, 0.5D + spread, 0.94D + Math.abs(spread) * 0.01D, 0.5D,
                    0.22F, card.highlight(), zRot, OverlayTexture.NO_OVERLAY);
            renderBillboardText(poseStack, bufferSource, Component.literal(card.titleText()), spread, 1.19D,
                    0.10D - Math.abs(spread) * 0.03D, card.accentColor(), 0.016F);
            renderBillboardText(poseStack, bufferSource, Component.literal(card.summaryText()), spread, 1.10D,
                    0.10D - Math.abs(spread) * 0.03D, 0xFFE9E9E9, 0.013F);
        }
    }

    private void renderAnimatedCards(FourthRoomTableBlockEntity entity, PoseStack poseStack,
                                     MultiBufferSource bufferSource, Direction facing, int packedOverlay) {
        long now = System.currentTimeMillis();
        for (FourthRoomTableBlockEntity.ActiveCardAnimation animation : entity.activeCardAnimations()) {
            float progress = animation.progress(now);
            Vec3 from = animation.from().localOffset(facing);
            Vec3 to = animation.to().localOffset(facing);
            double x = Mth.lerp(progress, from.x, to.x);
            double z = Mth.lerp(progress, from.z, to.z);
            double y = 0.97D + Math.sin(progress * Math.PI) * 0.26D;
            float scale = 0.24F + (float) Math.sin(progress * Math.PI) * 0.04F;
            renderCard(entity, poseStack, bufferSource, 0.5D + x, y, 0.5D + z, scale, animation.gold(),
                    Mth.lerp(progress, -8.0F, 8.0F), packedOverlay);
            if (!animation.label().isBlank()) {
                renderBillboardText(poseStack, bufferSource, Component.literal(animation.label()), x, y + 0.17D, z,
                        animation.color(), 0.015F);
            }
        }
    }

    private void renderPulses(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                              Direction facing) {
        long now = System.currentTimeMillis();
        for (FourthRoomTableBlockEntity.AnchorPulseState pulse : entity.activePulses()) {
            float progress = pulse.progress(now);
            float alpha = 1.0F - progress;
            float scale = 0.12F + progress * pulse.intensity() * 0.30F;
            Vec3 offset = pulse.anchor().localOffset(facing);
            float red = ((pulse.color() >> 16) & 0xFF) / 255.0F;
            float green = ((pulse.color() >> 8) & 0xFF) / 255.0F;
            float blue = (pulse.color() & 0xFF) / 255.0F;
            AABB box = new AABB(0.5D + offset.x - scale, 0.935D, 0.5D + offset.z - scale,
                    0.5D + offset.x + scale, 0.99D, 0.5D + offset.z + scale);
            LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), box, red, green, blue,
                    alpha);
        }
    }

    private void renderTableLabels(FourthRoomTableBlockEntity entity, PoseStack poseStack,
                                   MultiBufferSource bufferSource) {
        renderBillboardText(poseStack, bufferSource,
                Component.literal(entity.phase().isBlank() ? "卡牌对战" : entity.phase()), 0.0D, 1.42D, -0.05D,
                0xFF88D6FF, 0.020F);
        if (!entity.lastActionText().isBlank()) {
            renderBillboardText(poseStack, bufferSource, Component.literal(entity.lastActionText()), 0.0D, 1.31D,
                    -0.05D, 0xFFF4E8CC, 0.016F);
        }
        FourthRoomTableBlockEntity.BannerState banner = entity.activeBanner();
        if (banner != null) {
            long now = System.currentTimeMillis();
            float alpha = banner.alpha(now);
            int textAlpha = Math.min(255, Math.max(0, (int) (alpha * 255.0F)));
            int color = (textAlpha << 24) | (banner.color() & 0x00FFFFFF);
            renderBillboardText(poseStack, bufferSource, Component.literal(banner.text()), 0.0D,
                    1.56D + (1.0D - alpha) * 0.08D, 0.0D, color, 0.022F + alpha * 0.004F);
        }
    }

    private void renderCard(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                            double x, double y, double z, float scale, boolean highlighted, float zRotation,
                            int packedOverlay) {
        ItemStack stack = new ItemStack(Items.PAPER);
        if (highlighted) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(zRotation));
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                packedOverlay, poseStack, bufferSource, entity.getLevel(), 0);
        poseStack.popPose();
    }

    private void renderBillboardText(PoseStack poseStack, MultiBufferSource bufferSource, Component text, double x,
                                     double y, double z, int color, float scale) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(0.5D + x, y, 0.5D + z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(scale, -scale, scale);
        Matrix4f matrix = poseStack.last().pose();
        float xOffset = -font.width(text) / 2.0F;
        int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(text, xOffset, 0.0F, 0x21000000, false, matrix, bufferSource, Font.DisplayMode.SEE_THROUGH,
                background, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, xOffset, 0.0F, color, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull FourthRoomTableBlockEntity blockEntity) {
        return true;
    }
}