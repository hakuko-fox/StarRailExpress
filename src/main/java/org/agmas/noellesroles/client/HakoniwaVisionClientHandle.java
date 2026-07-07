package org.agmas.noellesroles.client;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayDeque;

/**
 * 箱庭视野（{@link ModEffects#HAKONIWA_VISION}）客户端核心。
 *
 * <b>房间构筑</b>：以玩家脚下位置为种子做碰撞泛洪（穿过空气 / 无碰撞方块，
 * 被墙体 / 玻璃 / 关闭的门阻挡），得到「当前房间」的空气包围盒与天花板高度。
 * 泛洪细胞见天（{@code canSeeSky}）超过阈值即判定为 outside（露天 / 屋顶上），
 * 此时不做任何切割 —— 玩家上了屋顶自然恢复完整渲染。
 *
 * <b>视角切割</b>：
 * <ul>
 * <li>俯视（2D 效果 amplifier 4，或普通视角俯角 &gt; {@value #TOP_PITCH_THRESHOLD}°）：
 * 隐藏房间正上方 {@value #ROOF_HIDE_DEPTH} 格厚的屋顶层；</li>
 * <li>2.5D / 平面侧视（amplifier 0~3 / 5~8）：切开面向镜头一侧
 * {@value #WALL_HIDE_DEPTH} 格厚的墙体。</li>
 * </ul>
 *
 * <b>剔除通道</b>：方块在区块网格构建期被替换为空气（vanilla：RenderChunkRegion；
 * sodium：WorldSlice，见对应 mixin），切割盒变化时仅重建受影响的 section；
 * 实体由 EntityRenderDispatcher 的 shouldRender 剔除。
 */
public final class HakoniwaVisionClientHandle {

    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_VISITED = 8192;
    private static final int H_RADIUS = 20;
    private static final int V_RADIUS = 10;
    private static final int SKY_CELL_TOLERANCE = 8;
    private static final float TOP_PITCH_THRESHOLD = 45.0F;
    private static final int ROOF_HIDE_DEPTH = 5;
    private static final int WALL_HIDE_DEPTH = 2;

    private static final int MODE_NONE = 0;
    private static final int MODE_ROOF = 1;
    private static final int MODE_WALL_WEST = 2;
    private static final int MODE_WALL_EAST = 3;
    private static final int MODE_WALL_NORTH = 4;
    private static final int MODE_WALL_SOUTH = 5;

    // ── 房间扫描结果 ─────────────────────────────────────────────
    private static boolean interior;
    private static int roomMinX, roomMinY, roomMinZ, roomMaxX, roomMaxY, roomMaxZ;
    private static BlockPos lastScanOrigin = BlockPos.ZERO;
    private static long lastScanTick = Long.MIN_VALUE;

    // ── 当前切割盒（含端点，无效时 hideActive=false）─────────────
    private static volatile boolean hideActive;
    private static volatile int hideMinX, hideMinY, hideMinZ, hideMaxX, hideMaxY, hideMaxZ;
    private static int currentMode = MODE_NONE;

