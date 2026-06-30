package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import io.wifi.starrailexpress.api.TouhouRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class THTenshiRole extends TouhouRole {

    public THTenshiRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        addFlag("th_misc");
        setVigilanteTeam(true);
    }
    
    /**
     * 在HarpyModLoader中使用
     */
    public List<ItemStack> getDefaultItems() {
        return new ArrayList<>();
    }
    
}
