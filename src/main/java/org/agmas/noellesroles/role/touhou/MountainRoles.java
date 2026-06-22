package org.agmas.noellesroles.role.touhou;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.innocent.postman.PostmanPlayerComponent;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class MountainRoles {
    public static final String NAMESPACE = "th_mount";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static final ResourceLocation AYA_ID = Noellesroles.id("aya");

    public static SRERole POSTMAN = TMMRoles.registerRole(new TouhouRole(
            AYA_ID, // 角色 ID
            new Color(70, 130, 180).getRGB(), // 钢蓝色 - 代表邮差制服
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ) {

        @Override
        public List<ItemStack> getDefaultItems() {
            ArrayList<ItemStack> itemStacks = new ArrayList<>();
            return itemStacks;
        }

    }.setComponentKey(PostmanPlayerComponent.KEY));
}
