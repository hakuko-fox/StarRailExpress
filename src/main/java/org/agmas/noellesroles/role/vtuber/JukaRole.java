package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

public class JukaRole extends CustomWinnerRole {
    public JukaRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public WinStatus checkWin(ServerPlayer player,
            WinStatus winStatus) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return WinStatus.NOT_MODIFY;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        boolean civilianOrPoliceAlive = player.serverLevel().players().stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .map(game::getRole)
                .anyMatch(role -> role != null && (role.isInnocent() || role.isVigilanteTeam()));
        return civilianOrPoliceAlive
                ? WinStatus.NOT_MODIFY
                : WinStatus.CUSTOM;
    }

    @Override
    public boolean onUseGun(Player player) { return false; }

    @Override
    public boolean onUseKnife(Player player) { return false; }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return VtuberRoleSupport.isWeapon(item.getItem()) ? TrueFalseResult.FALSE : TrueFalseResult.PASS;
    }

    public static void giveInitialItems(Player player) {
        RoleUtils.insertStackInFreeSlot(player, ModItems.TOY_HAMMER.getDefaultInstance());

    }

    public static boolean useToyHammer(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        if (GameUtils.isPlayerAliveAndSurvival(attacker)
                    && GameUtils.isPlayerAliveAndSurvival(target)
                && attacker.level() == target.level()
                && SREGameWorldComponent.KEY.get(attacker.level()).isRole(attacker, ModRoles.JUKA)
                && !attacker.getCooldowns().isOnCooldown(mainhandItem.getItem())) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 2, 1,
                    false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 20 * 2, 0,
                    false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 20 * 2, 0,
                    false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, 20 * 2, 0,
                    false, false, true));
            attacker.getCooldowns().addCooldown(mainhandItem.getItem(), 20 * 60);
        }
        return false;
    }
}
