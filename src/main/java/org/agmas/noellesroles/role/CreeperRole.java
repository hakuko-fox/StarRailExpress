package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.CreeperPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;

import java.util.List;

/**
 * 苦力怕角色类
 */
public class CreeperRole extends NormalRole {

    public CreeperRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(
            new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), 130, ShopEntry.Type.WEAPON),
            new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 130, ShopEntry.Type.TOOL)
        );
    }

    @Override
    public void onAbilityUse(Player player) {
        CreeperPlayerComponent component = ModComponents.CREEPER.get(player);
        component.ignite();
    }
}