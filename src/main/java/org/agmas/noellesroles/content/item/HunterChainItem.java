package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.block_entity.HunterCageBlockEntity;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;

import java.util.List;

public class HunterChainItem extends Item {
    public HunterChainItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player.level() instanceof ServerLevel level) || !(target instanceof ServerPlayer prisoner)) {
            return InteractionResult.PASS;
        }
        if (prisoner == player || prisoner.isSpectator()) {
            return InteractionResult.PASS;
        }
        BlockPos cagePos = prisoner.blockPosition();
        if (!level.getBlockState(cagePos).canBeReplaced()) {
            cagePos = cagePos.above();
        }
        if (!level.getBlockState(cagePos).canBeReplaced()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.no_cage_space")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        BlockState state = ModBlocks.HUNTER_CAGE.defaultBlockState();
        level.setBlockAndUpdate(cagePos, state);
        if (level.getBlockEntity(cagePos) instanceof HunterCageBlockEntity cage) {
            cage.setPrisoner(prisoner.getUUID());
            cage.setCaptor(player.getUUID());
        }
        prisoner.teleportTo(cagePos.getX() + 0.5D, cagePos.getY(), cagePos.getZ() + 0.5D);
        RepairGameplayEffects.burst(level, cagePos.getX() + 0.5D, cagePos.getY() + 0.8D, cagePos.getZ() + 0.5D, 2);
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.caged", prisoner.getName()), true);
        prisoner.displayClientMessage(Component.translatable("message.noellesroles.repair.you_are_caged"), false);
        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.hunter_chain.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
