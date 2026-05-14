package org.agmas.noellesroles.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class HunterCageBlockEntity extends BlockEntity {
    private UUID prisoner;
    private UUID captor;
    private int rescueProgress;

    public HunterCageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HUNTER_CAGE_BLOCK_ENTITY, pos, state);
    }

    public Optional<UUID> getPrisoner() {
        return Optional.ofNullable(prisoner);
    }

    public void setPrisoner(UUID prisoner) {
        this.prisoner = prisoner;
        setChangedAndSync();
    }

    public Optional<UUID> getCaptor() {
        return Optional.ofNullable(captor);
    }

    public void setCaptor(UUID captor) {
        this.captor = captor;
        setChangedAndSync();
    }

    public int getRescueProgress() {
        return rescueProgress;
    }

    public boolean addRescueProgress(int amount) {
        rescueProgress = Math.min(100, rescueProgress + amount);
        setChangedAndSync();
        if (rescueProgress >= 100) {
            releasePrisoner();
            return true;
        }
        return false;
    }

    public void releasePrisoner() {
        if (level instanceof ServerLevel serverLevel && prisoner != null) {
            if (serverLevel.getPlayerByUUID(prisoner) instanceof ServerPlayer target) {
                target.teleportTo(worldPosition.getX() + 0.5D, worldPosition.getY(), worldPosition.getZ() + 0.5D);
                target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                target.removeEffect(MobEffects.WEAKNESS);
            }
        }
        if (level != null) {
            level.destroyBlock(worldPosition, false);
        }
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            HunterCageBlockEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || entity.prisoner == null) {
            return;
        }
        if (!(serverLevel.getPlayerByUUID(entity.prisoner) instanceof ServerPlayer target) || target.isSpectator()) {
            return;
        }
        target.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 10, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2, false, false, true));
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (prisoner != null) {
            tag.putUUID("Prisoner", prisoner);
        }
        if (captor != null) {
            tag.putUUID("Captor", captor);
        }
        tag.putInt("RescueProgress", rescueProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        prisoner = tag.hasUUID("Prisoner") ? tag.getUUID("Prisoner") : null;
        captor = tag.hasUUID("Captor") ? tag.getUUID("Captor") : null;
        rescueProgress = tag.getInt("RescueProgress");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
