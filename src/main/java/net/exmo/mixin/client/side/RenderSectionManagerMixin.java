package net.exmo.mixin.client.side;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.TaskQueueType;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;

/**
 * Sodium 0.8 版：遍历结束后把活动场景 section 补进本帧的
 * {@link SectionCollector}，确保它们进入渲染列表并绕过
 * INITIAL_BUILD 任务队列的容量上限（场景资产可能包含数千个 section）。
 */
@Mixin(RenderSectionManager.class)
public abstract class RenderSectionManagerMixin {
    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow
    private SectionCollector sectionCollector;

    @Shadow
    @Final
    private SortBehavior sortBehavior;

    @Shadow
    public abstract void onSectionAdded(int sectionX, int sectionY, int sectionZ);

    @Inject(
            method = "createTerrainRenderList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SectionCollector;getTaskLists()Ljava/util/Map;"),
            remap = false)
    private void sre$addSceneSections(
            Camera camera,
            Viewport viewport,
            int frame,
            boolean spectator,
            CallbackInfoReturnable<Boolean> cir) {
        long[] sceneSections = SceneAssetClient.activeSections();
        if (sceneSections.length == 0) {
            return;
        }
        TaskQueueType importantRebuildQueueType =
                SodiumClientMod.options().performance.chunkBuildDeferMode.getImportantRebuildQueueType();
        TaskQueueType importantSortQueueType =
                this.sortBehavior.getDeferMode().getImportantRebuildQueueType();
        int recovered = 0;
        for (long packed : sceneSections) {
            RenderSection section = sectionByPosition.get(packed);
            if (section == null) {
                onSectionAdded(
                        SectionPos.x(packed),
                        SectionPos.y(packed),
                        SectionPos.z(packed));
                section = sectionByPosition.get(packed);
                if (section != null) {
                    recovered++;
                }
            }
            if (section == null || section.getLastVisibleFrame() == frame) {
                continue;
            }
            section.setLastVisibleFrame(frame);

            int pendingUpdate = section.getPendingUpdate();
            ArrayDeque<RenderSection> queue = null;
            int queuedBeforeVisit = -1;
            if (pendingUpdate != 0) {
                TaskQueueType queueType = ChunkUpdateTypes.getQueueType(
                        pendingUpdate, importantRebuildQueueType, importantSortQueueType);
                queue = this.sectionCollector.getTaskLists().get(queueType);
                queuedBeforeVisit = queue == null ? -1 : queue.size();
            }
            this.sectionCollector.visit(section);
            if (queue != null
                    && queue.size() == queuedBeforeVisit
                    && section.getRunningJob() == null) {
                // 场景资产可能包含数千个 section，sodium 的任务队列上限
                // 会让其中大部分迟迟得不到构建，这里直接补进队列。
                queue.add(section);
            }
        }
        if (recovered > 0) {
            SRE.LOGGER.info("Recovered {} scene render sections after a Sodium renderer reload", recovered);
        }
    }
}