    private HakoniwaVisionClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HakoniwaVisionClientHandle::tick);
    }

    /** 区块网格构建期的方块剔除判定（必须极快，仅做包围盒比较）。 */
    public static boolean shouldHideBlock(int x, int y, int z) {
        return hideActive
                && x >= hideMinX && x <= hideMaxX
                && y >= hideMinY && y <= hideMaxY
                && z >= hideMinZ && z <= hideMaxZ;
    }

    /** 实体剔除判定：切割盒内的实体（屋顶上的实体）不渲染。 */
    public static boolean shouldCullEntity(Entity entity) {
        if (!hideActive) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (entity == client.player || entity == client.getCameraEntity()) {
            return false;
        }
        return shouldHideBlock(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
    }

    public static boolean isActive() {
        return hideActive;
    }

    // ==================== Tick ====================

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || !player.hasEffect(ModEffects.HAKONIWA_VISION)) {
            applyCutBox(MODE_NONE, 0, 0, 0, 0, 0, 0);
            lastScanTick = Long.MIN_VALUE;
            return;
        }

        long gameTime = client.level.getGameTime();
        BlockPos origin = player.blockPosition();
        if (gameTime - lastScanTick >= SCAN_INTERVAL_TICKS || !origin.equals(lastScanOrigin)) {
            lastScanTick = gameTime;
            lastScanOrigin = origin;
            scanRoom(client.level, origin);
        }

        int mode = resolveMode(player);
        if (!interior || mode == MODE_NONE) {
            applyCutBox(MODE_NONE, 0, 0, 0, 0, 0, 0);
            return;
        }

        switch (mode) {
            case MODE_ROOF -> applyCutBox(mode,
                    roomMinX - 1, roomMaxY + 1, roomMinZ - 1,
                    roomMaxX + 1, roomMaxY + ROOF_HIDE_DEPTH, roomMaxZ + 1);
            case MODE_WALL_WEST -> applyCutBox(mode,
                    roomMinX - WALL_HIDE_DEPTH, roomMinY - 1, roomMinZ - 1,
                    roomMinX - 1, roomMaxY + 1, roomMaxZ + 1);
            case MODE_WALL_EAST -> applyCutBox(mode,
                    roomMaxX + 1, roomMinY - 1, roomMinZ - 1,
                    roomMaxX + WALL_HIDE_DEPTH, roomMaxY + 1, roomMaxZ + 1);
            case MODE_WALL_NORTH -> applyCutBox(mode,
                    roomMinX - 1, roomMinY - 1, roomMinZ - WALL_HIDE_DEPTH,
                    roomMaxX + 1, roomMaxY + 1, roomMinZ - 1);
            case MODE_WALL_SOUTH -> applyCutBox(mode,
                    roomMinX - 1, roomMinY - 1, roomMaxZ + 1,
                    roomMaxX + 1, roomMaxY + 1, roomMaxZ + WALL_HIDE_DEPTH);
            default -> applyCutBox(MODE_NONE, 0, 0, 0, 0, 0, 0);
        }
    }

    /** 当前应采用的切割模式：2D 效果的镜头方位优先，其次普通视角的俯角。 */
    private static int resolveMode(LocalPlayer player) {
        MobEffectInstance twoD = player.getEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
        if (twoD != null && TwoDimensionalCameraClientHandle.isActive()) {
            int amplifier = twoD.getAmplifier();
            if (amplifier == 4) {
                return MODE_ROOF;
            }
            // 0~3 = 2.5D 侧视，5~8 = 纯平面侧视；方向均为 西/东/北/南
            int side = amplifier >= 5 ? amplifier - 5 : amplifier;
            return switch (side) {
                case 0 -> MODE_WALL_WEST;
                case 1 -> MODE_WALL_EAST;
                case 2 -> MODE_WALL_NORTH;
                case 3 -> MODE_WALL_SOUTH;
                default -> MODE_NONE;
            };
        }
        return player.getXRot() > TOP_PITCH_THRESHOLD ? MODE_ROOF : MODE_NONE;
    }

    /** 更新切割盒；变化时把新旧区域覆盖的 section 标脏触发网格重建。 */
    private static void applyCutBox(int mode, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean newActive = mode != MODE_NONE;
        boolean same = hideActive == newActive
                && (!newActive || (currentMode == mode
                        && hideMinX == minX && hideMinY == minY && hideMinZ == minZ
                        && hideMaxX == maxX && hideMaxY == maxY && hideMaxZ == maxZ));
        if (same) {
            return;
        }

        if (hideActive) {
            markSectionsDirty(hideMinX, hideMinY, hideMinZ, hideMaxX, hideMaxY, hideMaxZ);
        }
        currentMode = mode;
        if (newActive) {
            hideMinX = minX;
            hideMinY = minY;
            hideMinZ = minZ;
            hideMaxX = maxX;
            hideMaxY = maxY;
            hideMaxZ = maxZ;
            hideActive = true;
            markSectionsDirty(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            hideActive = false;
        }
    }

    private static void markSectionsDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null) {
            return;
        }
        int sMinX = SectionPos.blockToSectionCoord(minX);
        int sMinY = SectionPos.blockToSectionCoord(minY);
        int sMinZ = SectionPos.blockToSectionCoord(minZ);
        int sMaxX = SectionPos.blockToSectionCoord(maxX);
        int sMaxY = SectionPos.blockToSectionCoord(maxY);
        int sMaxZ = SectionPos.blockToSectionCoord(maxZ);
        for (int sx = sMinX; sx <= sMaxX; sx++) {
            for (int sy = sMinY; sy <= sMaxY; sy++) {
                for (int sz = sMinZ; sz <= sMaxZ; sz++) {
                    client.levelRenderer.setSectionDirtyWithNeighbors(sx, sy, sz);
                }
            }
        }
    }

    // ==================== 房间泛洪扫描 ====================

    private static void scanRoom(Level level, BlockPos origin) {
        interior = false;
        if (!isPassable(level, origin) && !isPassable(level, origin.above())) {
            return;
        }
        BlockPos seed = isPassable(level, origin) ? origin : origin.above();

        LongOpenHashSet visited = new LongOpenHashSet(1024);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed.asLong());

        int minX = seed.getX(), minY = seed.getY(), minZ = seed.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        int skyCells = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && visited.size() < MAX_VISITED) {
            BlockPos pos = queue.poll();
            if (level.canSeeSky(pos)) {
                skyCells++;
                if (skyCells > SKY_CELL_TOLERANCE) {
                    return; // 露天 / 屋顶：outside，不构筑箱庭
                }
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            for (var direction : net.minecraft.core.Direction.values()) {
                cursor.setWithOffset(pos, direction);
                if (Math.abs(cursor.getX() - seed.getX()) > H_RADIUS
                        || Math.abs(cursor.getZ() - seed.getZ()) > H_RADIUS
                        || Math.abs(cursor.getY() - seed.getY()) > V_RADIUS) {
                    continue;
                }
                long key = cursor.asLong();
                if (visited.contains(key) || !isPassable(level, cursor)) {
                    continue;
                }
                visited.add(key);
                queue.add(cursor.immutable());
            }
        }

        interior = true;
        roomMinX = minX;
        roomMinY = minY;
        roomMinZ = minZ;
        roomMaxX = maxX;
        roomMaxY = maxY;
        roomMaxZ = maxZ;
    }

    /** 泛洪可通过性：空气 / 无碰撞方块可通过；墙体、玻璃、关闭的门阻挡。 */
    private static boolean isPassable(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }
}
