package io.wifi.starrailexpress.mixin.sodium6;

import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.compat.SodiumShaderInterface;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferUsage;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * Sodium 0.6 版场景偏移集成（遗留，运行时检测到 sodium &lt; 0.7 时启用）。
 * 与主源集的 0.8 版对应，本源集针对 sodium mc1.21.1-0.6.13 编译。
 */
@Mixin(value = DefaultChunkRenderer.class)
public abstract class DefaultChunkRendererMixin {
    @Unique
    private static final int sre$offsetCount =
            RenderRegion.REGION_SIZE * ModelQuadFacing.COUNT + 1;
    @Unique
    private static ByteBuffer sre$offsetBuffer;
    @Unique
    private static GlMutableBuffer sre$glBuffer;

    @Shadow(remap = false)
    private static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private static void addNonIndexedDrawCommands(MultiDrawBatch batch, long pMeshData, int slices) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private static void addIndexedDrawCommands(MultiDrawBatch batch, long pMeshData, int slices) {
        throw new AssertionError();
    }

    @Inject(method = "fillCommandBuffer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sre$fillCommandBuffer(
            MultiDrawBatch batch,
            RenderRegion region,
            SectionRenderDataStorage renderDataStorage,
            ChunkRenderList renderList,
            CameraTransform camera,
            TerrainRenderPass pass,
            boolean useBlockFaceCulling,
            CallbackInfo ci) {
        batch.clear();
        if (sre$offsetBuffer != null) {
            MemoryUtil.memFree(sre$offsetBuffer);
        }
        sre$offsetBuffer = MemoryUtil.memCalloc(sre$offsetCount * 16);

        boolean translucent = pass.isTranslucent();
        ByteIterator iterator = renderList.sectionsWithGeometryIterator(translucent);
        if (iterator == null) {
            ci.cancel();
            return;
        }

        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 offset = SceneAssetClient.renderOffset(partialTick);

        int originX = region.getChunkX();
        int originY = region.getChunkY();
        int originZ = region.getChunkZ();
        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();
            long pMeshData = renderDataStorage.getDataPointer(sectionIndex);
            int sectionX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int sectionY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int sectionZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

            int slices = useBlockFaceCulling
                    ? getVisibleFaces(camera.intX, camera.intY, camera.intZ, sectionX, sectionY, sectionZ)
                    : ModelQuadFacing.ALL;
            boolean activeSection = SceneAssetClient.isActiveSection(sectionX, sectionY, sectionZ);
            if (activeSection) {
                slices = ModelQuadFacing.ALL;
            }
            slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);
            if (slices == 0) {
                continue;
            }

            if (translucent) {
                addIndexedDrawCommands(batch, pMeshData, slices);
            } else {
                addNonIndexedDrawCommands(batch, pMeshData, slices);
            }

            if (activeSection) {
                // 着色器以 _draw_id（= LocalSectionIndex）为下标读取偏移
                int base = sectionIndex * 16;
                sre$offsetBuffer.putFloat(base, (float) offset.x);
                sre$offsetBuffer.putFloat(base + 4, (float) offset.y);
                sre$offsetBuffer.putFloat(base + 8, (float) offset.z);
            }
        }
        ci.cancel();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V"),
            remap = false)
    private void sre$bindOffsets(
            ChunkRenderMatrices matrices,
            CommandList commandList,
            ChunkRenderListIterable renderLists,
            TerrainRenderPass renderPass,
            CameraTransform camera,
            CallbackInfo ci,
            @Local(ordinal = 0) ChunkShaderInterface shader) {
        if (sre$offsetBuffer == null) {
            return;
        }
        sre$glBuffer = commandList.createMutableBuffer();
        commandList.uploadData(sre$glBuffer, sre$offsetBuffer, GlBufferUsage.STREAM_DRAW);
        ((SodiumShaderInterface) shader).tmm$set(sre$glBuffer);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V",
                    shift = At.Shift.AFTER),
            remap = false)
    private void sre$releaseOffsets(
            ChunkRenderMatrices matrices,
            CommandList commandList,
            ChunkRenderListIterable renderLists,
            TerrainRenderPass renderPass,
            CameraTransform camera,
            CallbackInfo ci) {
        if (sre$glBuffer != null) {
            commandList.deleteBuffer(sre$glBuffer);
            sre$glBuffer = null;
        }
        if (sre$offsetBuffer != null) {
            MemoryUtil.memFree(sre$offsetBuffer);
            sre$offsetBuffer = null;
        }
    }
}
