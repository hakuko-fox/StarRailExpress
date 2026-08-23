package org.agmas.noellesroles.content.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.innocence.futai.FuTaiPlayerComponent;

/** A round-scoped redstone glitch that can only be collected by its FuTai owner. */
public class FuTaiGlitchEntity extends ItemEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            FuTaiGlitchEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public FuTaiGlitchEntity(EntityType<? extends ItemEntity> entityType, Level level) {
        super(entityType, level);
        setItem(Items.REDSTONE.getDefaultInstance());
        setNeverPickUp();
        setUnlimitedLifetime();
    }

    public FuTaiGlitchEntity(EntityType<? extends ItemEntity> entityType, Level level,
            double x, double y, double z, UUID ownerUuid) {
        this(entityType, level);
        setPos(x, y, z);
        entityData.set(OWNER_UUID, Optional.of(ownerUuid));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || !isAlive()) {
            return false;
        }
        Optional<UUID> ownerUuid = entityData.get(OWNER_UUID);
        if (ownerUuid.isEmpty() || !(source.getEntity() instanceof ServerPlayer attacker)
                || !ownerUuid.get().equals(attacker.getUUID())) {
            return false;
        }
        FuTaiPlayerComponent component = FuTaiPlayerComponent.KEY.maybeGet(attacker).orElse(null);
        if (component != null && component.tryCollectGlitch(attacker)) {
            discard();
        }
        return false;
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
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER_UUID).ifPresent(uuid -> tag.putUUID("FuTaiOwner", uuid));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("FuTaiOwner")) {
            entityData.set(OWNER_UUID, Optional.of(tag.getUUID("FuTaiOwner")));
        }
        setItem(Items.REDSTONE.getDefaultInstance());
        setNeverPickUp();
        setUnlimitedLifetime();
    }
}
