package org.agmas.noellesroles.mixin.client.sodium;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class HakukoFoxPOVSodiumSectionMixin {

    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow
    private SectionCollector sectionCollector;

    @Inject(method = "createTerrainRenderList", at = @At("TAIL"), remap = false)
    private void noellesroles$addPlayerSection(Camera camera, net.caffeinemc.mods.sodium.client.render.viewport.Viewport viewport,
            int frame, boolean spectator, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.cameraEntity instanceof Fox) || mc.player == null) return;
        if (this.sectionCollector == null) return;

        SectionPos sectionPos = SectionPos.of(mc.player.blockPosition());
        long packed = sectionPos.asLong();
        RenderSection section = this.sectionByPosition.get(packed);
        if (section == null || section.getLastVisibleFrame() == frame) return;
        section.setLastVisibleFrame(frame);
        this.sectionCollector.visit(section);
    }
}
