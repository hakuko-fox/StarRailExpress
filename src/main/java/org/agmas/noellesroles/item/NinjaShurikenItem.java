package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.item.KnifeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.Noellesroles;

public class NinjaShurikenItem extends KnifeItem {

    // 手里剑射程 20 格
    private static final float SHURIKEN_RANGE = 20.0F;

    public NinjaShurikenItem(Properties properties) {
        super(properties);
    }

    /**
     * 自定义射线检测（20格）
     */
    private static HitResult getShurikenTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player),
                SHURIKEN_RANGE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(player instanceof ServerPlayer shooter)) {
            return InteractionResultHolder.pass(stack);
        }

        Noellesroles.LOGGER.info("手里剑使用 - 玩家: {}", shooter.getName());

        // 射线检测目标
        HitResult collision = getShurikenTarget(shooter);
        Noellesroles.LOGGER.info("射线检测结果: {}", collision);

        if (collision instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            Noellesroles.LOGGER.info("命中目标: {}", target);

            if (target instanceof Player victim) {
                Noellesroles.LOGGER.info("击杀玩家: {}", victim.getName());
                GameUtils.killPlayer(victim, true, shooter, Noellesroles.id("shuriken_kill"));
                // 手里剑消失
                stack.shrink(1);
                return InteractionResultHolder.consume(stack);
            }
        } else {
            Noellesroles.LOGGER.info("未命中任何玩家");
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 0;  // 无需蓄力
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;  // 无动画
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;  // 禁用左键
    }

    @Override
    public String getItemSkinType() {
        return "";  // 禁用皮肤系统
    }
}