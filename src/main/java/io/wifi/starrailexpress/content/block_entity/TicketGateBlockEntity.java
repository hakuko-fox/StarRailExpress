package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block.TicketGateBlock;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class TicketGateBlockEntity extends SmallDoorBlockEntity {
    private String ticketId = "";
    protected boolean redstoneOpen = false;

    public TicketGateBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.TICKET_GATE, pos, state);
    }

    public String getTicketId() {
        return ticketId == null ? "" : ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId == null ? "" : ticketId;
        sync();
    }

    public boolean hasTicket() {
        return !getTicketId().isBlank();
    }

    public void setRedstoneOpen(boolean open) {
        if (this.redstoneOpen == open && this.open == open) {
            return;
        }
        this.redstoneOpen = open;
        this.open = open;
        toggleBlocks();
        if (this.level != null) {
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 1, open ? 1 : 0);
        }
        sync();
    }

    public static <T extends TicketGateBlockEntity> void serverTick(Level level, BlockPos pos, BlockState state,
            T entity) {
        SmallDoorBlockEntity.serverTick(level, pos, state, entity);
        if (level.getGameTime() % 5L == 0L) {
            boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
            if (powered || entity.redstoneOpen) {
                entity.setRedstoneOpen(powered);
            }
            if (state.getValue(SmallDoorBlock.POWERED) != powered) {
                level.setBlock(pos, state.setValue(SmallDoorBlock.POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void toggleBlocks() {
        if (this.level == null) {
            return;
        }
        BlockState lower = this.getBlockState().setValue(TicketGateBlock.OPEN, this.open)
                .setValue(TicketGateBlock.HALF, DoubleBlockHalf.LOWER);
        this.level.setBlock(this.worldPosition, lower, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        this.level.setBlock(this.worldPosition.above(), lower.setValue(TicketGateBlock.HALF, DoubleBlockHalf.UPPER),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.saveAdditional(tag, registryLookup);
        tag.putString("TicketId", getTicketId());
        tag.putBoolean("RedstoneOpen", redstoneOpen);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.loadAdditional(tag, registryLookup);
        this.ticketId = tag.getString("TicketId");
        this.redstoneOpen = tag.getBoolean("RedstoneOpen");
    }
}
