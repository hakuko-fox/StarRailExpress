package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties.DoorCustomOpenItem;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.agmas.noellesroles.role.vtuber.AlinRole;

/** One-use door tool; the role validates and applies the interaction. */
public final class AlinDoorToolItem extends Item implements DoorCustomOpenItem, AdventureUsable {
    public enum Mode { REPAIR, BREAK }
    private final Mode mode;

    public AlinDoorToolItem(Mode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return AlinRole.useDoorTool(context, mode);
    }
}
