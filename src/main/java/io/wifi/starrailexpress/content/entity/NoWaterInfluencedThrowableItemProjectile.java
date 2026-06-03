package io.wifi.starrailexpress.content.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public abstract class NoWaterInfluencedThrowableItemProjectile extends ThrowableItemProjectile {

    public NoWaterInfluencedThrowableItemProjectile(
            EntityType<? extends NoWaterInfluencedThrowableItemProjectile> entityType,
            Level level) {
        super(entityType, level);
    }

    public NoWaterInfluencedThrowableItemProjectile(
            EntityType<? extends NoWaterInfluencedThrowableItemProjectile> entityType, double d,
            double e, double f, Level level) {
        super(entityType, d, e, f, level);
    }

    public NoWaterInfluencedThrowableItemProjectile(
            EntityType<? extends NoWaterInfluencedThrowableItemProjectile> entityType,
            LivingEntity livingEntity, Level level) {
        super(entityType, livingEntity, level);
    }

    /**
     * 禁用气泡柱对其的影响
     */
    @Override
    public void onInsideBubbleColumn(boolean _bl) {
        this.resetFallDistance();
    }

    /**
     * 禁用气泡柱对其的影响
     */
    @Override
    public void tick() {
        super.tick();
        Vec3 vec3 = this.getDeltaMovement();
        float h;
        if (this.isInWater()) {
            h = 1.2375F; // 去掉水的影响
        } else {
            h = 1F;
        }
        this.setDeltaMovement(vec3.scale((double) h));
    }

    /**
     * 禁用气泡柱对其的影响 2
     */
    @Override
    public void onAboveBubbleCol(boolean _bl) {
    }

    /**
     * 禁用水对其的影响
     */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }
}
