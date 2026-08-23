package org.agmas.noellesroles.game.roles.vtuber;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/** Synced visual state shared by VTuber roles with animal forms. */
public class VtuberRolePlayerComponent implements RoleComponent {
    public static final int NONE = 0;
    public static final int BLOOD_FOX = 1;
    public static final int YOZORA_CAT = 2;

    public static final ComponentKey<VtuberRolePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "vtuber_role_state"),
            VtuberRolePlayerComponent.class);

    private final Player player;
    private int disguise;

    public VtuberRolePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer other) {
        return true;
    }

    public int getDisguise() {
        return disguise;
    }

    public boolean isDisguised() {
        return disguise != NONE;
    }

    public void setDisguise(int disguise) {
        this.disguise = disguise;
        KEY.sync(player);
    }

    @Override
    public void init() {
        setDisguise(NONE);
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Disguise", disguise);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        disguise = tag.getInt("Disguise");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
    }
}
