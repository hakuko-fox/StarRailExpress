package org.agmas.noellesroles.role_data.vtuber;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.agmas.noellesroles.role.vtuber.LafinaRole.LafinaCharge;

public final class LafinaRoleData extends SimpleRoleData {
    public LafinaCharge charge;
    public LafinaRoleData(RoleDataContext context) { super(context); }
    @Override public void clear() {
        if (charge != null) {
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            charge = null;
        }
    }
    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {}
    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {}
}
