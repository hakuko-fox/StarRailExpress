package org.agmas.noellesroles.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 死亡原因帮助类
 * 提供葬仪可用的死亡原因列表
 */
public class DeathReasonHelper {
    
    private static Boolean fakeRoleEnabled = null;
    
    /**
     * 检查是否启用FakeRole选择功能
     */
    public static boolean isFakeRoleEnabled() {
        if (fakeRoleEnabled == null) {
            // 检查配置文件或mods
            try {
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("kinswathe")) {
                    // 从KinsWathe配置中获取
                    fakeRoleEnabled = true;
                } else {
                    fakeRoleEnabled = false;
                }
            } catch (Exception e) {
                fakeRoleEnabled = false;
            }
        }
        return fakeRoleEnabled;
    }
    
    /**
     * 获取可用的死亡原因物品列表
     */
    public static ItemStack[] getAvailableDeathReasons() {
        return new ItemStack[] {
            // 基础死亡原因
            new ItemStack(Items.IRON_SWORD),        // knife_stab - 刀刺
            new ItemStack(Items.BOW),               // gun_shot - 枪击
            new ItemStack(Items.TNT),               // grenade - 手雷
            new ItemStack(Items.BLAZE_ROD),         // bat_hit - 棒击
            new ItemStack(Items.POTION),            // poison - 中毒
            new ItemStack(Items.OMINOUS_BOTTLE),   // voodoo - 巫术
            // 根据mods添加更多
        };
    }
    
    /**
     * 获取死亡原因的ID
     */
    public static String getDeathReasonId(ItemStack stack) {
        if (stack.is(Items.IRON_SWORD)) return "noellesroles:knife_stab";
        if (stack.is(Items.BOW)) return "noellesroles:gun_shot";
        if (stack.is(Items.TNT)) return "noellesroles:grenade";
        if (stack.is(Items.BLAZE_ROD)) return "noellesroles:bat_hit";
        if (stack.is(Items.POTION)) return "noellesroles:poison";
        if (stack.is(Items.OMINOUS_BOTTLE)) return "noellesroles:voodoo";
        if (stack.is(Items.LAVA_BUCKET)) return "noellesroles:burned";
        if (stack.is(Items.ANVIL)) return "noellesroles:crushed";
        if (stack.is(Items.CACTUS)) return "noellesroles:pricked";
        if (stack.is(Items.FIRE_CHARGE)) return "noellesroles:burned";
        return "noellesroles:generic";
    }
}
