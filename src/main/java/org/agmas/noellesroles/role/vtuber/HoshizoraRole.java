package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.SniperRifleItem;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.SniperShootPayload;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

public class HoshizoraRole extends NormalRole {
    public HoshizoraRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
    }

    @Override
    public boolean onUseGun(net.minecraft.world.entity.player.Player player) {
        return HoshizoraRole.canHoshizoraUseWeapon(player);
    }

    @Override
    public boolean onUseKnife(net.minecraft.world.entity.player.Player player) {
        return HoshizoraRole.canHoshizoraUseWeapon(player);
    }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return item.getItem() != TMMItems.SNIPER_RIFLE && VtuberRoleSupport.isWeapon(item.getItem())
                ? TrueFalseResult.FALSE
                : TrueFalseResult.PASS;
    }

    public static void giveInitialItems(Player player) {
        SniperShootPayload.resetZoraState(player.getUUID());
        var sniper = TMMItems.SNIPER_RIFLE.getDefaultInstance();
        SniperRifleItem.setAmmoCount(sniper,
                SniperRifleItem.MAX_AMMO);
        SniperRifleItem.setScopeAttached(sniper, true);
        RoleUtils.insertStackInFreeSlot(player, sniper);

    }

    @Override
    public List<ShopEntry> getShopEntries() {

        var HOSHIZORA_SHOP = new ArrayList<ShopEntry>();
        HOSHIZORA_SHOP.add(new ShopEntry(new ItemStack(TMMItems.MAGNUM_BULLET, 5), 50,
                ShopEntry.Type.TOOL));
        HOSHIZORA_SHOP.add(maxOneScopeEntry(25));
        HOSHIZORA_SHOP.add(maxOneItemEntry(TMMItems.SNIPER_RIFLE.getDefaultInstance(), 400,
                ShopEntry.Type.WEAPON));
        HOSHIZORA_SHOP.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL));
        return HOSHIZORA_SHOP;
    }

    private static ShopEntry maxOneItemEntry(ItemStack stack, int price, ShopEntry.Type type) {
        return new ShopEntry(stack, price, type) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                return super.canBuy(player) && !SREItemUtils.hasItem(player, stack.getItem());
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                return !SREItemUtils.hasItem(player, stack.getItem()) && super.onBuy(player);
            }
        };
    }

    private static ShopEntry maxOneScopeEntry(int price) {
        return new ShopEntry(TMMItems.SCOPE.getDefaultInstance(), price, ShopEntry.Type.TOOL) {
            private boolean hasScopeOrAttached(Player player) {
                if (SREItemUtils.hasItem(player, TMMItems.SCOPE)) {
                    return true;
                }
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack inventoryStack = player.getInventory().getItem(i);
                    if (inventoryStack.is(TMMItems.SNIPER_RIFLE)
                                && SniperRifleItem.hasScopeAttached(inventoryStack)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean canBuy(@NotNull Player player) {
                return super.canBuy(player) && !hasScopeOrAttached(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                return !hasScopeOrAttached(player) && super.onBuy(player);
            }
        };
    }

    public static boolean canHoshizoraUseWeapon(Player player) {
        VtuberRoleData data = player == null ? null : RoleData.getNullable(VtuberRoleData.class, player);
        return data != null && player.level().getGameTime() >= data.hoshizoraWeaponBlockedUntil;
    }

    public static void tickHoshizora(ServerPlayer player, SREGameWorldComponent game, long now) {
        if (!game.isRole(player, ModRoles.HOSHIZORA)) {
            return;
        }
        boolean nearby = player.serverLevel().players().stream()
                .anyMatch(other -> other != player && GameUtils.isPlayerAliveAndSurvival(other)
                && other.distanceToSqr(player) <= 7.0D * 7.0D);
        if (nearby) {
            RoleData.getNullable(VtuberRoleData.class, player).hoshizoraWeaponBlockedUntil = now + 20L;
        }
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        tickHoshizora(player, game, now);
    }

    public static boolean canBuy(ShopEntry entry) {
        return entry.type() != ShopEntry.Type.WEAPON || entry.stack().is(TMMItems.SNIPER_RIFLE);
    }

}
