package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

public class ToyHammerItem extends Item implements SREItemProperties.LeftClickHurtable {
    public ToyHammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        if (SREGameWorldComponent.KEY.get(attacker.level()).isRole(attacker, ModRoles.JUKA)
                && !attacker.getCooldowns().isOnCooldown(this)) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 2, 1,
                    false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 20 * 2, 0,
                    false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 20 * 2, 0,
                    false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, 20 * 2, 0,
                    false, false, true));
            attacker.getCooldowns().addCooldown(this, 20 * 60);
        }
        return false;
    }
}
