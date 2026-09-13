package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.agmas.noellesroles.content.item.AlinDoorToolItem.Mode;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

public class AlinRole extends NormalRole {
    public AlinRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {

        var ALIN_SHOP = new ArrayList<ShopEntry>();
        ALIN_SHOP.add(new ShopEntry(ModItems.ALIN_WRENCH.getDefaultInstance(), 130,
                ShopEntry.Type.TOOL));
        ALIN_SHOP.add(new ShopEntry(ModItems.ALIN_SCREWDRIVER.getDefaultInstance(), 130,
                ShopEntry.Type.TOOL));
        return ALIN_SHOP;
    }

    // Shared world state: a door can be handled once per mode each round.
    private static final Set<String> USED_DOORS = new HashSet<>();

    public static void registerEvents() {
        OnGameTrueStarted.EVENT.register(level -> USED_DOORS.clear());
        OnGameEnd.EVENT.register((level, game) -> USED_DOORS.clear());
    }

    public static InteractionResult useDoorTool(UseOnContext context, Mode mode) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !GameUtils.isPlayerAliveAndSurvival(player) || !SREGameWorldComponent.KEY.get(level).isRole(player, ModRoles.ALIN)) {
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
        if (level.isClientSide) return InteractionResult.SUCCESS;
        String doorKey = level.dimension().location() + ":" + lowerPos.asLong() + ":" + mode.name();
        if (USED_DOORS.contains(doorKey)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.noellesroles.alin.door_already_handled"), true);
            return InteractionResult.FAIL;
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
            USED_DOORS.add(doorKey);
            door.sync();
            context.getItemInHand().shrink(1);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    mode == Mode.REPAIR
                    ? "message.noellesroles.alin.door_repaired"
                    : "message.noellesroles.alin.door_broken"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
