package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

public class YuzuFenglingRole extends NormalRole {
    public YuzuFenglingRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
    }

    @Override
    public boolean onUseGun(Player player) {
        return !identifier().equals(ModRoles.KANA_ID)
                && !VtuberRoleRuntime.isWeaponBlocked(player);
    }

    @Override
    public boolean onUseKnife(Player player) {
        return !VtuberRoleRuntime.isWeaponBlocked(player)
                && KanaRole.canUseKanaKnife(player);
    }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return identifier().equals(ModRoles.KANA_ID) && VtuberRoleSupport.isWeapon(item.getItem())
                ? TrueFalseResult.FALSE : TrueFalseResult.PASS;
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.YUZU_FENGLING,
                RoleSkill.skill(SRE.id("yuzu_fengling_agility"), "skill.noellesroles.yuzu_fengling.agility", context ->
                YuzuFenglingRole.useYuzuAgility(context.player()))
                .cooldownSeconds(90).showOnHud(true).build());
    }

    public static void giveInitialItems(Player player) {
        RoleUtils.insertStackInFreeSlot(player, TMMItems.KNIFE.getDefaultInstance());

    }

    @Override
    public java.util.List<ShopEntry> getShopEntries() {
        return VtuberRoleSupport.killerShopWithCrowbar();
    }

    public static boolean useYuzuAgility(ServerPlayer player) {
        if (!VtuberRoleSupport.deduct(player, 100)) {
            return false;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 7, 1, false, false, true));
        return true;
    }

    public static void tickYuzuSleep(ServerPlayer player, SREGameWorldComponent game, long now) {
        if (!game.isRole(player, ModRoles.YUZU_FENGLING)) {
            return;
        }
        if (player.isSleeping()) {
            RoleData.getNullable(VtuberRoleData.class, player).yuzuSleepDeadline = now + 20L * 90L;
            RoleData.getNullable(VtuberRoleData.class, player).yuzuSleepWeaponBlocked = false;
        } else if (now >= RoleData.getNullable(VtuberRoleData.class, player).yuzuSleepDeadline) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 0, false, false, true));
            RoleData.getNullable(VtuberRoleData.class, player).yuzuSleepWeaponBlocked = true;
        }
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        tickYuzuSleep(player, game, now);
        VtuberRoleSupport.tickUncontrolledAlliance(player, game, now);
    }

}
