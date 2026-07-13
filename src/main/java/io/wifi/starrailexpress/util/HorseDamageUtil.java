package io.wifi.starrailexpress.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.entity.CanyuesaHorseEntity;
import org.agmas.noellesroles.content.entity.RainbowHorseEntity;
import org.agmas.noellesroles.content.entity.SuperPigHorseEntity;

/**
 * 通用马匹伤害工具类。
 * 子弹/箭矢/任何远程命中马的逻辑统一走这里，确保三种马都能被正确扣血。
 */
public final class HorseDamageUtil {

    private HorseDamageUtil() {
    }

    /**
     * 尝试对马造成伤害。
     *
     * @param hitEntity  被击中的实体
     * @param attacker   攻击者（用于距离校验）
     * @param damage     基础伤害值
     * @param maxRange   最大有效距离（格）
     * @return 是否成功造成了伤害
     */
    public static boolean tryDamageHorse(Entity hitEntity, Player attacker, float damage, double maxRange) {
        if (!(hitEntity instanceof RainbowHorseEntity
                || hitEntity instanceof CanyuesaHorseEntity
                || hitEntity instanceof SuperPigHorseEntity)) {
            return false;
        }
        if (attacker != null && hitEntity.distanceToSqr(attacker) > maxRange * maxRange) {
            return false;
        }
        ((Horse) hitEntity).hurt(hitEntity.damageSources().generic(), damage);
        return true;
    }
}
