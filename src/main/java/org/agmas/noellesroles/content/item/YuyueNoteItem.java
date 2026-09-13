package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.NoteItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.role.vtuber.YuyueRole;

/** YuYue's ordinary sticky note, which may also be attached to a player's back. */
public class YuyueNoteItem extends NoteItem {
    public YuyueNoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
    InteractionHand hand) {
if (player.isShiftKeyDown()) return super.interactLivingEntity(stack, player, target, hand);
return YuyueRole.attachNote(stack, player, target, this::createNoteEntity);
    }
}
