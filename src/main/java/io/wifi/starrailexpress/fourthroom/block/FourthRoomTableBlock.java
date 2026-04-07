package io.wifi.starrailexpress.fourthroom.block;

import io.wifi.starrailexpress.fourthroom.game.FourthRoomGameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class FourthRoomTableBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<TablePart> PART = EnumProperty.create("part", TablePart.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14.9, 16);

    public FourthRoomTableBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0F)
                .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, TablePart.CENTER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos corePos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (TablePart part : TablePart.values()) {
            BlockPos targetPos = corePos.offset(part.xOffset, 0, part.zOffset);
            boolean canReplace = context.getLevel().getBlockState(targetPos).canBeReplaced(context)
                    && context.getLevel().getWorldBorder().isWithinBounds(targetPos);
            if (!canReplace) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, TablePart.CENTER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            placeStructure(level, pos, state.getValue(FACING));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) != TablePart.CENTER) {
            return null;
        }
        return new FourthRoomTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof FourthRoomTableBlockEntity table) {
                    table.clientTick();
                }
            };
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos corePos = getCore(state, pos);
        BlockEntity be = level.getBlockEntity(corePos);
        if (be instanceof FourthRoomTableBlockEntity table) {
            if (table.linkedRoomId() >= 0 && FourthRoomGameManager.of((net.minecraft.server.level.ServerLevel) level).data().active) {
                player.displayClientMessage(Component.literal("§e[第四房间] §f盯着牌桌按 H 可打开战术面板"), true);
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                table.onPlayerInteract(serverPlayer);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected @NotNull BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                              LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockPos corePos = getCore(state, pos);
        for (TablePart part : TablePart.values()) {
            if (!level.getBlockState(corePos.offset(part.xOffset, 0, part.zOffset)).is(this)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            removeStructure(level, getCore(state, pos));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    public static BlockPos getCore(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof FourthRoomTableBlock)) {
            return pos;
        }
        TablePart part = state.getValue(PART);
        return pos.offset(-part.xOffset, 0, -part.zOffset);
    }

    public static void placeStructure(LevelAccessor level, BlockPos corePos, Direction facing) {
        BlockState centerState = level.getBlockState(corePos);
        if (!(centerState.getBlock() instanceof FourthRoomTableBlock block)) {
            return;
        }
        for (TablePart part : TablePart.values()) {
            level.setBlock(corePos.offset(part.xOffset, 0, part.zOffset),
                    block.defaultBlockState().setValue(FACING, facing).setValue(PART, part),
                    Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void removeStructure(Level level, BlockPos corePos) {
        for (TablePart part : TablePart.values()) {
            level.setBlock(corePos.offset(part.xOffset, 0, part.zOffset), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }

    public enum TablePart implements StringRepresentable {
        NORTH_WEST(-1, -1),
        NORTH(0, -1),
        NORTH_EAST(1, -1),
        WEST(-1, 0),
        CENTER(0, 0),
        EAST(1, 0),
        SOUTH_WEST(-1, 1),
        SOUTH(0, 1),
        SOUTH_EAST(1, 1);

        private final int xOffset;
        private final int zOffset;

        TablePart(int xOffset, int zOffset) {
            this.xOffset = xOffset;
            this.zOffset = zOffset;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name().toLowerCase();
        }
    }
}
