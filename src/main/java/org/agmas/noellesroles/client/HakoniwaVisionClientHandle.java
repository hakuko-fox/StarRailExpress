package org.agmas.noellesroles.client;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayDeque;

/**
 * 箱庭视野（{@link ModEffects#HAKONIWA_VISION}）客户端核心。
 * 实现参考 dungeons-perspective（FloodCuller + GenericCuller3），两层剔除叠加：
 *
 * <p><b>一、逐列天花板（屋顶剔除）</b>：以玩家脚下为种子对连通空气做三维泛洪，
 * 记录每个 XZ 列泛洪到的最高空气高度（即该列的天花板）。凡高于该列天花板的方块一律剔除。
 * 相比矩形切割盒，它沿房间的真实形状剔屋顶，L 形房间 / 走廊都不会误伤；且天生自适应室内外——
 * 露天时泛洪撞上 {@value #FLOOD_UP} 格的垂直上限，此类「未真正见顶」的列直接排除在剔除之外，
 * 因此远处的高塔 / 树冠不会被误剔。
 *
 * <p><b>二、视线圆锥（遮挡剔除）</b>：沿「玩家 → 相机」轴剔除轴心附近、位于玩家脚下平面之上、
 * 且比相机更近的方块。近端是半角 45° 的圆锥（贴着玩家不会剔掉过多），远端收敛为半径
 * {@value #SIGHT_RADIUS} 的圆柱。侧视 / 2.5D 下它负责挖穿镜头与玩家之间的近墙。
 * 正上方俯视不启用它——那时玩家头顶整列已被第一层清空，再挖锥子既无收益，
 * 又会因锥体随玩家逐格移动而不断触发区块重建。
 *
 * <p><b>剔除通道</b>：方块在区块网格构建期被替换为空气（vanilla：RenderChunkRegion；
 * sodium：LevelSlice，见对应 mixin），实体由 EntityRenderDispatcher 的 shouldRender 剔除。
 * 两者都在渲染线程之外调用，故剔除状态以不可变快照 + volatile 引用发布。
 */
public final class HakoniwaVisionClientHandle {

    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_VISITED = 8192;
    /** 泛洪的水平 / 垂直扩散上限，防止露天时泛洪逃逸到整个世界。 */
    private static final int FLOOD_H_RADIUS = 20;
    private static final int FLOOD_UP = 24;
    private static final int FLOOD_DOWN = 8;
    /**
     * 天花板之上被剔除的最大厚度。俯视镜头高度为 {@code max(8, 相机距离 + 6)}，而相机距离上限 64
     * （见 {@code ModEffects#getTwoDimensionalCameraDistance}），故 72 必然越过镜头；
     * 有了上界才能算出需要标脏的 section 范围。
     */
    private static final int ROOF_CULL_HEIGHT = 72;
    /** 视线圆柱的远端半径（格）。 */
    private static final double SIGHT_RADIUS = 6.0D;
    /** 视线圆锥的半角正切；1.0 = 45°。 */
    private static final double SIGHT_CONE_TAN = 1.0D;
    /** 普通视角下触发屋顶剔除的俯角阈值。 */
    private static final float TOP_PITCH_THRESHOLD = 45.0F;
    /** {@link Long2IntOpenHashMap} 的缺省值：该 XZ 列不在泛洪范围内。 */
    private static final int NO_CEILING = Integer.MIN_VALUE;
    /** {@code Direction.values()} 每次调用都克隆数组，而泛洪要逐格遍历它。 */
    private static final Direction[] DIRECTIONS = Direction.values();

    /** 当前生效的剔除快照；null 表示未激活。发布后绝不修改，故可被网格构建线程无锁读取。 */
    private static volatile Cull current;
    private static BlockPos lastScanOrigin = BlockPos.ZERO;
    private static long lastScanTick = Long.MIN_VALUE;

