package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.content.item.NoteItem;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;

/** YuYue's ordinary sticky note, which may also be attached to a player's back. */
public class YuyueNoteItem extends NoteItem {
    public YuyueNoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            return super.interactLivingEntity(stack, player, target, hand);
        }
        SREPlayerNoteComponent component = SREPlayerNoteComponent.KEY.get(player);
        if (!component.written) {
            player.displayClientMessage(Component.translatable("message.note.write_sth")
                    .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
            return InteractionResult.PASS;
        }
        Level level = player.level();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        var note = createNoteEntity(level);
        if (note == null) {
            return InteractionResult.PASS;
        }
        note.setAttached(ModRoles.ENTITY_NOTE_MAKER, target.getUUID().toString());
        note.setYRot(target.getYHeadRot());
        note.setPos(target.getX(), target.getY() + 1.0D, target.getZ());
        note.setDirection(Direction.EAST);
        note.setLines(component.text);
        level.addFreshEntity(note);
        player.displayClientMessage(Component.translatable("message.note.put_back", target.getName())
                .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
        if (!player.isCreative()) {
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
