package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DoorCustomOpenItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;

/** 一次性的修車麟阿麟專屬強制門工具。 */
public final class AlinDoorToolItem extends Item implements DoorCustomOpenItem {
    public enum Mode { REPAIR, BREAK }

    private final Mode mode;

    public AlinDoorToolItem(Mode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !SREGameWorldComponent.KEY.get(level).isRole(player, ModRoles.ALIN)) {
            return InteractionResult.FAIL;
        }

        BlockPos lowerPos = context.getClickedPos();
        BlockState state = level.getBlockState(lowerPos);
        if (state.getBlock() instanceof SmallDoorBlock
                && state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            lowerPos = lowerPos.below();
        }
        if (!(level.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity door)) {
            return InteractionResult.PASS;
        }

        if (mode == Mode.REPAIR) {
            if (!door.isBlasted()) {
                return InteractionResult.FAIL;
            }
            door.setBlasted(false);
            BlockState repairedState = level.getBlockState(lowerPos);
            if (repairedState.getBlock() instanceof SmallDoorBlock smallDoor
                    && !smallDoor.isOpen(repairedState)) {
                smallDoor.open(repairedState, level, door, lowerPos);
            }
        } else {
            if (door.isBlasted()) {
                return InteractionResult.FAIL;
            }
            door.blast();
        }

        if (!level.isClientSide) {
            door.setChanged();
            context.getItemInHand().shrink(1);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    mode == Mode.REPAIR
                            ? "message.noellesroles.alin.door_repaired"
                            : "message.noellesroles.alin.door_broken"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
