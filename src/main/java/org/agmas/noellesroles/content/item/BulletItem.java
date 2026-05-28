package org.agmas.noellesroles.content.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class BulletItem extends Item {
    public BulletItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        
        // TODO: 实现子弹装填逻辑
        // if (user instanceof ServerPlayer serverPlayer && MafiaManager.tryLoadBullet(serverPlayer, stack)) {
        //     return InteractionResultHolder.consume(stack);
        // }
        return InteractionResultHolder.pass(stack);
    }
}
