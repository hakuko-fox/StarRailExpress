package org.agmas.noellesroles.role_data.vtuber;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** 菈菲娜首次致命攻击的延后结算状态。 */
public final class LafinaRoleData extends SimpleRoleData {
    public long deathAt;
    public UUID killer;
    public ResourceLocation reason;
    public LafinaRoleData(RoleDataContext context) { super(context); }
    @Override public void clear() {
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(org.agmas.noellesroles.role.vtuber.LafinaRole.SPEED_ID);
    }
    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {}
    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {}
}
