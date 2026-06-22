package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;

public class CourierMailItem extends Item {
    public static BiConsumer<Player, InteractionHand> openSendScreen = null;
    public static BiConsumer<Player, InteractionHand> openReceiveScreen = null;

    public CourierMailItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CourierMailData.appendTooltip(stack, ctx, tooltip, flag);
        long cd = getCooldownEnd(stack);
        if (cd > 0) {
            long now = System.currentTimeMillis() / 50;
            long rem = Math.max(0, cd - now) / 20;
            tooltip.add(Component.translatable("item.noellesroles.courier_mail.cooldown", rem).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) {
            boolean isSend = CourierMailData.getSender(stack).isEmpty() && !CourierMailData.isReply(stack);
            if (isSend) {
                if (openSendScreen != null) openSendScreen.accept(user, hand);
            } else {
                if (openReceiveScreen != null) openReceiveScreen.accept(user, hand);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    public static long getCooldownEnd(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) return cd.copyTag().getLong("CooldownEnd");
        return 0;
    }

    public static void setCooldownEnd(ItemStack stack, long tick) {
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA) != null
                ? stack.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        tag.putLong("CooldownEnd", tick);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
