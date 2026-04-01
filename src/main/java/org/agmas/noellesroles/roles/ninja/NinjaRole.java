package org.agmas.noellesroles.roles.ninja;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

public class NinjaRole extends NormalRole {

    private static final Item KNIFE = BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "knife")
    );
    private static final Item LOCKPICK = BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "lockpick")
    );

    public NinjaRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                     MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(new ShopEntry(new ItemStack(KNIFE), 130, ShopEntry.Type.WEAPON));
        entries.add(new ShopEntry(new ItemStack(ModItems.NINJA_KNIFE), 200, ShopEntry.Type.WEAPON));
        entries.add(new ShopEntry(new ItemStack(ModItems.NINJA_SHURIKEN), 325, ShopEntry.Type.WEAPON));
        return entries;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = super.getDefaultItems();
        items.add(new ItemStack(LOCKPICK));
        return items;
    }

    // 夜行被动已移到 NinjaPlayerComponent
    @Override
    public void clientTick(Player player) {
        super.clientTick(player);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        super.serverTick(player);
    }
}