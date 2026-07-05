package io.wifi.starrailexpress.content.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ZiplineRiderEntity extends Entity {
    private static final float RIDE_SPEED = 0.05f; // 滑动速度（每 tick 的比例，0~1）
    private static final double MAX_DISTANCE = 4.0; // 脱离距离阈值

    @Nullable
    private BlockPos startPos;
    @Nullable
    private BlockPos endPos;
    private float progress = 0f; // 0~1，滑动进度
    private int rideTick = 0;

    public ZiplineRiderEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        NbtUtils.readBlockPos(tag, "StartPos").ifPresent(pos -> this.startPos = pos);
        NbtUtils.readBlockPos(tag, "EndPos").ifPresent(pos -> this.endPos = pos);
        this.progress = tag.getFloat("Progress");
        this.rideTick = tag.getInt("RideTick");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (startPos != null) tag.put("StartPos", NbtUtils.writeBlockPos(startPos));
        if (endPos != null) tag.put("EndPos", NbtUtils.writeBlockPos(endPos));
        tag.putFloat("Progress", progress);
        tag.putInt("RideTick", rideTick);
    }

    public void setStartAndEnd(BlockPos start, BlockPos end) {
        this.startPos = start;
        this.endPos = end;
        this.progress = 0f;
        // 初始位置设在起点
        Vec3 startCenter = Vec3.atCenterOf(start).add(0, 0.5, 0);
        this.setPos(startCenter);
    }

    @Override
    public void tick() {
        super.tick();

        if (startPos == null || endPos == null) {
            ejectAndDiscard();
            return;
        }

        // 检查骑乘者是否仍然有效
        if (!this.isVehicle()) {
            this.discard();
            return;
        }

        Entity passenger = this.getFirstPassenger();
        if (passenger == null) {
            this.discard();
            return;
        }

        // 检查脱离条件
        if (shouldDetach(passenger)) {
            ejectAndDiscard();
            return;
        }

        // 音效 - 每 5 tick 播放一次滑动音效
        if (rideTick % 5 == 0) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, 0.3f, 1.2f);
        }

        // 更新进度
        progress += RIDE_SPEED;
        if (progress >= 1.0f) {
            progress = 1.0f;
            // 到达终点
            Vec3 endCenter = Vec3.atCenterOf(endPos).add(0, 0.5, 0);
            this.setPos(endCenter);
            ejectAndDiscard();
            return;
        }

        // 平滑计算当前位置
        Vec3 startCenter = Vec3.atCenterOf(startPos).add(0, 0.5, 0);
        Vec3 endCenter = Vec3.atCenterOf(endPos).add(0, 0.5, 0);
        Vec3 currentPos = startCenter.lerp(endCenter, progress);
        this.setPos(currentPos);

        // 让乘客看向终点
        if (passenger instanceof Player player) {
            Vec3 lookTarget = endCenter.subtract(currentPos).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-lookTarget.x, lookTarget.z));
            float pitch = (float) Math.toDegrees(Math.asin(-lookTarget.y));
            // 平滑设置朝向
            player.setYRot(yaw);
            player.setXRot(pitch);
        }

        rideTick++;
    }

    /**
     * 检查是否应该脱离滑索
     */
    private boolean shouldDetach(Entity passenger) {
        // 1. 旁观模式检查
        if (passenger instanceof Player player) {
            if (player.isSpectator()) {
                return true;
            }
        }

        // 2. 距离检查 - 如果乘客距滑索路径超过 MAX_DISTANCE
        Vec3 startCenter = Vec3.atCenterOf(startPos).add(0, 0.5, 0);
        Vec3 endCenter = Vec3.atCenterOf(endPos).add(0, 0.5, 0);
        Vec3 pathDir = endCenter.subtract(startCenter).normalize();

        // 计算乘客到滑索路径的最短距离
        Vec3 passengerPos = passenger.position();
        Vec3 toPassenger = passengerPos.subtract(startCenter);
        double t = toPassenger.dot(pathDir);
        double pathLength = endCenter.distanceTo(startCenter);
        t = Math.max(0, Math.min(pathLength, t));

        Vec3 closestPointOnPath = startCenter.add(pathDir.scale(t));
        double distanceToPath = passengerPos.distanceTo(closestPointOnPath);

        if (distanceToPath > MAX_DISTANCE) {
            return true;
        }

        return false;
    }

    private void ejectAndDiscard() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }
        this.discard();
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return false;
    }
}
