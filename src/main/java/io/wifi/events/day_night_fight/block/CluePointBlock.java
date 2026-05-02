package io.wifi.events.day_night_fight.block;

import io.wifi.events.day_night_fight.cca.DNFUnderworldComponent;
import io.wifi.events.day_night_fight.cca.DNFWorldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * 发光线索点方块 - 末地烛样式
 * 散发末地烛粒子效果
 * 玩家在附近时进行提示
 * 玩家接触时触发复活逻辑
 */
public class CluePointBlock extends Block {
    private static final int NOTIFICATION_RANGE = 15; // 提示范围
    private static final Random random = new Random();
    private int particleTick = 0;

    public CluePointBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, world, pos, oldState, movedByPiston);
        if (!world.isClientSide) {
            world.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, net.minecraft.util.RandomSource rand) {
        // 末地烛粒子效果 - 持续散发紫色粒子
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.2;
        double z = pos.getZ() + 0.5;
        
        // 末地烛特有的紫色粒子
        world.addParticle(ParticleTypes.DRAGON_BREATH, 
                x + (rand.nextDouble() - 0.5) * 0.3, 
                y + rand.nextDouble() * 0.5, 
                z + (rand.nextDouble() - 0.5) * 0.3, 
                0, 0.05, 0);
        
        world.addParticle(ParticleTypes.ENCHANT, 
                x + (rand.nextDouble() - 0.5) * 0.2, 
                y + rand.nextDouble() * 0.3, 
                z + (rand.nextDouble() - 0.5) * 0.2, 
                0, 0.02, 0);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, net.minecraft.util.RandomSource rand) {
        super.tick(state, world, pos, rand);
        
        particleTick++;
        
        // 每5tick产生额外粒子效果
        if (particleTick % 5 == 0) {
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    2, 0.2, 0.3, 0.2, 0.02);
        }
        
        // 检测范围内的里世界玩家并提示
        if (particleTick % 40 == 0) { // 每2秒检测一次
            notifyNearbyPlayers(world, pos);
        }
        
        // 保持tick更新
        world.scheduleTick(pos, this, 1);
    }

    /**
     * 提示附近的里世界玩家
     */
    private void notifyNearbyPlayers(ServerLevel world, BlockPos pos) {
        for (ServerPlayer player : world.players()) {
            DNFUnderworldComponent underworld = DNFUnderworldComponent.KEY.get(player);
            if (!underworld.isInUnderworld()) continue;
            
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance <= NOTIFICATION_RANGE * NOTIFICATION_RANGE) {
                // 在玩家位置显示提示粒子
                world.sendParticles(ParticleTypes.GLOW,
                        player.getX(), player.getY() + 1.5, player.getZ(),
                        5, 0.3, 0.3, 0.3, 0.05);
                
                // 如果玩家距离很近(5格内),显示消息提示
                if (distance <= 25) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.dnf.underworld.clue_nearby")
                                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
                }
            }
        }
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide) return;
        if (!(entity instanceof ServerPlayer player)) return;
        
        DNFUnderworldComponent underworld = DNFUnderworldComponent.KEY.get(player);
        if (!underworld.isInUnderworld()) return;
        
        // 玩家复活
        revivePlayer(player, pos);
    }

    /**
     * 复活玩家
     */
    private void revivePlayer(ServerPlayer player, BlockPos cluePoint) {
        DNFUnderworldComponent underworld = DNFUnderworldComponent.KEY.get(player);
        
        // 清除里世界状态
        underworld.revivePlayer();
        
        // 设置玩家为冒险模式
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        
        // 传送到线索点
        player.teleportTo(cluePoint.getX() + 0.5, cluePoint.getY() + 1, cluePoint.getZ() + 0.5);
        
        // 给予生命恢复效果
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        
        // 满血复活
        player.setHealth(player.getMaxHealth());
        
        // 播放音效
        player.level().playSound(null, player.blockPosition(), 
                net.minecraft.sounds.SoundEvents.TOTEM_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        
        // 显示消息
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dnf.underworld.revived")
                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        
        // 销毁被触碰的线索点方块，而不是复活后玩家脚下的方块。
        player.level().setBlock(cluePoint, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        
        // 移除世界组件中的线索点记录
        DNFWorldComponent.KEY.get(player.level()).removeCluePoint(cluePoint);
    }

    /**
     * 获取线索点方块实例(用于放置)
     */
    public static CluePointBlock cluePointBlock() {
        return (CluePointBlock) DNFBlocks.CLUE_POINT;
    }
}
