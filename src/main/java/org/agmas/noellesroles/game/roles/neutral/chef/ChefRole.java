package org.agmas.noellesroles.game.roles.neutral.chef;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;

public class ChefRole extends NormalRole {
    public ChefRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }
}
