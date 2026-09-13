package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

public class FuTaiRole extends NormalRole {
    public FuTaiRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static boolean useOracleSkill(ServerPlayer user, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return false;
        }
        int cost = 200;
        int balance = MoneyUtils.getBalance(user);
        if (balance < cost) {
            user.displayClientMessage(Component.translatable(
                    "message.noellesroles.fu_tai.not_enough_money", cost), true);
            return false;
        }
        if (cost > 0) {
            MoneyUtils.addToBalance(user, -cost);
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(user.level());
        int killers = 0;
        int neutrals = 0;
        for (ServerPlayer target : user.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            var role = game.getRole(target);
            if (role == null) {
                continue;
            }
            if (role.isNeutrals()) {
                neutrals++;
            } else if (game.isKillerTeamRole(role)) {
                killers++;
            }
        }
        user.displayClientMessage(Component.translatable(
                "message.noellesroles.fu_tai.oracle_result", killers, neutrals), true);
        return true;
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.FU_TAI,
                RoleSkill.skill(SRE.id("fu_tai_oracle"), "skill.noellesroles.fu_tai.oracle", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return false;
                    return FuTaiRole.useOracleSkill(player, context);
                }).cooldownSeconds(120).showOnHud(true).build());
    }

    @Override
    public List<ShopEntry> getShopEntries() {

        var FU_TAI_SHOP = new ArrayList<ShopEntry>();
        FU_TAI_SHOP.add(new ShopEntry(TMMItems.DEFENSE_VIAL.getDefaultInstance(), 500,
                ShopEntry.Type.POISON));
        return FU_TAI_SHOP;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        player.removeEffect(MobEffects.BLINDNESS);
    }
}
