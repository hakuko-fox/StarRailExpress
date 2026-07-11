package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.config.NoellesRolesConfig;

/**
 * Dream 的范围关灯（商店 150 金币，一次性）。
 *
 * <p>右键使用：以自己为中心、半径 {@code dreamBlackoutRadius}（默认 30 格）内的灯
 * 全部熄灭（复用 {@link SREWorldBlackoutComponent} 的区域关灯），并触发标准关灯
 * 音效/致盲流程。使用后物品消耗。
 */
public class DreamBlackoutItem extends Item {
    public DreamBlackoutItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer sp) || sp.isSpectator()) {
            return InteractionResultHolder.fail(stack);
        }
        var config = NoellesRolesConfig.HANDLER.instance();
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(level);
        blackout.triggerBlackout(sp.blockPosition(), config.dreamBlackoutRadius, true,
                SREWorldBlackoutComponent.getMaxDuration(level));
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }
}
