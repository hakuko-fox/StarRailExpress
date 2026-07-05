package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.content.block_entity.ZiplineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Set;

public class ZiplineBlockEntityRenderer implements BlockEntityRenderer<ZiplineBlockEntity> {

    private static final double MAX_RENDER_DISTANCE_SQ = 32.0 * 32.0;

    public ZiplineBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ZiplineBlockEntity entity, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        Set<BlockPos> connections = entity.getConnectedPositions();
        if (connections.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 距离检查
        double distSq = player.distanceToSqr(
                entity.getBlockPos().getX() + 0.5,
                entity.getBlockPos().getY() + 0.5,
                entity.getBlockPos().getZ() + 0.5);
        if (distSq > MAX_RENDER_DISTANCE_SQ) return;

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.LINES);

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos fromPos = entity.getBlockPos();
        Vec3 from = Vec3.atCenterOf(fromPos).add(0, 0.5, 0);

        for (BlockPos toPos : connections) {
            Vec3 to = Vec3.atCenterOf(toPos).add(0, 0.5, 0);

            // 计算带弧度的绳索路径：3个控制点的贝塞尔曲线
            Vec3 mid = from.add(to).scale(0.5).add(0, -0.3 * from.distanceTo(to), 0);
            int segments = Math.max(8, (int) (from.distanceTo(to) * 3));
            renderRope(matrices, consumer, camPos, from, mid, to, segments);
        }
    }

    private void renderRope(PoseStack matrices, VertexConsumer consumer, Vec3 camPos,
                            Vec3 p0, Vec3 p1, Vec3 p2, int segments) {
        Matrix4f pose = matrices.last().pose();

        Vec3 prevPoint = null;
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            // 二次贝塞尔：B(t) = (1-t)²P0 + 2(1-t)tP1 + t²P2
            float oneMinusT = 1 - t;
            double x = oneMinusT * oneMinusT * p0.x + 2 * oneMinusT * t * p1.x + t * t * p2.x;
            double y = oneMinusT * oneMinusT * p0.y + 2 * oneMinusT * t * p1.y + t * t * p2.y;
            double z = oneMinusT * oneMinusT * p0.z + 2 * oneMinusT * t * p1.z + t * t * p2.z;

            Vec3 currentPoint = new Vec3(x, y, z);

            if (prevPoint != null) {
                float dx = (float) (prevPoint.x - camPos.x);
                float dy = (float) (prevPoint.y - camPos.y);
                float dz = (float) (prevPoint.z - camPos.z);
                float dx2 = (float) (currentPoint.x - camPos.x);
                float dy2 = (float) (currentPoint.y - camPos.y);
                float dz2 = (float) (currentPoint.z - camPos.z);

                // 棕色绳索颜色
                consumer.addVertex(pose, dx, dy, dz).setColor(139, 90, 43, 255).setNormal(0, 1, 0);
                consumer.addVertex(pose, dx2, dy2, dz2).setColor(139, 90, 43, 255).setNormal(0, 1, 0);
            }
            prevPoint = currentPoint;
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ZiplineBlockEntity blockEntity) {
        return true;
    }
}
