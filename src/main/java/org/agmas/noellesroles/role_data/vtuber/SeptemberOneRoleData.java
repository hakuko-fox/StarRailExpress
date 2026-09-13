package org.agmas.noellesroles.role_data.vtuber;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.init.ModEffects;

/** Current-role server state; no private progress is broadcast. */
public final class SeptemberOneRoleData extends SimpleRoleData {
    public boolean attacked;
    public boolean fatalShieldUsed;
    public int tasksCompleted;
    public SeptemberOneRoleData(RoleDataContext context) { super(context); }
    @Override
    public void clear() {
        if (player instanceof ServerPlayer) {
            player.removeEffect(ModEffects.NINE_ONE_TASK_CONCEALMENT);
            if (attacked) {
                player.removeEffect(ModEffects.VOICE_SILENCE);
                player.removeEffect(ModEffects.CHAT_BAN);
            }
        }
    }
    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {}
    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {}
}
