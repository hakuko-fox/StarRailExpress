package org.agmas.noellesroles.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.PlayerVolumeComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RadioItem extends Item {
    // 全局临时组（简单实现）：加入即认为在同一语音组
    public static final Set<UUID> RADIO_GROUP = new HashSet<>();

    public RadioItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (world.isClientSide) {
            return InteractionResultHolder.success(user.getItemInHand(hand));
        }

        UUID id = user.getUUID();
        var comp = PlayerVolumeComponent.KEY.get(user);
        if (RADIO_GROUP.contains(id)) {
            RADIO_GROUP.remove(id);
            comp.clear();
            user.displayClientMessage(Component.translatable("message.noellesroles.radio.left"), true);
        } else {
            RADIO_GROUP.add(id);
            // 给出较长时间的 voice volume 状态，具体服务器端语音逻辑可依赖此组件
            comp.setVolume(20 * 60 * 10, 1.0f);
            user.displayClientMessage(Component.translatable("message.noellesroles.radio.joined"), true);
        }

        return InteractionResultHolder.consume(user.getItemInHand(hand));
    }
}
