package org.agmas.noellesroles.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

public class RepairStationBlockEntity extends BlockEntity {
    private int progress;
    private int animationTicks;
    private int jamTicks;

    public RepairStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.REPAIR_STATION_BLOCK_ENTITY, pos, state);
    }

    public int getProgress() {
        return progress;
    }

    public int getAnimationTicks() {
        return animationTicks;
    }

    public int getJamTicks() {
        return jamTicks;
    }

    public boolean isJammed() {
        return jamTicks > 0;
    }

    public boolean isCompleted() {
        return progress >= RepairModeState.REPAIR_STATION_MAX_PROGRESS;
    }

    public boolean addProgress(int amount) {
        if (isCompleted()) {
            return false;
        }
        if (isJammed()) {
            amount = Math.max(1, amount / 2);
        }
        progress = Math.min(RepairModeState.REPAIR_STATION_MAX_PROGRESS, progress + amount);
        animationTicks = 12;
        setChangedAndSync();
        if (level instanceof ServerLevel serverLevel && isCompleted()) {
            RepairModeState.stationCompleted(serverLevel, worldPosition);
        }
        return true;
    }

    public void sabotage(int amount, int durationTicks) {
        if (isCompleted()) {
            return;
        }
        progress = Math.max(0, progress - amount);
        jamTicks = Math.max(jamTicks, durationTicks);
        animationTicks = 20;
        setChangedAndSync();
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            RepairStationBlockEntity entity) {
        boolean changed = false;
        if (entity.animationTicks > 0) {
            entity.animationTicks--;
            changed = true;
        }
        if (entity.jamTicks > 0) {
            entity.jamTicks--;
            changed = true;
        }
        if (changed) {
            entity.setChangedAndSync();
        }
    }

    public void setChangedAndSync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("Progress", progress);
        tag.putInt("AnimationTicks", animationTicks);
        tag.putInt("JamTicks", jamTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        progress = tag.getInt("Progress");
        animationTicks = tag.getInt("AnimationTicks");
        jamTicks = tag.getInt("JamTicks");
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
