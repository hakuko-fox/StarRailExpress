package org.agmas.noellesroles.role_data.vtuber;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.agmas.noellesroles.role.vtuber.ShenwuBingfengRole;

/** Current-role server state; no private progress is broadcast. */
public final class ShenwuBingfengRoleData extends SimpleRoleData {
    public final java.util.Set<ShenwuBingfengRole.ShenwuDamageGroup> damageGroups = java.util.EnumSet.noneOf(ShenwuBingfengRole.ShenwuDamageGroup.class);
    public ShenwuBingfengRoleData(RoleDataContext context) { super(context); }
    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {}
    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {}
}
