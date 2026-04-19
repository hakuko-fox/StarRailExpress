package io.wifi.starrailexpress.content.block.api;

import io.wifi.starrailexpress.game.GameUtils.BlockEntityInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface AutoResetBlockInterface {
    /**
     * 重置方块
     * 
     * @param level
     * @param state
     * @param pos
     * @return 应该返回重置后的 BlockState
     */
    BlockState onReset(ServerLevel level, BlockState state, BlockPos pos);

    /**
     * 重置方块实体
     * 
     * @param level
     * @param state
     * @param pos
     * @return 返回null代表不存在方块实体
     */
    BlockEntityInfo onResetBlockEntity(ServerLevel level, BlockState state, BlockEntity blockEntity,BlockPos pos);
}
