package io.wifi.events.day_night_fight.block;

import io.wifi.events.day_night_fight.DNF;
import io.wifi.events.day_night_fight.DNFItems;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class LabBlock extends Block {

    public LabBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 检查是否手持实验室卡
        if (stack.is(DNFItems.LAB_CARD)) {
            if (world.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return ItemInteractionResult.FAIL;
            }
            return teleportToLab(serverPlayer, pos).consumesAction()
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.FAIL;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !canUseLabBlock(serverPlayer)) {
            return InteractionResult.PASS;
        }
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.lab_block.need_card")
                .withStyle(ChatFormatting.YELLOW), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.05;
        double z = pos.getZ() + 0.5;
        // 紫色粒子效果
        world.addParticle(ParticleTypes.PORTAL, x, y, z, 0, 0.05, 0);
        world.addParticle(ParticleTypes.REVERSE_PORTAL, x + (random.nextDouble() - 0.5) * 0.3, y, z + (random.nextDouble() - 0.5) * 0.3,
                0, 0.02, 0);
    }

    private ItemInteractionResult teleportToLab(ServerPlayer player, BlockPos blockPos) {
        if (!canUseLabBlock(player)) {
            player.displayClientMessage(Component.translatable("message.dnf.lab_block.cannot_use")
                    .withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.FAIL;
        }

        // 计算目标位置（向下 20 格）
        BlockPos targetPos = blockPos.below(io.wifi.events.day_night_fight.DNF.LAB_TELEPORT_OFFSET_Y);
        
        // 检查目标位置是否安全（确保不是固体方块）
        Level world = player.level();
        if (world.getBlockState(targetPos).isSolid() || world.getBlockState(targetPos.above()).isSolid()) {
            player.displayClientMessage(Component.translatable("message.dnf.lab_block.unsafe_location")
                    .withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.FAIL;
        }

        // 执行传送
        Vec3 targetVec = new Vec3(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        player.teleportTo(targetVec.x, targetVec.y, targetVec.z);
        
        // 播放传送音效
        world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        
        // 生成传送粒子效果
        if (world instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, targetVec.x, targetVec.y + 1, targetVec.z,
                    20, 0.5, 0.5, 0.5, 0.1);
        }

        player.displayClientMessage(Component.translatable("message.dnf.lab_block.teleport_success")
                .withStyle(ChatFormatting.AQUA), true);
        
        // 消耗实验室卡（如果不是创造模式）
        if (!player.isCreative()) {
            ItemStack handItem = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (handItem.is(DNFItems.LAB_CARD)) {
                handItem.shrink(1);
            }
        }

        return ItemInteractionResult.SUCCESS;
    }

    private static boolean canUseLabBlock(ServerPlayer player) {
        return DNF.isDayNightFightMode(player.level()) && GameUtils.isPlayerAliveAndSurvival(player);
    }
}
