package org.agmas.noellesroles.content.item;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.ModItems;
import io.wifi.starrailexpress.game.GameUtils;

public class RiotShieldHandler {
    public static void register() {
        AttackEntityCallback.EVENT.register(RiotShieldHandler::onEntityDamaged);
    }

    public static InteractionResult onEntityDamaged(Player attacker, Level level, net.minecraft.world.InteractionHand hand, Entity entity,
            EntityHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.PASS;
        if (!(entity instanceof Player victim)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(victim)) return InteractionResult.PASS;

        // 如果受害者正在举盾且主手/副手持有我们的防暴盾，则阻挡并损坏盾
        if (!victim.isUsingItem()) return InteractionResult.PASS;

        ItemStack main = victim.getMainHandItem();
        ItemStack off = victim.getOffhandItem();
        ItemStack shieldStack = null;
        boolean mainHand = false;
        if (main != null && main.is(ModItems.RIOT_SHIELD)) {
            shieldStack = main;
            mainHand = true;
        } else if (off != null && off.is(ModItems.RIOT_SHIELD)) {
            shieldStack = off;
            mainHand = false;
        }

        if (shieldStack == null) return InteractionResult.PASS;

        // 检查是否来自玩家正面
        Vec3 look = victim.getLookAngle();
        Vec3 dir = attacker.position().subtract(victim.position());
        dir = new Vec3(dir.x, 0, dir.z);
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len == 0) return InteractionResult.PASS;
        dir = dir.scale(1.0 / len);
        Vec3 l2 = new Vec3(look.x, 0, look.z);
        double llen = Math.sqrt(l2.x * l2.x + l2.z * l2.z);
        if (llen == 0) return InteractionResult.PASS;
        l2 = l2.scale(1.0 / llen);
        double dot = l2.x * dir.x + l2.z * dir.z;
        if (dot < Math.cos(Math.toRadians(60.0))) {
            // 不是正面（允许一定夹角）
            return InteractionResult.PASS;
        }

        // 阻挡：播放音效并损坏盾
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!victim.isCreative()) {
            shieldStack.hurtAndBreak(1, victim, mainHand ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }

        return InteractionResult.SUCCESS;
    }
}
