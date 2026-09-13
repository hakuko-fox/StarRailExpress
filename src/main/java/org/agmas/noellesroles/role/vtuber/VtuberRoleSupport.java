package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.ShopEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;

/** Shared item classification; individual role policy stays in its role class. */
public final class VtuberRoleSupport {
    private VtuberRoleSupport() {}

    public static boolean isWeapon(Item item) {
        return item.builtInRegistryHolder().is(TMMItemTags.GUNS)
                || item == TMMItems.KNIFE || item == TMMItems.BAT
                || item == TMMItems.GRENADE || item == TMMItems.STICKY_GRENADE
                || item == TMMItems.TIMED_GRENADE || item == TMMItems.FIRECRACKER
                || item == TMMItems.NUNCHUCK;
    }

    public static java.util.List<ShopEntry> killerShopWithCrowbar() {
        var entries = new ArrayList<>(ShopContent.getDefaultKnifeEntries());
        boolean hasCrowbar = entries.stream().anyMatch(entry -> entry.stack().is(TMMItems.CROWBAR));
        if (!hasCrowbar) {
            entries.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                    SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL));
        }
        return entries;
    }

    public static boolean deduct(ServerPlayer player, int cost) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        int balance = MoneyUtils.getBalance(player);
        if (balance < cost) {
            player.displayClientMessage(Component.translatable("message.noellesroles.vtuber.not_enough_coins", cost), true);
            return false;
        }
        MoneyUtils.addToBalance(player, -cost);
        return true;
    }

    public static void leaveAnimalForm(ServerPlayer player) {
        var data = RoleData.getNullable(VtuberRoleData.class, player);
        if (data == null) return;
        int disguise = data.getDisguise();
        if (disguise != VtuberRoleData.NONE) {
            var skillId = SRE.id(disguise == VtuberRoleData.BLOOD_FOX
                    ? "blood_fox_transform" : "yozora_cat_sixth_sense");
            SREAbilityPlayerComponent.KEY.get(player)
                    .setSkillCooldown(skillId, 20 * 10);
        }
        RoleData.getNullable(VtuberRoleData.class, player).setDisguise(VtuberRoleData.NONE);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0,
                false, false, true));
    }

    public static void tickUncontrolledAlliance(ServerPlayer player, SREGameWorldComponent game, long now) {
        boolean member = game.isRole(player, ModRoles.XIAOYE)
                || game.isRole(player, ModRoles.XIANMIAO)
                || game.isRole(player, ModRoles.YUZU_FENGLING);
        if (!member || now < RoleData.getNullable(VtuberRoleData.class, player).nextAllianceRoll) {
            return;
        }
        RoleData.getNullable(VtuberRoleData.class, player).nextAllianceRoll = now + 20L * 20L;
        if (player.getRandom().nextBoolean()) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 10, 4, false, false, true));
            RoleData.getNullable(VtuberRoleData.class, player).weaponBlockedUntil = now + 20L * 20L;
            player.displayClientMessage(Component.translatable("message.noellesroles.uncontrolled_alliance.triggered"), true);
        }
    }

    public static void tickPasserby(ServerPlayer player, SREGameWorldComponent game) {
        if (!game.isRole(player, ModRoles.AMI) && !game.isRole(player, ModRoles.YOZORA)) {
            return;
        }
        boolean nearby = player.serverLevel().players().stream()
                .anyMatch(other -> other != player && GameUtils.isPlayerAliveAndSurvival(other)
                && other.distanceToSqr(player) <= 9.0D);
        if (!nearby) {
            RoleData.getNullable(VtuberRoleData.class, player).passerbyTicks = 0;
            return;
        }
        int ticks = ++RoleData.getNullable(VtuberRoleData.class, player).passerbyTicks;
        if (ticks >= 20 * 10 && ticks % 20 == 0) {
            SREPlayerMoodComponent.KEY.get(player).addMood(-0.01F);
        }
    }

    public static boolean canBuy(Player player, ShopEntry entry) {
        var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        if (role instanceof HoshizoraRole) return HoshizoraRole.canBuy(entry);
        if (role instanceof HalicRole || role instanceof SeptemberOneRole || role instanceof ShenwuBingfengRole
                || role instanceof MaolunRole || role instanceof JukaRole || role instanceof KanaRole) {
            return entry.type() != ShopEntry.Type.WEAPON;
        }
        return true;
    }
}
