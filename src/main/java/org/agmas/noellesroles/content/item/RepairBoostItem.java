package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;

import java.util.List;

public class RepairBoostItem extends Item {
    private final int boost;
    private final String tooltipKey;

    public RepairBoostItem(int boost, String tooltipKey, Properties properties) {
        super(properties);
        this.boost = boost;
        this.tooltipKey = tooltipKey;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        if (!(context.getLevel().getBlockEntity(pos) instanceof RepairStationBlockEntity station)) {
            return InteractionResult.PASS;
        }
        if (station.addProgress(boost) && context.getPlayer() instanceof ServerPlayer player) {
            RepairModeState.awardCoins(player, 12, "repair_coin_source.boost");
            SREPlayerShopComponent.KEY.get(player).addToBalance(12);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.boosted", boost,
                    station.getProgress()), true);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.coin_reward", 12)
                    .withStyle(ChatFormatting.GOLD), true);
            RepairModeState.addNeutralTaskProgress(player, "collector", 1, 3);
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
