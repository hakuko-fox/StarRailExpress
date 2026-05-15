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
import net.minecraft.world.item.context.UseOnContext;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.block_entity.HunterCageBlockEntity;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
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
        if (!(player.level() instanceof ServerLevel level) || !(target instanceof ServerPlayer prisoner)
                || !(player instanceof ServerPlayer hunter)) {
            return InteractionResult.PASS;
        }
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        var prisonerComponent = ModComponents.REPAIR_ROLES.get(prisoner);
        if (!RepairModeState.isHunter(hunter) || !prisonerComponent.downed) {
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.chain_requires_downed")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (hunterComponent.carryBlockedTicks > 0) {
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.carry_blocked",
                    Math.max(1, hunterComponent.carryBlockedTicks / 20)).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (hunterComponent.carrying != null) {
            RepairModeState.releaseCarried(hunter);
        }
        hunterComponent.carrying = prisoner.getUUID();
        prisonerComponent.carriedBy = hunter.getUUID();
        hunterComponent.sync();
        prisonerComponent.sync();
        prisoner.teleportTo(hunter.getX(), hunter.getY(), hunter.getZ());
        RepairGameplayEffects.burst(level, hunter.getX(), hunter.getY() + 0.8D, hunter.getZ(), 1);
        hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.carrying", prisoner.getName()), true);
        prisoner.displayClientMessage(Component.translatable("message.noellesroles.repair.you_are_carried"), false);
        if (!hunter.getAbilities().instabuild) {
            stack.hurtAndBreak(1, hunter, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer hunter)) {
            return InteractionResult.SUCCESS;
        }
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        if (hunterComponent.carrying == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof HunterCageBlockEntity cage)) {
            return InteractionResult.PASS;
        }
        if (!cage.addPrisoner(hunterComponent.carrying, hunter.getUUID())) {
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.trial_full")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (context.getLevel() instanceof ServerLevel level
                && level.getPlayerByUUID(hunterComponent.carrying) instanceof ServerPlayer prisoner) {
            var prisonerComponent = ModComponents.REPAIR_ROLES.get(prisoner);
            prisonerComponent.carriedBy = null;
            prisonerComponent.trialStand = org.agmas.noellesroles.component.RepairRolePlayerComponent.BlockPosTag.of(context.getClickedPos());
            prisoner.teleportTo(context.getClickedPos().getX() + 0.5D, context.getClickedPos().getY(),
                    context.getClickedPos().getZ() + 0.5D);
            prisonerComponent.sync();
            RepairGameplayEffects.burst(level, context.getClickedPos().getX() + 0.5D,
                    context.getClickedPos().getY() + 1.0D, context.getClickedPos().getZ() + 0.5D, 2);
        }
        hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.trial_started"), true);
        hunterComponent.carrying = null;
        hunterComponent.sync();
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
