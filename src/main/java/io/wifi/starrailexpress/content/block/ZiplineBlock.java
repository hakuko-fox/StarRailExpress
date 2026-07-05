package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.ZiplineBlockEntity;
import io.wifi.starrailexpress.content.entity.ZiplineRiderEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ZiplineBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final int MAX_ZIPLINE_RANGE = 25;

    public ZiplineBlock() {
        super(Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.WOOD)
                .mapColor(MapColor.COLOR_BROWN)
                .noOcclusion()
                .forceSolidOn());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            // 自动扫描四个方向，建立双向连接
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ZiplineBlockEntity zbe) {
                scanAndConnect(level, pos, zbe);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // 通知所有连接的滑索移除对自己的引用
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ZiplineBlockEntity zbe) {
                for (BlockPos connected : zbe.getConnectedPositions()) {
                    BlockEntity otherBe = level.getBlockEntity(connected);
                    if (otherBe instanceof ZiplineBlockEntity otherZbe) {
                        otherZbe.removeConnection(pos);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 扫描四个方向，查找并连接其他滑索方块
     */
    private void scanAndConnect(Level level, BlockPos pos, ZiplineBlockEntity zbe) {
        zbe.clearConnections();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos found = null;
            for (int i = 1; i <= MAX_ZIPLINE_RANGE; i++) {
                BlockPos checkPos = pos.relative(dir, i);
                BlockState checkState = level.getBlockState(checkPos);
                if (checkState.getBlock() instanceof ZiplineBlock) {
                    found = checkPos;
                    break;
                }
            }
            if (found != null) {
                // 双向连接
                zbe.addConnection(found);
                BlockEntity otherBe = level.getBlockEntity(found);
                if (otherBe instanceof ZiplineBlockEntity otherZbe) {
                    otherZbe.addConnection(pos);
                }
            }
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ZiplineBlockEntity zbe)) {
            return InteractionResult.PASS;
        }

        Set<BlockPos> connections = zbe.getConnectedPositions();
        if (connections.isEmpty()) {
            return InteractionResult.PASS;
        }

        // 优先选择玩家看向方向的连接点，否则选最近的
        BlockPos targetPos = selectTarget(connections, pos, player);

        if (targetPos == null) {
            return InteractionResult.PASS;
        }

        ZiplineRiderEntity rider = TMMEntities.ZIPLINE_RIDER.create(level);
        if (rider == null) {
            return InteractionResult.PASS;
        }

        rider.setStartAndEnd(pos, targetPos);
        Vec3 startPos = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        rider.setPos(startPos);
        level.addFreshEntity(rider);
        player.startRiding(rider, true);

        return InteractionResult.SUCCESS;
    }

    /**
     * 从连接列表中选择最合适的滑索目标
     */
    @Nullable
    private BlockPos selectTarget(Set<BlockPos> connections, BlockPos from, Player player) {
        if (connections.isEmpty()) return null;
        if (connections.size() == 1) return connections.iterator().next();

        Vec3 lookVec = player.getLookAngle();
        Vec3 fromCenter = Vec3.atCenterOf(from);

        // 找与玩家视线方向最接近的连接点
        BlockPos best = null;
        double bestDot = -2.0;
        for (BlockPos conn : connections) {
            Vec3 toConn = Vec3.atCenterOf(conn).subtract(fromCenter).normalize();
            double dot = lookVec.dot(toConn);
            if (dot > bestDot) {
                bestDot = dot;
                best = conn;
            }
        }
        return best != null ? best : connections.iterator().next();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZiplineBlockEntity(TMMBlockEntities.ZIPLINE, pos, state);
    }
}
