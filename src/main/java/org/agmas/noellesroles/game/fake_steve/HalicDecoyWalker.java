package org.agmas.noellesroles.game.fake_steve;

import java.util.ArrayDeque;
import java.util.UUID;
import io.wifi.starrailexpress.content.item.KnifeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

/** Moves Halic's entity decoy along the same wander goals and A* routes as Fake Steve. */
public final class HalicDecoyWalker {
    private static final double WALK_SPEED = 0.11D;
    private static final double FLEE_SPEED = 0.20D;
    private static final double KNIFE_THREAT_RANGE_SQR = 25.0D;
    private static final int MIN_FLEE_TICKS = 100;
    private static final double WAYPOINT_REACH_SQR = 0.49D;
    private static final double MIN_PROGRESS_SQR = 0.0025D;
    private static final int STUCK_TICKS = 40;

    private final ArrayDeque<BlockPos> path = new ArrayDeque<>();
    private BlockPos lastGoal;
    private Vec3 lastPosition;
    private long retryAtTick;
    private int stuckTicks;
    private UUID fleeingFrom;
    private Vec3 fleeFromPosition;
    private long fleeUntilTick;
    private long nextFleeRouteTick;

    public void tick(ServerLevel level, PuppeteerBodyEntity decoy) {
        long now = level.getGameTime();
        ServerPlayer threat = nearestRaisedKnife(level, decoy);
        if (threat != null) {
            if (!threat.getUUID().equals(fleeingFrom)) {
                path.clear();
                retryAtTick = 0L;
                stuckTicks = 0;
                nextFleeRouteTick = 0L;
            }
            fleeingFrom = threat.getUUID();
            fleeFromPosition = threat.position();
            fleeUntilTick = now + MIN_FLEE_TICKS;
        }
        boolean fleeing = fleeFromPosition != null && now < fleeUntilTick;
        if (!fleeing && fleeingFrom != null) {
            path.clear();
            retryAtTick = 0L;
            stuckTicks = 0;
            fleeingFrom = null;
            fleeFromPosition = null;
        }
        if (lastPosition != null && !path.isEmpty()) {
            stuckTicks = lastPosition.distanceToSqr(decoy.position()) < MIN_PROGRESS_SQR
                    ? stuckTicks + 1 : 0;
            if (stuckTicks >= STUCK_TICKS) {
                path.clear();
                retryAtTick = now + (fleeing ? 5L : 20L);
                stuckTicks = 0;
            }
        }
        lastPosition = decoy.position();

        if (now < retryAtTick) {
            stopHorizontalMotion(decoy);
            return;
        }

        if (fleeing && now >= nextFleeRouteTick) {
            path.clear();
        }

        if (!path.isEmpty()) {
            openDoorsOnApproach(level, decoy, path.peekFirst());
        }
        while (!path.isEmpty() && reached(decoy, path.peekFirst())) {
            path.removeFirst();
        }
        if (path.isEmpty() && !(fleeing
                ? chooseFleeRoute(level, decoy, fleeFromPosition) : chooseRoute(level, decoy))) {
            retryAtTick = now + (fleeing ? 5L : 20L);
            stopHorizontalMotion(decoy);
            return;
        }
        if (fleeing && nextFleeRouteTick <= now) {
            nextFleeRouteTick = now + 10L;
        }

        BlockPos waypoint = path.peekFirst();
        openDoorsOnApproach(level, decoy, waypoint);
        double dx = waypoint.getX() + 0.5D - decoy.getX();
        double dz = waypoint.getZ() + 0.5D - decoy.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double speed = Math.min(fleeing ? FLEE_SPEED : WALK_SPEED, distance);
        Vec3 horizontal = distance > 0.001D
                ? new Vec3(dx / distance * speed, 0.0D, dz / distance * speed)
                : Vec3.ZERO;
        Vec3 lookAhead = distance > 0.001D
                ? new Vec3(dx / distance * Math.min(0.5D, distance), 0.0D,
                        dz / distance * Math.min(0.5D, distance))
                : Vec3.ZERO;
        if (!FakeSteveNavigator.stepSafe(level, decoy.position(), lookAhead)) {
            path.clear();
            retryAtTick = now + 20L;
            stopHorizontalMotion(decoy);
            return;
        }

        double vertical = decoy.getDeltaMovement().y;
        if (waypoint.getY() > decoy.getY() + 0.65D) {
            if (decoy.onGround()) {
                vertical = 0.42D;
            } else if (decoy.isInWater()) {
                vertical = Math.max(vertical, 0.12D);
            }
        }
        decoy.setDeltaMovement(horizontal.x, vertical, horizontal.z);
        if (distance > 0.001D) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            decoy.setYRot(yaw);
            decoy.setYBodyRot(yaw);
            decoy.setYHeadRot(yaw);
        }
    }

    private static ServerPlayer nearestRaisedKnife(ServerLevel level, PuppeteerBodyEntity decoy) {
        ServerPlayer nearest = null;
        double nearestDistanceSqr = KNIFE_THREAT_RANGE_SQR;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || !player.isUsingItem()
                    || !(player.getUseItem().getItem() instanceof KnifeItem)) {
                continue;
            }
            double distanceSqr = decoy.distanceToSqr(player);
            if (distanceSqr <= nearestDistanceSqr) {
                nearest = player;
                nearestDistanceSqr = distanceSqr;
            }
        }
        return nearest;
    }

    private boolean chooseFleeRoute(ServerLevel level, PuppeteerBodyEntity decoy,
                                    Vec3 threatPosition) {
        double awayX = decoy.getX() - threatPosition.x;
        double awayZ = decoy.getZ() - threatPosition.z;
        double length = Math.hypot(awayX, awayZ);
        if (length < 0.01D) {
            awayX = Math.sin(Math.toRadians(decoy.getYRot()));
            awayZ = Math.cos(Math.toRadians(decoy.getYRot()));
            length = 1.0D;
        }
        awayX /= length;
        awayZ /= length;
        BlockPos origin = decoy.blockPosition();
        for (int distance : new int[] { 10, 6 }) {
            for (int side : new int[] { 0, -4, 4 }) {
                BlockPos goal = origin.offset(
                        (int) Math.round(awayX * distance - awayZ * side), 0,
                        (int) Math.round(awayZ * distance + awayX * side));
                if (!FakeSteveNavigator.safeStand(level, goal)
                        || Vec3.atCenterOf(goal).distanceToSqr(threatPosition)
                                <= decoy.position().distanceToSqr(threatPosition)) {
                    continue;
                }
                ArrayDeque<BlockPos> candidate = FakeSteveNavigator.find(level, decoy, goal);
                if (!candidate.isEmpty() && FakeSteveNavigator.reaches(candidate, goal)) {
                    path.addAll(candidate);
                    stuckTicks = 0;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean chooseRoute(ServerLevel level, PuppeteerBodyEntity decoy) {
        for (int attempt = 0; attempt < 3; attempt++) {
            BlockPos goal = FakeSteveNavigator.randomWanderGoal(
                    level, decoy.blockPosition(), lastGoal);
            if (goal == null) {
                return false;
            }
            ArrayDeque<BlockPos> candidate = FakeSteveNavigator.find(level, decoy, goal);
            if (!candidate.isEmpty() && FakeSteveNavigator.reaches(candidate, goal)) {
                path.addAll(candidate);
                lastGoal = goal;
                stuckTicks = 0;
                return true;
            }
        }
        return false;
    }

    private static void openDoorsOnApproach(ServerLevel level, PuppeteerBodyEntity decoy,
                                            BlockPos next) {
        Vec3 position = decoy.position();
        Vec3 route = Vec3.atBottomCenterOf(next);
        Vec3 direction = route.subtract(position);
        if (direction.horizontalDistance() > 3.0D) {
            route = position.add(new Vec3(direction.x, 0.0D, direction.z).normalize().scale(3.0D));
        }
        BlockPos end = BlockPos.containing(route);
        for (BlockPos pos : BlockPos.betweenClosed(
                Math.min(decoy.blockPosition().getX(), end.getX()) - 1,
                Math.min(decoy.blockPosition().getY(), end.getY()) - 1,
                Math.min(decoy.blockPosition().getZ(), end.getZ()) - 1,
                Math.max(decoy.blockPosition().getX(), end.getX()) + 1,
                Math.max(decoy.blockPosition().getY(), end.getY()) + 2,
                Math.max(decoy.blockPosition().getZ(), end.getZ()) + 1)) {
            BlockState state = level.getBlockState(pos);
            if (!FakeSteveDoorAccess.isOpenablePassage(state)
                    || FakeSteveDoorAccess.isOpen(state)
                    || !FakeSteveDoorAccess.isInsideApproachCorridor(
                            decoy.getX(), decoy.getZ(), route.x, route.z,
                            pos.getX() + 0.5D, pos.getZ() + 0.5D)) {
                continue;
            }
            if (state.getBlock() instanceof SmallDoorBlock door) {
                BlockPos lower = door.getLowerHalfPos(state, pos);
                if (level.getBlockEntity(lower) instanceof SmallDoorBlockEntity entity
                        && !entity.isJammed() && !entity.isBlasted()
                        && entity.getKeyName().isEmpty()
                        && !FakeSteveDoorAccess.hasExternalDoorLock(lower)) {
                    door.toggleDoor(level.getBlockState(lower), level, entity, lower);
                }
            } else if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(decoy, level, state, pos, true);
            } else if (state.hasProperty(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.OPEN, true));
            }
        }
    }

    private static boolean reached(PuppeteerBodyEntity decoy, BlockPos waypoint) {
        double dx = waypoint.getX() + 0.5D - decoy.getX();
        double dz = waypoint.getZ() + 0.5D - decoy.getZ();
        return dx * dx + dz * dz <= WAYPOINT_REACH_SQR
                && Math.abs(decoy.getY() - waypoint.getY()) <= 0.85D;
    }

    private static void stopHorizontalMotion(PuppeteerBodyEntity decoy) {
        decoy.setDeltaMovement(0.0D, decoy.getDeltaMovement().y, 0.0D);
    }
}