    private HakoniwaVisionClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HakoniwaVisionClientHandle::tick);
    }

    /** 区块网格构建期的方块剔除判定（在工作线程上被逐方块调用，必须极快）。 */
    public static boolean shouldHideBlock(int x, int y, int z) {
        Cull cull = current;
        return cull != null && cull.hides(x, y, z);
    }

    /** 实体剔除判定：落在被剔除区域内的实体（屋顶上的、近墙里的）不渲染。 */
    public static boolean shouldCullEntity(Entity entity) {
        Cull cull = current;
        if (cull == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (entity == client.player || entity == client.getCameraEntity()) {
            return false;
        }
        return cull.hides(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
    }

    public static boolean isActive() {
        return current != null;
    }

    // ==================== Tick ====================

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || !player.hasEffect(ModEffects.HAKONIWA_VISION)) {
            publish(null);
            lastScanTick = Long.MIN_VALUE;
            return;
        }

        boolean twoDimensional = TwoDimensionalCameraClientHandle.isActive();
        boolean roofCull = twoDimensional || player.getXRot() > TOP_PITCH_THRESHOLD;
        Vec3 cameraPos = TwoDimensionalCameraClientHandle.cameraPosition();
        // 俯视时头顶整列已被逐列天花板清空，无需再挖视线圆锥。
        boolean sightCull = twoDimensional && !TwoDimensionalCameraClientHandle.isTopView() && cameraPos != null;
        if (!roofCull && !sightCull) {
            publish(null);
            lastScanTick = Long.MIN_VALUE;
            return;
        }

        long gameTime = client.level.getGameTime();
        BlockPos origin = player.blockPosition();
        boolean due = lastScanTick == Long.MIN_VALUE
                || !origin.equals(lastScanOrigin)
                || gameTime - lastScanTick >= SCAN_INTERVAL_TICKS;
        if (!due) {
            return;
        }
        lastScanTick = gameTime;
        lastScanOrigin = origin;

        Long2IntOpenHashMap ceilings = roofCull
                ? floodCeilings(client.level, player)
                : emptyCeilings();
        if (ceilings.isEmpty() && !sightCull) {
            publish(null);
            return;
        }

        Cull next = Cull.of(ceilings, sightCull, player.position(), cameraPos, client.level);
        // 玩家在同一房间内走动时逐列天花板不变、且俯视不含圆锥 —— 此时快照等价，跳过标脏。
        if (next.equivalentTo(current)) {
            return;
        }
        publish(next);
    }

    /** 切换快照，并把新旧快照覆盖的区域标脏以触发网格重建。 */
    private static void publish(Cull next) {
        Cull previous = current;
        if (previous == null && next == null) {
            return;
        }
        current = next;
        if (previous != null) {
            markSectionsDirty(previous);
        }
        if (next != null) {
            markSectionsDirty(next);
        }
    }

    private static void markSectionsDirty(Cull cull) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null) {
            return;
        }
        int sMinX = SectionPos.blockToSectionCoord(cull.minX);
        int sMinY = SectionPos.blockToSectionCoord(cull.minY);
        int sMinZ = SectionPos.blockToSectionCoord(cull.minZ);
        int sMaxX = SectionPos.blockToSectionCoord(cull.maxX);
        int sMaxY = SectionPos.blockToSectionCoord(cull.maxY);
        int sMaxZ = SectionPos.blockToSectionCoord(cull.maxZ);
        for (int sx = sMinX; sx <= sMaxX; sx++) {
            for (int sy = sMinY; sy <= sMaxY; sy++) {
                for (int sz = sMinZ; sz <= sMaxZ; sz++) {
                    client.levelRenderer.setSectionDirtyWithNeighbors(sx, sy, sz);
                }
            }
        }
    }

    // ==================== 逐列天花板泛洪 ====================

    private static Long2IntOpenHashMap emptyCeilings() {
        Long2IntOpenHashMap map = new Long2IntOpenHashMap();
        map.defaultReturnValue(NO_CEILING);
        return map;
    }

    /**
     * 从玩家脚下的连通空气出发做三维泛洪，返回「XZ 列 → 该列泛洪到的最高空气 Y」。
     * 泛洪到达垂直上限的列说明它并未真正见顶（露天 / 高得离谱的中庭），予以剔除出结果，
     * 否则会把天上无关的方块一并剔掉。
     */
    private static Long2IntOpenHashMap floodCeilings(Level level, LocalPlayer player) {
        Long2IntOpenHashMap ceilings = emptyCeilings();
        BlockPos foot = player.blockPosition();
        BlockPos seed = isPassable(level, foot) ? foot
                : (isPassable(level, foot.above()) ? foot.above() : null);
        if (seed == null) {
            return ceilings;
        }

        int minY = seed.getY() - FLOOD_DOWN;
        int maxY = seed.getY() + FLOOD_UP;

        LongOpenHashSet visited = new LongOpenHashSet(1024);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed.asLong());
        LongOpenHashSet openColumns = new LongOpenHashSet();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && visited.size() < MAX_VISITED) {
            BlockPos pos = queue.poll();
            long column = packXZ(pos.getX(), pos.getZ());
            if (pos.getY() >= maxY) {
                // 撞上垂直上限：这一列没有真正的天花板。
                openColumns.add(column);
            } else if (pos.getY() > ceilings.get(column)) {
                ceilings.put(column, pos.getY());
            }

            for (Direction direction : DIRECTIONS) {
                cursor.setWithOffset(pos, direction);
                if (Math.abs(cursor.getX() - seed.getX()) > FLOOD_H_RADIUS
                        || Math.abs(cursor.getZ() - seed.getZ()) > FLOOD_H_RADIUS
                        || cursor.getY() > maxY
                        || cursor.getY() < minY) {
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

        ceilings.keySet().removeAll(openColumns);
        return ceilings;
    }

    /** 泛洪可通过性：空气 / 无碰撞方块可通过；墙体、玻璃、关闭的门阻挡。 */
    private static boolean isPassable(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // ==================== 剔除快照 ====================

    /** 不可变剔除快照。{@code minX..maxX} 等为它影响到的方块范围，仅用于标脏。 */
    private static final class Cull {
        private final Long2IntOpenHashMap ceilings;
        private final boolean sight;
        private final double originX, originY, originZ;
        private final double cameraX, cameraY, cameraZ;
        private final int minX, minY, minZ, maxX, maxY, maxZ;

        private Cull(Long2IntOpenHashMap ceilings, boolean sight,
                     double originX, double originY, double originZ,
                     double cameraX, double cameraY, double cameraZ,
                     int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.ceilings = ceilings;
            this.sight = sight;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        static Cull of(Long2IntOpenHashMap ceilings, boolean sight, Vec3 origin, Vec3 camera, Level level) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

            for (var entry : ceilings.long2IntEntrySet()) {
                int x = (int) (entry.getLongKey() >> 32);
                int z = (int) entry.getLongKey();
                int ceiling = entry.getIntValue();
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                minY = Math.min(minY, ceiling + 1);
                maxY = Math.max(maxY, ceiling + ROOF_CULL_HEIGHT);
            }

            if (sight) {
                int pad = Mth.ceil(SIGHT_RADIUS) + 1;
                minX = Math.min(minX, Mth.floor(Math.min(origin.x, camera.x)) - pad);
                maxX = Math.max(maxX, Mth.ceil(Math.max(origin.x, camera.x)) + pad);
                minY = Math.min(minY, Mth.floor(Math.min(origin.y, camera.y)) - pad);
                maxY = Math.max(maxY, Mth.ceil(Math.max(origin.y, camera.y)) + pad);
                minZ = Math.min(minZ, Mth.floor(Math.min(origin.z, camera.z)) - pad);
                maxZ = Math.max(maxZ, Mth.ceil(Math.max(origin.z, camera.z)) + pad);
            }

            minY = Math.max(minY, level.getMinBuildHeight());
            maxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
            double camX = sight ? camera.x : 0.0D;
            double camY = sight ? camera.y : 0.0D;
            double camZ = sight ? camera.z : 0.0D;
            return new Cull(ceilings, sight, origin.x, origin.y, origin.z, camX, camY, camZ,
                    minX, minY, minZ, maxX, maxY, maxZ);
        }

        /**
         * 只有视线圆锥的几何参数（玩家 / 相机位置）与逐列天花板都一致，两个快照才等价。
         * 天花板图在玩家于同一房间内走动时保持不变，因此俯视（无圆锥）几乎不触发重建。
         */
        boolean equivalentTo(Cull other) {
            if (other == null || other.sight != this.sight) {
                return false;
            }
            if (this.sight && (other.originX != this.originX || other.originY != this.originY
                    || other.originZ != this.originZ || other.cameraX != this.cameraX
                    || other.cameraY != this.cameraY || other.cameraZ != this.cameraZ)) {
                return false;
            }
            return other.ceilings.equals(this.ceilings);
        }

        boolean hides(int x, int y, int z) {
            // 包围盒早退：本方法在网格构建线程上被逐方块调用，绝大多数方块落在剔除区域之外。
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                return false;
            }
            return aboveCeiling(x, y, z) || insideSightCone(x + 0.5D, y + 0.5D, z + 0.5D);
        }

        private boolean aboveCeiling(int x, int y, int z) {
            int ceiling = ceilings.get(packXZ(x, z));
            return ceiling != NO_CEILING && y > ceiling && y - ceiling <= ROOF_CULL_HEIGHT;
        }

        /** 「玩家 → 相机」轴上的圆锥（近端）/ 圆柱（远端）。轴向背后、相机之外、玩家脚下的方块都不剔。 */
        private boolean insideSightCone(double blockX, double blockY, double blockZ) {
            if (!sight || blockY <= originY) {
                return false;
            }
            double axisX = cameraX - originX;
            double axisY = cameraY - originY;
            double axisZ = cameraZ - originZ;
            double axisLenSq = axisX * axisX + axisY * axisY + axisZ * axisZ;
            if (axisLenSq <= 1.0E-6D) {
                return false;
            }

            double toX = blockX - originX;
            double toY = blockY - originY;
            double toZ = blockZ - originZ;
            if (toX * toX + toY * toY + toZ * toZ >= axisLenSq) {
                return false; // 比相机还远，挡不住玩家
            }
            double dot = toX * axisX + toY * axisY + toZ * axisZ;
            if (dot <= 0.0D) {
                return false; // 在玩家背朝相机的一侧
            }

            double crossX = toY * axisZ - toZ * axisY;
            double crossY = toZ * axisX - toX * axisZ;
            double crossZ = toX * axisY - toY * axisX;
            double perpDistSq = (crossX * crossX + crossY * crossY + crossZ * crossZ) / axisLenSq;
            double projDist = dot / Math.sqrt(axisLenSq);
            double radius = Math.min(projDist * SIGHT_CONE_TAN, SIGHT_RADIUS);
            return perpDistSq <= radius * radius;
        }
    }
}
