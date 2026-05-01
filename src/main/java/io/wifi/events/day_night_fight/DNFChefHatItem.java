package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.List;

public class DNFChefHatItem extends Item {
    public DNFChefHatItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        
        if (!(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.pass(stack);
        }

        // 检查是否是DNF模式
        if (!DNF.isDayNightFightMode(world)) {
            player.displayClientMessage(Component.translatable("message.dnf.chef_hat.not_in_dnf")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        // 检查玩家是否是平民
        if (!SREGameWorldComponent.KEY.get(world).isRole(player, DNFRoles.CIVILIAN)) {
            player.displayClientMessage(Component.translatable("message.dnf.chef_hat.not_civilian")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        // 切换为厨师角色
        RoleUtils.changeRole(player, DNFRoles.CHEF);
        
        // 播放音效
        world.playSound(null, player.blockPosition(), 
                net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        
        // 显示切换成功消息
        player.displayClientMessage(Component.translatable("message.dnf.chef_hat.switched_to_chef")
                .withStyle(ChatFormatting.DARK_GREEN), true);



        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.starrailexpress.dnf_chef_hat.tooltip")
                .withStyle(ChatFormatting.GOLD));

    }
}
