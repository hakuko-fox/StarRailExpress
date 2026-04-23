package org.agmas.noellesroles.content.item;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import io.wifi.starrailexpress.game.GameUtils;

import java.util.List;

public class ShortShotgunItem extends Item {
    public ShortShotgunItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) {
            // 客户端无额外处理，音效/击杀由服务端执行
        } else {
            world.playSound(null, user.blockPosition(), NRSounds.SHOTGUN_FIRE, SoundSource.PLAYERS, 1.0F, 1.0F);

            double radius = 2.0D;
            AABB box = new AABB(user.getX() - radius, user.getY() - radius, user.getZ() - radius,
                    user.getX() + radius, user.getY() + radius, user.getZ() + radius);
            List<Player> nearby = world.getEntitiesOfClass(Player.class, box, p -> p != user && GameUtils.isPlayerAliveAndSurvival(p));

            Vec3 look = user.getLookAngle();
            double cosThreshold = Math.cos(Math.toRadians(35.0)); // 70度扇形
            for (Player target : nearby) {
                Vec3 dir = new Vec3(target.getX() - user.getX(), 0, target.getZ() - user.getZ());
                double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                if (len == 0) continue;
                Vec3 ndir = dir.scale(1.0 / len);
                Vec3 l2 = new Vec3(look.x, 0, look.z);
                double llen = Math.sqrt(l2.x * l2.x + l2.z * l2.z);
                if (llen == 0) continue;
                Vec3 nlook = l2.scale(1.0 / llen);
                double dot = nlook.x * ndir.x + nlook.z * ndir.z;
                if (dot >= cosThreshold) {
                    io.wifi.starrailexpress.game.GameUtils.killPlayer(target, true, user, Noellesroles.id("short_shotgun"));
                }
            }

            if (!user.isCreative()) {
                stack.hurtAndBreak(1, user, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                user.getCooldowns().addCooldown(ModItems.SHORT_SHOTGUN, 30 * 20);
            }
        }

        return InteractionResultHolder.consume(stack);
    }
}
