package org.agmas.noellesroles.content.entity;

import java.util.List;

import org.agmas.noellesroles.init.ModEntities;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 滚石实体：沿指定方向滚动，碾死沿途玩家，撞墙或寿命耗尽后碎裂。渲染为一块巨大的原版石头。
 */
public class RollingStoneEntity extends Entity {

    private static final double SPEED = 0.42;
    private static final int MAX_LIFE = 200;

    private double dirX;
    private double dirZ;
    private int life = MAX_LIFE;

    public RollingStoneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public static RollingStoneEntity spawn(ServerLevel level, Vec3 pos, Direction dir) {
        RollingStoneEntity e = new RollingStoneEntity(ModEntities.ROLLING_STONE, level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(dir.toYRot());
        e.dirX = dir.getStepX();
        e.dirZ = dir.getStepZ();
        e.setDeltaMovement(e.dirX * SPEED, 0, e.dirZ * SPEED);
        level.addFreshEntity(e);
        level.playSound(null, e.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.4F, 0.5F);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.dirX = tag.getDouble("DirX");
        this.dirZ = tag.getDouble("DirZ");
        this.life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("DirX", this.dirX);
        tag.putDouble("DirZ", this.dirZ);
        tag.putInt("Life", this.life);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        double vy = this.onGround() ? 0.0 : this.getDeltaMovement().y - 0.08;
        this.setDeltaMovement(this.dirX * SPEED, vy, this.dirZ * SPEED);
        this.move(MoverType.SELF, this.getDeltaMovement());

        ServerLevel level = (ServerLevel) this.level();

        // 碾压玩家
        List<Player> hit = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(0.15),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (Player p : hit) {
            GameUtils.forceKillPlayer(p, true, null, GameConstants.DeathReasons.BOULDER_CRUSH);
        }

        // 滚动尘土 + 轰隆声
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.6, 0.2, 0.6, 0.02);
        if (this.tickCount % 5 == 0) {
            level.playSound(null, this.blockPosition(), SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.6F, 0.5F);
        }

        // 撞到完整方块 → 碎裂；非完整方块（如花草、火把）直接穿过
        if (this.horizontalCollision) {
            if (isHittingSolidBlock(level)) {
                shatter(level);
                this.discard();
                return;
            }
        }
        // 寿命耗尽 → 碎裂
        if (--this.life <= 0) {
            shatter(level);
            this.discard();
        }
    }

    private void shatter(ServerLevel level) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                this.getX(), this.getY() + 0.5, this.getZ(), 60, 0.8, 0.8, 0.8, 0.2);
        level.playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.8F, 0.6F);
    }

    /** 检查滚石前进方向是否撞到了完整方块。 */
    private boolean isHittingSolidBlock(ServerLevel level) {
        // 沿移动方向检查滚石 AABB 前方的方块
        int stepX = this.dirX > 0 ? 1 : this.dirX < 0 ? -1 : 0;
        int stepZ = this.dirZ > 0 ? 1 : this.dirZ < 0 ? -1 : 0;
        var box = this.getBoundingBox();
        // 检查滚石前方可能碰撞的方块位置（覆盖整个 AABB 高度范围）
        for (int y = (int) Math.floor(box.minY); y <= (int) Math.ceil(box.maxY); y++) {
            BlockPos checkPos = new BlockPos(
                    (int) Math.floor(stepX > 0 ? box.maxX : stepX < 0 ? box.minX - 1 : box.minX),
                    y,
                    (int) Math.floor(stepZ > 0 ? box.maxZ : stepZ < 0 ? box.minZ - 1 : box.minZ));
            // 沿移动方向偏移
            checkPos = checkPos.offset(stepX, 0, stepZ);
            if (level.getBlockState(checkPos).isCollisionShapeFullBlock(level, checkPos)) {
                return true;
            }
        }
        return false;
    }
}
