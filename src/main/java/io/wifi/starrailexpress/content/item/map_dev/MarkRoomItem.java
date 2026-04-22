package io.wifi.starrailexpress.content.item.map_dev;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class MarkRoomItem extends Item {

    public static Runnable openScreenCallback = null;
    
    public MarkRoomItem(Properties properties) {
        super(properties);
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        
        // 客户端：打开GUI
        if (world.isClientSide()) {
            if (openScreenCallback != null) {
                openScreenCallback.run();
            }
        }
        
        // 返回 success 但不消耗物品，等猜测完成后再消耗
        return InteractionResultHolder.success(stack);
    }
}
