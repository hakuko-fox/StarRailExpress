package io.wifi.events.day_night_fight;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class DNFLabCardItem extends Item {

    public DNFLabCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.starrailexpress.dnf_lab_card.tooltip")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.starrailexpress.dnf_lab_card.tooltip.usage")
                .withStyle(ChatFormatting.GRAY));
    }
}
