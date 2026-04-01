package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.item.KnifeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;

public class NinjaKnifeItem extends KnifeItem {

    public NinjaKnifeItem(Properties properties) {
        super(properties);
    }

    /**
     * 右键使用，立即触发（无需蓄力）
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(player instanceof ServerPlayer attacker)) {
            return InteractionResultHolder.pass(stack);
        }

        // 直接执行攻击（参考父类的 releaseUsing 逻辑）
        // 获取攻击目标（父类的方法）
        var collision = KnifeItem.getKnifeTarget(attacker);
        if (collision instanceof net.minecraft.world.phys.EntityHitResult entityHitResult) {
            var target = entityHitResult.getEntity();
            if (target instanceof Player victim) {
                // 执行击杀
                GameUtils.killPlayer(victim, true, attacker, Noellesroles.id("ninja_knife_kill"));
                // 苦无消失
                stack.shrink(1);
                return InteractionResultHolder.consume(stack);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * 禁用蓄力（返回0，无需蓄力）
     */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 0;
    }

    /**
     * 禁用使用动画
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /**
     * 禁用左键攻击
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    @Override
    public String getItemSkinType() {
        return "";
    }
}