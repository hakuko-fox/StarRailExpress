package org.agmas.noellesroles.game.fake_steve;

import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

/** Moves Halic's entity decoy along the same wander goals and A* routes as Fake Steve. */
public final class HalicDecoyWalker {
    private static final double WALK_SPEED = 0.11D;
    private static final double WAYPOINT_REACH_SQR = 0.49D;
    private static final double MIN_PROGRESS_SQR = 0.0025D;
    private static final int STUCK_TICKS = 40;

    private final ArrayDeque<BlockPos> path = new ArrayDeque<>();
    private BlockPos lastGoal;
    private Vec3 lastPosition;
    private long retryAtTick;
    private int stuckTicks;

    public void tick(ServerLevel level, PuppeteerBodyEntity decoy) {
        long now = level.getGameTime();
        if (lastPosition != null && !path.isEmpty()) {
            stuckTicks = lastPosition.distanceToSqr(decoy.position()) < MIN_PROGRESS_SQR
                    ? stuckTicks + 1 : 0;
            if (stuckTicks >= STUCK_TICKS) {
                path.clear();
                retryAtTick = now + 20L;
                stuckTicks = 0;
            }
        }
        lastPosition = decoy.position();

        if (now < retryAtTick) {
            stopHorizontalMotion(decoy);
            return;
        }

        while (!path.isEmpty() && reached(decoy, path.peekFirst())) {
            path.removeFirst();
        }
        if (path.isEmpty() && !chooseRoute(level, decoy)) {
            retryAtTick = now + 20L;
            stopHorizontalMotion(decoy);
            return;
        }

        BlockPos waypoint = path.peekFirst();
        double dx = waypoint.getX() + 0.5D - decoy.getX();
        double dz = waypoint.getZ() + 0.5D - decoy.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double speed = Math.min(WALK_SPEED, distance);
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
