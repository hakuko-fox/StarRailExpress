package io.wifi.starrailexpress.mixin.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 游戏进行中耕地不会被踩踏成泥土，保护农作物。
 * 摔落伤害等其余 fallOn 逻辑不受影响。
 */
@Mixin(FarmBlock.class)
public class FarmBlockTrampleMixin {

    @WrapOperation(method = "fallOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/FarmBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void sre$noTrampleDuringGame(Entity entity, BlockState state, Level level, BlockPos pos, Operation<Void> original) {
        if (GameUtils.isGameStarted) {
            return;
        }
        original.call(entity, state, level, pos);
    }
}
