package org.agmas.noellesroles.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;

public class RepairExitGateBlock extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public RepairExitGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return useGate(state, level, pos, player) ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        useGate(state, level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private boolean useGate(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        boolean survivor = RepairRoleDefinition.byId(ModComponents.REPAIR_ROLES.get(serverPlayer).activeRole)
                .map(role -> role.faction == RepairRoleDefinition.Faction.SURVIVOR)
                .orElse(false);
        if (!survivor || !RepairModeState.canUseSurvivorUtility(serverPlayer)) {
            return true;
        }
        if (!RepairModeState.areExitGatesPowered(serverLevel)) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.gate_locked",
                    RepairModeState.getCompletedStationCount(serverLevel), RepairModeState.REQUIRED_REPAIRED_STATIONS),
                    true);
            return true;
        }
        level.setBlockAndUpdate(pos, state.setValue(OPEN, true));
        RepairModeState.clearRestraints(serverPlayer);
        serverPlayer.addTag(RepairModeState.ESCAPED_TAG);
        RepairModeState.awardCoins(serverPlayer, 125, "repair_coin_source.station");
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.escaped"), false);
        serverPlayer.setGameMode(GameType.SPECTATOR);
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }
}
