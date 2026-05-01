package io.wifi.events.day_night_fight.entity;

import io.wifi.events.day_night_fight.cca.DNFUnderworldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 里世界怪物 - 布袋型狂魔
 * 透明渲染,通过散发粒子展示位置
 * 追逐在里世界中的玩家,没有透视能力
 * 被攻击到的玩家会减少30秒复活时间
 */
public class UnderworldMonsterEntity extends Monster {
    private int particleTick = 0;
    private int searchTick = 0;
    private Player targetPlayer = null;
    private static final int DETECTION_RANGE = 20; // 检测范围(无透视)

    public UnderworldMonsterEntity(EntityType<? extends Monster> entityType, Level world) {
        super(entityType, world);
    }

    public static AttributeSupplier createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 200.0).add(Attributes.MOVEMENT_SPEED, 1).add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 64.0).add(Attributes.SCALE, 1.25).build();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
    }

    @Override
    public void tick() {
        super.tick();
        
        if (level().isClientSide) return;
        
        // 每tick更新里世界倒计时
        updateUnderworldPlayers();
        
        // 粒子效果 - 持续散发粒子展示位置
        particleTick++;
        if (particleTick % 5 == 0) {
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                        getX(), getY() + 1, getZ(),
                        5, 0.5, 0.7, 0.5, 0.1);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,
                        getX(), getY() + random.nextFloat() * 2, getZ(),
                        7, 0.4, 0.4, 0.4, 0.1);
            }
        }
        
        // 寻找目标 - 没有透视,只能检测范围内的玩家
        searchTick++;
        if (searchTick % 20 == 0) {
            findAndChasePlayer();
        }
        
        // 追逐目标
        if (targetPlayer != null) {
            if (!targetPlayer.isAlive() || !isInUnderworld(targetPlayer)) {
                targetPlayer = null;
                return;
            }
            
            double distance = distanceTo(targetPlayer);
            if (distance > DETECTION_RANGE) {
                targetPlayer = null;
            } else {
                // 向玩家移动
                Vec3 direction = targetPlayer.position().subtract(position()).normalize();
                double speed = 0.5;
                setDeltaMovement(direction.x * speed, getDeltaMovement().y, direction.z * speed);
                
                // 攻击玩家
                if (distance <= 1.5) {
                    attackPlayer();
                }
            }
        }
    }

    /**
     * 更新里世界玩家的倒计时
     */
    private void updateUnderworldPlayers() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        
        for (ServerPlayer player : serverLevel.players()) {


        }
    }

    /**
     * 寻找并追逐玩家
     */
    private void findAndChasePlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        
        Player closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (ServerPlayer player : serverLevel.players()) {
            if (!isInUnderworld(player)) continue;
            
            double distance = distanceTo(player);
            if (distance <= DETECTION_RANGE && distance < closestDistance) {
                closestPlayer = player;
                closestDistance = distance;
            }
        }
        
        targetPlayer = closestPlayer;
    }

    /**
     * 攻击玩家
     */
    private void attackPlayer() {
        if (targetPlayer == null || !(targetPlayer instanceof ServerPlayer)) return;
        
        ServerPlayer player = (ServerPlayer) targetPlayer;
        DNFUnderworldComponent underworld = DNFUnderworldComponent.KEY.get(player);
        
        if (underworld.isInUnderworld()) {
            // 减少30秒复活时间
            underworld.reduceTime();
            
            // 播放攻击音效
            level().playSound(null, blockPosition(), 
                    net.minecraft.sounds.SoundEvents.WARDEN_ATTACK_IMPACT,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
            
            // 给予玩家短暂的缓慢效果
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            
            // 显示消息
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dnf.underworld.time_reduced")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            
            // 击退效果
            Vec3 knockback = targetPlayer.position().subtract(position()).normalize().scale(0.5);
            targetPlayer.setDeltaMovement(knockback.x, 0.3, knockback.z);
        }
    }

    /**
     * 检查玩家是否在里世界
     */
    private boolean isInUnderworld(Player player) {
        if (!(player instanceof ServerPlayer)) return false;
        DNFUnderworldComponent underworld = DNFUnderworldComponent.KEY.get(player);
        return underworld.isInUnderworld();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    /**
     * 怪物不可见(透明)
     */
    @Override
    public boolean isInvisible() {
        return true;
    }


}
