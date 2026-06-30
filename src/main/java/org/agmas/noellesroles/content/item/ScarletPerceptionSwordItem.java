package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties.LeftClickKillable;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SkinUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ScarletPerceptionSwordItem extends Item implements LeftClickKillable {

    public static final int COOLDOWN_TIME = 20 * 60;

    public ScarletPerceptionSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onTryHurt(Player attacker, Entity targetE, ItemStack mainhandItem) {
        if (!(targetE instanceof Player target))
            return InteractionResult.FAIL;
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return InteractionResult.FAIL;
        }
        if (attacker.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        if (attacker.getCooldowns().isOnCooldown(this)) {
            return;
        }
        if (!attacker.isCreative()) {
            attacker.getCooldowns().addCooldown(this, COOLDOWN_TIME);
        }
        GameUtils.killPlayer(target, true, attacker, SkinUtils.getItemTypeResourceLocation(this));
    }
}
