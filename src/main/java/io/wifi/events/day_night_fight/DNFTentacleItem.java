package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.SREItemProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DNFTentacleItem extends Item implements SREItemProperties.LeftClickHurtable {
    public DNFTentacleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer player) || !SREGameWorldComponent.KEY.get( world).isRole(player, DNFRoles.DNF_ABYSS)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().expandTowards(look.scale(6)).inflate(1.2);
        Entity nearest = null;
        double dist = 999;
        for (Entity entity : world.getEntities(player, box, e -> e instanceof Player && e.isAlive())) {
            Vec3 delta = entity.position().subtract(player.position());
            double dot = delta.normalize().dot(look);
            if (dot < 0.55) continue;
            double d = delta.lengthSqr();
            if (d < dist) { dist = d; nearest = entity; }
        }
        if (nearest != null) {
            Vec3 pullTo = player.position().add(look.scale(1.2));
            
            // 在玩家和目标之间生成触手拉取的粒子效果
            if (world instanceof ServerLevel serverLevel) {
                // 从目标位置到玩家位置的粒子连线效果
                Vec3 targetPos = nearest.position();
                Vec3 startPos = targetPos;
                Vec3 endPos = pullTo;
                
                // 计算方向向量和距离
                Vec3 direction = endPos.subtract(startPos);
                double distance = direction.length();
                Vec3 normalizedDirection = direction.normalize();
                
                // 沿路径生成粒子轨迹（触手效果）
                int particleCount = (int)(distance * 5); // 每单位距离5个粒子
                for (int i = 0; i < particleCount; i++) {
                    double ratio = (double)i / particleCount;
                    Vec3 particlePos = startPos.add(direction.scale(ratio));
                    
                    // 生成主要的触手粒子（使用END_ROD模拟触手）
                    serverLevel.sendParticles(
                        ParticleTypes.END_ROD,
                        particlePos.x, particlePos.y + 1, particlePos.z,
                        1, 0.1, 0.1, 0.1, 0.02
                    );
                    
                    // 偶尔添加一些额外的效果粒子
                    if (i % 3 == 0) {
                        serverLevel.sendParticles(
                            ParticleTypes.SOUL_FIRE_FLAME,
                            particlePos.x, particlePos.y + 1, particlePos.z,
                            1, 0.2, 0.2, 0.2, 0.05
                        );
                    }
                }
                
                // 在目标位置生成抓取特效
                serverLevel.sendParticles(
                    ParticleTypes.EXPLOSION_EMITTER,
                    targetPos.x, targetPos.y + 1, targetPos.z,
                    1, 0, 0, 0, 0
                );
                
                // 在玩家位置生成接收特效
                serverLevel.sendParticles(
                    ParticleTypes.PORTAL,
                    pullTo.x, pullTo.y + 1, pullTo.z,
                    10, 0.5, 0.5, 0.5, 0.1
                );
            }
            
            nearest.teleportTo(pullTo.x, player.getY(), pullTo.z);
            nearest.setDeltaMovement(player.position().subtract(nearest.position()).normalize().scale(0.6));
            player.getCooldowns().addCooldown(this, 200);
            return InteractionResultHolder.success(stack);
        }
        player.getCooldowns().addCooldown(this, 15);
        player.displayClientMessage(Component.literal("前方没有可抓取目标").withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.fail(stack);
    }
}
