package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class DNFWaterItem extends Item {
    public DNFWaterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer && DNF.isDayNightFightMode(world)) {
            if (!DNFPlayerComponent.KEY.get(serverPlayer).markDrankWater(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            String poisoner = stack.getOrDefault(SREDataComponentTypes.POISONER, null);
            if (poisoner != null) {
                SREPlayerPoisonComponent.KEY.get(serverPlayer).setPoisonTicks(
                        world.getRandom().nextIntBetweenInclusive(SREPlayerPoisonComponent.clampTime.getA(),
                                SREPlayerPoisonComponent.clampTime.getB()),
                        UUID.fromString(poisoner));
            }
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
            if (!serverPlayer.isCreative()) {
                stack.shrink(1);
                DNFItems.giveOrDrop(serverPlayer, new ItemStack(Items.GLASS_BOTTLE));
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
