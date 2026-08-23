package org.agmas.noellesroles.game.roles.innocence.futai;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.FuTaiGlitchEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/** FuTai's seven round-scoped redstone glitches and oracle skill. */
public class FuTaiPlayerComponent implements RoleComponent {
    private static final int GLITCH_TOTAL = 7;
    private static final int UPGRADE_THRESHOLD = 5;
    private static final double SPAWN_EXCLUSION_DISTANCE = 16.0D;
    private static final Set<UUID> ROUND_GLITCHES = new HashSet<>();

    public static final ComponentKey<FuTaiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fu_tai"),
            FuTaiPlayerComponent.class);

    private final Player player;
    private int collectedGlitches;
    private long nextOracleTick;

    public FuTaiPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer other) {
        return other == player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        collectedGlitches = 0;
        nextOracleTick = 0L;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public int getCollectedGlitches() {
        return collectedGlitches;
    }

    public boolean tryCollectGlitch(ServerPlayer collector) {
        if (!GameUtils.isPlayerAliveAndSurvival(collector)
                || !SREGameWorldComponent.KEY.get(collector.level()).isRole(collector, ModRoles.FU_TAI)
                || collectedGlitches >= GLITCH_TOTAL) {
            return false;
        }
        collectedGlitches++;
        SREPlayerShopComponent.KEY.get(collector).addToBalance(30);
        sync();
        collector.displayClientMessage(Component.translatable(
                "message.noellesroles.fu_tai.glitch_collected", collectedGlitches, GLITCH_TOTAL), true);
        if (collectedGlitches == UPGRADE_THRESHOLD) {
            collector.displayClientMessage(Component.translatable(
                    "message.noellesroles.fu_tai.oracle_upgraded"), false);
        }
        return true;
    }

    public boolean useOracleSkill(ServerPlayer user, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return false;
        }
        long now = user.level().getGameTime();
        if (now < nextOracleTick) {
            long seconds = (nextOracleTick - now + 19L) / 20L;
            user.displayClientMessage(Component.translatable(
                    "message.noellesroles.fu_tai.oracle_cooldown", seconds), true);
            return false;
        }

        boolean upgraded = collectedGlitches >= UPGRADE_THRESHOLD;
        int cost = upgraded ? 0 : 200;
        var shop = SREPlayerShopComponent.KEY.get(user);
        if (shop.balance < cost) {
            user.displayClientMessage(Component.translatable(
                    "message.noellesroles.fu_tai.not_enough_money", cost), true);
            return false;
        }
        if (cost > 0) {
            shop.addToBalance(-cost);
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(user.level());
        int killers = 0;
        int neutrals = 0;
        for (ServerPlayer target : user.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            var role = game.getRole(target);
            if (role == null) {
                continue;
            }
            if (role.isNeutrals()) {
                neutrals++;
            } else if (game.isKillerTeamRole(role)) {
                killers++;
            }
        }
        nextOracleTick = now + 20L * (upgraded ? 120L : 150L);
        user.displayClientMessage(Component.translatable(
                "message.noellesroles.fu_tai.oracle_result", killers, neutrals), true);
        return true;
    }

    private static void spawnRoundGlitches(ServerLevel level, ServerPlayer owner) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        AABB playArea = areas.getPlayArea();
        Vec3 spawn = areas.getSpawnPos().pos;
        int spawned = 0;
        int attempts = 0;
        while (spawned < GLITCH_TOTAL && attempts++ < GLITCH_TOTAL * 300) {
            double x = Mth.lerp(level.random.nextDouble(), playArea.minX + 0.5D, playArea.maxX - 0.5D);
            double z = Mth.lerp(level.random.nextDouble(), playArea.minZ + 0.5D, playArea.maxZ - 0.5D);
            double dx = x - spawn.x;
            double dz = z - spawn.z;
            if (dx * dx + dz * dz < SPAWN_EXCLUSION_DISTANCE * SPAWN_EXCLUSION_DISTANCE) {
                continue;
            }
            BlockPos floor = findFloor(level, playArea, x, z);
            if (floor == null) {
                continue;
            }
            FuTaiGlitchEntity glitch = new FuTaiGlitchEntity(ModEntities.FU_TAI_GLITCH, level,
                    floor.getX() + 0.5D, floor.getY() + 1.1D, floor.getZ() + 0.5D, owner.getUUID());
            if (level.addFreshEntity(glitch)) {
                ROUND_GLITCHES.add(glitch.getUUID());
                spawned++;
            }
        }
        if (spawned < GLITCH_TOTAL) {
            Noellesroles.LOGGER.warn("Only spawned {}/{} FuTai glitches inside the current play area", spawned,
                    GLITCH_TOTAL);
        }
    }

    private static BlockPos findFloor(ServerLevel level, AABB playArea, double x, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, Mth.floor(playArea.maxY) - 1);
        int minY = Math.max(level.getMinBuildHeight(), Mth.ceil(playArea.minY));
        for (int y = maxY; y >= minY; y--) {
            BlockPos floor = new BlockPos(blockX, y, blockZ);
            BlockPos standing = floor.above();
            if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                    && level.getBlockState(standing).getCollisionShape(level, standing).isEmpty()
                    && level.getBlockState(standing.above()).getCollisionShape(level, standing.above()).isEmpty()) {
                return floor;
            }
        }
        return null;
    }

    private static void clearRoundGlitches(ServerLevel level) {
        for (UUID uuid : Set.copyOf(ROUND_GLITCHES)) {
            var entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        ROUND_GLITCHES.clear();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("CollectedGlitches", collectedGlitches);
        tag.putLong("NextOracleTick", nextOracleTick);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        collectedGlitches = tag.getInt("CollectedGlitches");
        nextOracleTick = tag.getLong("NextOracleTick");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
    }

    static {
        OnGameTrueStarted.EVENT.register(level -> {
            clearRoundGlitches(level);
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
            for (ServerPlayer candidate : level.players()) {
                if (game.isRole(candidate, ModRoles.FU_TAI)) {
                    FuTaiPlayerComponent component = KEY.get(candidate);
                    component.init();
                    spawnRoundGlitches(level, candidate);
                }
            }
        });
        OnGameEnd.EVENT.register((level, game) -> clearRoundGlitches(level));
    }
}
