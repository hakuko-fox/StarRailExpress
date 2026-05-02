package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.content.item.NoteItem;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.agmas.noellesroles.packet.BroadcasterC2SPacket;

import java.util.List;

public class DNFPaperScrapItem extends NoteItem {
    private static final String TAG_WRITTEN = "dnf_written";
    private static final String TAG_LINE_PREFIX = "line";

    public DNFPaperScrapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return super.use(world, player, hand);
        }
        if (world.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (hasSavedText(stack)) {
            showSavedText(player, stack);
            return InteractionResultHolder.success(stack);
        }
        SREPlayerNoteComponent note = SREPlayerNoteComponent.KEY.get(player);
        if (!note.written) {
            player.displayClientMessage(Component.translatable("message.note.write_sth")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        saveText(stack, note.text);
        player.displayClientMessage(Component.translatable("message.dnf.paper.saved")
                .withStyle(ChatFormatting.GREEN), true);
        showSavedText(player, stack);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (!context.getLevel().isClientSide && !DNF.isNight(player)) {
                player.displayClientMessage(Component.translatable("message.dnf.paper.night_only")
                        .withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.FAIL;
            }
            return super.useOn(context);
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!DNF.isNight(player) && !hasSavedText(context.getItemInHand())) {
            player.displayClientMessage(Component.translatable("message.dnf.paper.night_only")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        return use(context.getLevel(), player, context.getHand()).getResult();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.starrailexpress.dnf_paper_scrap.tooltip")
                .withStyle(ChatFormatting.GRAY));
        if (hasSavedText(stack)) {
            tooltip.add(Component.translatable("message.dnf.paper.saved_tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static boolean hasSavedText(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(TAG_WRITTEN);
    }

    private static void saveText(ItemStack stack, String[] lines) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(TAG_WRITTEN, true);
        for (int i = 0; i < 4; i++) {
            tag.putString(TAG_LINE_PREFIX + i, i < lines.length && lines[i] != null ? lines[i] : "");
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void showSavedText(Player player, ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
//        player.displayClientMessage(Component.translatable("message.dnf.paper.header")
//                .withStyle(ChatFormatting.GRAY), false);
        if (player instanceof ServerPlayer serverPlayer) {
            for (int i = 0; i < 4; i++) {
                String line = tag.getString(TAG_LINE_PREFIX + i);
                if (!line.isBlank()) {
                    ServerPlayNetworking.send(serverPlayer, new BroadcastMessageS2CPacket( Component.literal(line).withStyle(ChatFormatting.WHITE),true));
                }
            }
        }
    }
}
