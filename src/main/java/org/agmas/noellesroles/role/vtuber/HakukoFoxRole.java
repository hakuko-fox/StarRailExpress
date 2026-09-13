package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.HakukoFoxRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

public class HakukoFoxRole extends NormalRole {
    public HakukoFoxRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public boolean onUseGun(Player player) {
        return !HakukoFoxRoleData
                .isDisguised(player);
    }

    @Override
    public boolean onUseKnife(Player player) {
        return !HakukoFoxRoleData
                .isDisguised(player);
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.HAKUKO_FOX,
                RoleSkill.skill(SRE.id("hakukofox_transform"), "skill.noellesroles.hakukofox.transform", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator() || RoleData.getNullable(HakukoFoxRoleData.class, player) == null) return false;
                    return RoleData.getNullable(HakukoFoxRoleData.class, player)
                    .toggleBeastForm(player, context);
                }).cooldownSeconds(20).toggleable(true).manualCooldown().showOnHud(true).build(),
                RoleSkill.skill(SRE.id("hakukofox_freeze"), "skill.noellesroles.hakukofox.freeze", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator() || RoleData.getNullable(HakukoFoxRoleData.class, player) == null) return false;
                    return RoleData.getNullable(HakukoFoxRoleData.class, player)
                    .useFreezeSkill(player, context);
                }).shifted(true).cooldownSeconds(60).showOnHud(true).build());
    }

    @Override
    public void onInit(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
        super.onInit(server, player);
        RoleData.getOptional(HakukoFoxRoleData.class, player)
                .ifPresent(data -> data.startCultivation(player));
    }

    public static void giveInitialItems(Player player) {
        if (!SREItemUtils.hasItem(player, TMMItems.KNIFE)) {
            RoleUtils.insertOrDropItem(player, TMMItems.KNIFE.getDefaultInstance());
        }

    }

    @Override
    public java.util.List<ShopEntry> getShopEntries() {
        return VtuberRoleSupport.killerShopWithCrowbar();
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        if (!HakukoFoxRoleData.isCultivating(player)) player.removeEffect(MobEffects.BLINDNESS);
    }
}
