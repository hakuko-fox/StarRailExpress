package org.agmas.noellesroles.game.roles.innocent.adventurer;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

public final class AdventurerRole extends NormalRole {
    public AdventurerRole(ResourceLocation id, int color, boolean innocent, boolean killer,
                          MoodType mood, int sprint, boolean hide) {
        super(id, color, innocent, killer, mood, sprint, hide);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        var items  = new ArrayList<ItemStack>();
        items.add(new ItemStack(ModItems.GROSELL_TRAVELOG));
        return items;
    }
}
