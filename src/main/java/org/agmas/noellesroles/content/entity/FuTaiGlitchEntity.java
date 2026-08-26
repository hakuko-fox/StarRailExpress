package org.agmas.noellesroles.content.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Legacy entity kept registered so worlds saved with the retired Fu Tai passive remain loadable. */
public class FuTaiGlitchEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            FuTaiGlitchEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public FuTaiGlitchEntity(EntityType<? extends FuTaiGlitchEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public FuTaiGlitchEntity(EntityType<? extends FuTaiGlitchEntity> entityType, Level level,
            double x, double y, double z, UUID ownerUuid) {
        this(entityType, level);
        setPos(x, y, z);
        entityData.set(OWNER_UUID, Optional.of(ownerUuid));
    }

    @Override
    public ItemStack getItem() {
        return Items.REDSTONE.getDefaultInstance();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            discard();
            return;
        }
        setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER_UUID).ifPresent(uuid -> tag.putUUID("FuTaiOwner", uuid));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("FuTaiOwner")) {
            entityData.set(OWNER_UUID, Optional.of(tag.getUUID("FuTaiOwner")));
        }
        setNoGravity(true);
        noPhysics = true;
    }
}
