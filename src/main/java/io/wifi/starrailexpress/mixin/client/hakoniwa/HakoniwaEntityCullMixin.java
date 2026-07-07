package io.wifi.starrailexpress.mixin.client.hakoniwa;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.HakoniwaVisionClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 箱庭视野：切割盒（被隐藏的屋顶 / 墙体区域）内的实体一并剔除 ——
 * 屋顶上的实体只有玩家自己也上了屋顶（outside，无切割）时才可见。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class HakoniwaEntityCullMixin {

    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean sre$hakoniwaCullEntity(boolean original, Entity entity) {
        if (original && HakoniwaVisionClientHandle.shouldCullEntity(entity)) {
            return false;
        }
        return original;
    }
}
