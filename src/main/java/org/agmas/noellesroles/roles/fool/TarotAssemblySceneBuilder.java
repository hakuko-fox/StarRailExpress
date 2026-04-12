package org.agmas.noellesroles.roles.fool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class TarotAssemblySceneBuilder {
    private static final int ROOM_RADIUS = 11;
    private static final int ROOM_HEIGHT = 7;

    private final ServerLevel level;

    TarotAssemblySceneBuilder(ServerLevel level) {
        this.level = level;
    }

    void build(BlockPos center) {
        clear(center);
        buildFloor(center);
        buildWalls(center);
        buildRoof(center);
        buildTable(center);
        buildLanterns(center);
    }

    private void clear(BlockPos center) {
        fill(center.offset(-ROOM_RADIUS - 2, -1, -ROOM_RADIUS - 2),
                center.offset(ROOM_RADIUS + 2, ROOM_HEIGHT + 2, ROOM_RADIUS + 2),
                Blocks.AIR.defaultBlockState());
    }

    private void buildFloor(BlockPos center) {
        fill(center.offset(-ROOM_RADIUS, -1, -ROOM_RADIUS),
                center.offset(ROOM_RADIUS, -1, ROOM_RADIUS),
                Blocks.SMOOTH_STONE.defaultBlockState());
        fill(center.offset(-ROOM_RADIUS + 1, 0, -ROOM_RADIUS + 1),
                center.offset(ROOM_RADIUS - 1, 0, ROOM_RADIUS - 1),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        fill(center.offset(-6, 0, -6), center.offset(6, 0, 6), Blocks.WHITE_CARPET.defaultBlockState());
        fill(center.offset(-4, 0, -4), center.offset(4, 0, 4), Blocks.GRAY_CARPET.defaultBlockState());
    }

    private void buildWalls(BlockPos center) {
        BlockState shell = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        BlockState trim = Blocks.TINTED_GLASS.defaultBlockState();
        for (int y = 1; y <= ROOM_HEIGHT; y++) {
            for (int x = -ROOM_RADIUS; x <= ROOM_RADIUS; x++) {
                setBlock(center.offset(x, y, -ROOM_RADIUS), trim);
                setBlock(center.offset(x, y, ROOM_RADIUS), trim);
            }
            for (int z = -ROOM_RADIUS; z <= ROOM_RADIUS; z++) {
                setBlock(center.offset(-ROOM_RADIUS, y, z), trim);
                setBlock(center.offset(ROOM_RADIUS, y, z), trim);
            }
        }
        fill(center.offset(-ROOM_RADIUS + 1, 1, -ROOM_RADIUS),
                center.offset(ROOM_RADIUS - 1, ROOM_HEIGHT - 1, -ROOM_RADIUS), shell);
        fill(center.offset(-ROOM_RADIUS + 1, 1, ROOM_RADIUS),
                center.offset(ROOM_RADIUS - 1, ROOM_HEIGHT - 1, ROOM_RADIUS), shell);
        fill(center.offset(-ROOM_RADIUS, 1, -ROOM_RADIUS + 1),
                center.offset(-ROOM_RADIUS, ROOM_HEIGHT - 1, ROOM_RADIUS - 1), shell);
        fill(center.offset(ROOM_RADIUS, 1, -ROOM_RADIUS + 1),
                center.offset(ROOM_RADIUS, ROOM_HEIGHT - 1, ROOM_RADIUS - 1), shell);
    }

    private void buildRoof(BlockPos center) {
        fill(center.offset(-ROOM_RADIUS, ROOM_HEIGHT + 1, -ROOM_RADIUS),
                center.offset(ROOM_RADIUS, ROOM_HEIGHT + 1, ROOM_RADIUS),
                Blocks.TINTED_GLASS.defaultBlockState());
        fill(center.offset(-5, ROOM_HEIGHT + 1, -5), center.offset(5, ROOM_HEIGHT + 1, 5),
                Blocks.WHITE_STAINED_GLASS.defaultBlockState());
    }

    private void buildTable(BlockPos center) {
        fill(center.offset(-1, 1, -1), center.offset(1, 1, 1), Blocks.POLISHED_DIORITE.defaultBlockState());
        setBlock(center.offset(0, 2, 0), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private void buildLanterns(BlockPos center) {
        placeLantern(center.offset(-7, 0, -7));
        placeLantern(center.offset(7, 0, -7));
        placeLantern(center.offset(-7, 0, 7));
        placeLantern(center.offset(7, 0, 7));
    }

    private void placeLantern(BlockPos floorPos) {
        setBlock(floorPos, Blocks.POLISHED_ANDESITE.defaultBlockState());
        setBlock(floorPos.above(), Blocks.CHAIN.defaultBlockState());
        setBlock(floorPos.above(2), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private void fill(BlockPos from, BlockPos to, BlockState state) {
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_ALL);
                }
            }
        }
    }

    private void setBlock(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}