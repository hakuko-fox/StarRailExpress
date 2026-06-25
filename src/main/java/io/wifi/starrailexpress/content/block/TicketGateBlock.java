package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.TicketGateBlockEntity;
import io.wifi.starrailexpress.content.item.AdmissionTicketItem;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TicketGateBlock extends SmallDoorBlock {
    public TicketGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        return state == null ? null : state.setValue(OPEN, false).setValue(POWERED, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new TicketGateBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return level.isClientSide ? createTickerHelper(type, getBlockEntityType(), DoorBlockEntity::clientTick)
                : createTickerHelper(type, getBlockEntityType(), TicketGateBlockEntity::serverTick);
    }

    @Override
    protected BlockEntityType<? extends DoorBlockEntity> getBlockEntityType() {
        return TMMBlockEntities.TICKET_GATE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return handleGateUse(state, level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = handleGateUse(state, level, pos, player);
        return result.consumesAction() ? ItemInteractionResult.CONSUME : ItemInteractionResult.FAIL;
    }

    private InteractionResult handleGateUse(BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!(level.getBlockEntity(lowerPos) instanceof TicketGateBlockEntity gate)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getMainHandItem();
        if (player.isCreative() && player.isShiftKeyDown() && stack.is(TMMItems.ADMISSION_TICKET)) {
            if (!level.isClientSide) {
                String ticketId = AdmissionTicketItem.getTicketId(stack);
                if (ticketId.isBlank()) {
                    player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_gate.invalid_ticket"),
                            true);
                    return InteractionResult.FAIL;
                }
                gate.setTicketId(ticketId);
                player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_gate.bound"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (!gate.hasTicket()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_gate.not_bound"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!AdmissionTicketItem.matches(stack, gate.getTicketId())) {
            if (!level.isClientSide) {
                level.playSound(null, lowerPos, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_gate.requires_ticket"),
                        true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            if (!player.isCreative()) {
                AdmissionTicketItem.consumeUse(stack);
            }
            open(state, level, gate, lowerPos);
        }
        return InteractionResult.CONSUME;
    }
}
