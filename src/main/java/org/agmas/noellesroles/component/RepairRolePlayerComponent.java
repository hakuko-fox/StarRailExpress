package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDatabase;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class RepairRolePlayerComponent implements RoleComponent {
    public final Set<String> ownedRoles = new LinkedHashSet<>();
    public final EnumMap<RepairRoleDefinition.Faction, String> selectedRoles = new EnumMap<>(RepairRoleDefinition.Faction.class);
    public String activeRole = "";
    public int neutralTaskProgress = 0;
    public boolean neutralTaskCompleted = false;
    public long selectionEndTick = 0L;
    private final Player player;

    public RepairRolePlayerComponent(Player player) {
        this.player = player;
        ensureStarterRoles();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        activeRole = "";
        neutralTaskProgress = 0;
        neutralTaskCompleted = false;
        selectionEndTick = 0L;
        ensureStarterRoles();
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void ensureStarterRoles() {
        ownedRoles.addAll(RepairRoleDatabase.starterRoles());
        for (RepairRoleDefinition.Faction faction : RepairRoleDefinition.Faction.values()) {
            selectedRoles.computeIfAbsent(faction, ignored -> RepairRoleDefinition.byFaction(faction).stream()
                    .filter(role -> role.starter).findFirst().map(role -> role.id).orElse(""));
        }
    }

    public boolean owns(RepairRoleDefinition role) {
        return ownedRoles.contains(role.id);
    }

    public RepairRoleDefinition selectedRole(RepairRoleDefinition.Faction faction) {
        ensureStarterRoles();
        String id = selectedRoles.get(faction);
        return RepairRoleDefinition.byId(id).filter(role -> role.faction == faction && owns(role))
                .orElseGet(() -> RepairRoleDefinition.byFaction(faction).stream().filter(role -> role.starter).findFirst()
                        .orElse(RepairRoleDefinition.byFaction(faction).getFirst()));
    }

    public void setSelectedRole(RepairRoleDefinition role) {
        if (!owns(role)) {
            return;
        }
        selectedRoles.put(role.faction, role.id);
        sync();
    }

    public void unlock(RepairRoleDefinition role) {
        ownedRoles.add(role.id);
        sync();
        if (player instanceof ServerPlayer serverPlayer) {
            RepairRoleDatabase.saveFrom(serverPlayer);
        }
    }

    public void sync() {
        ModComponents.REPAIR_ROLES.sync(player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeData(tag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readData(tag);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeData(tag);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readData(tag);
        ensureStarterRoles();
    }

    private void writeData(CompoundTag tag) {
        ListTag owned = new ListTag();
        ownedRoles.forEach(role -> owned.add(StringTag.valueOf(role)));
        tag.put("Owned", owned);
        CompoundTag selected = new CompoundTag();
        selectedRoles.forEach((faction, role) -> selected.putString(faction.id(), role));
        tag.put("Selected", selected);
        tag.putString("ActiveRole", activeRole);
        tag.putInt("NeutralTaskProgress", neutralTaskProgress);
        tag.putBoolean("NeutralTaskCompleted", neutralTaskCompleted);
        tag.putLong("SelectionEndTick", selectionEndTick);
    }

    private void readData(CompoundTag tag) {
        ownedRoles.clear();
        if (tag.contains("Owned", Tag.TAG_LIST)) {
            ListTag owned = tag.getList("Owned", Tag.TAG_STRING);
            owned.forEach(entry -> ownedRoles.add(entry.getAsString()));
        }
        selectedRoles.clear();
        if (tag.contains("Selected", Tag.TAG_COMPOUND)) {
            CompoundTag selected = tag.getCompound("Selected");
            for (RepairRoleDefinition.Faction faction : RepairRoleDefinition.Faction.values()) {
                String role = selected.getString(faction.id());
                if (!role.isEmpty()) {
                    selectedRoles.put(faction, role);
                }
            }
        }
        activeRole = tag.getString("ActiveRole");
        neutralTaskProgress = tag.getInt("NeutralTaskProgress");
        neutralTaskCompleted = tag.getBoolean("NeutralTaskCompleted");
        selectionEndTick = tag.getLong("SelectionEndTick");
    }
}
