package org.agmas.noellesroles.role_data.vtuber;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.init.ModEffects;

/** Synced visual state shared by VTuber roles with animal forms. */
public class VtuberRoleData extends SimpleRoleData {
    public static final int NONE = 0;
    public static final int BLOOD_FOX = 1;
    public static final int YOZORA_CAT = 2;

    private int disguise;
    private long menuCooldownUntil;
    private String markedTargetName = "";
    private java.util.UUID knifeGrant;

    // Server-only state belongs to the current role and is never broadcast.
    public long weaponBlockedUntil;
    public long nextAllianceRoll;
    public long yuzuSleepDeadline;
    public boolean yuzuSleepWeaponBlocked;
    public int passerbyTicks;
    public int yozoraDeathNotices;
    public long ayersNextSwitch;
    public Boolean ayersFastMode;
    public long bloodFoxLastConsume;
    public long hoshizoraWeaponBlockedUntil;
    public boolean kanaParty;
    public int kanaInitialPlayers;
    public final java.util.Set<java.util.UUID> kanaAffected = new java.util.HashSet<>();
    public java.util.UUID baiyuMarkedTarget;

    public long getMenuCooldownUntil() { return menuCooldownUntil; }

    public long getMenuCooldownSeconds() {
        return Math.max(0L, (menuCooldownUntil - GameUtils.getTicksFromGameStart(player.level()) + 19L) / 20L);
    }

    public void setMenuCooldownUntil(long until) {
        menuCooldownUntil = until;
        sync();
    }

    public String getMarkedTargetName() {
        return markedTargetName;
    }

    public void setMarkedTargetName(String name) {
        markedTargetName = name;
        sync();
    }

    public java.util.UUID getKnifeGrant() {
        return knifeGrant;
    }

    public void setKnifeGrant(java.util.UUID grant) {
        knifeGrant = grant;
        sync();
    }

    public VtuberRoleData(RoleDataContext context) {
        super(context);
        resetServerState();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer other) {
        return this instanceof AnimalFormRoleData || other == player;
    }

    public int getDisguise() {
        return disguise;
    }

    public boolean isDisguised() {
        return disguise != NONE;
    }

    public void setDisguise(int disguise) {
        this.disguise = disguise;
        sync();
    }

    @Override
    public void init() {
        menuCooldownUntil = 0L;
        markedTargetName = "";
        knifeGrant = null;
        disguise = NONE;
        resetServerState();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer serverPlayer) {
            for (java.util.UUID uuid : kanaAffected) {
                ServerPlayer target = serverPlayer.getServer().getPlayerList().getPlayer(uuid);
                boolean otherOwner = serverPlayer.getServer().getPlayerList().getPlayers().stream()
                        .map(p -> RoleData.getNullable(VtuberRoleData.class, p))
                        .anyMatch(d -> d != null && d != this && d.kanaAffected.contains(uuid));
                if (target != null && !otherOwner) {
                    target.removeEffect(ModEffects.VOICE_HELIUM);
                    target.removeEffect(ModEffects.HEAVY_METAL_VOICE);
                }
            }
            if (disguise != NONE) player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        }
        disguise = NONE;
        kanaAffected.clear();
        knifeGrant = null;
        markedTargetName = "";
        menuCooldownUntil = 0;
        if (player instanceof ServerPlayer) sync();
    }

    private void resetServerState() {
        long now = player.level().getGameTime();
        nextAllianceRoll = now + 20L * 20L;
        yuzuSleepDeadline = now + 20L * 90L;
        bloodFoxLastConsume = now;
        ayersNextSwitch = 0;
        ayersFastMode = null;
        weaponBlockedUntil = 0;
        yuzuSleepWeaponBlocked = false;
        passerbyTicks = 0;
        yozoraDeathNotices = 0;
        hoshizoraWeaponBlockedUntil = 0;
        kanaParty = false;
        kanaInitialPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
        baiyuMarkedTarget = null;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Disguise", disguise);
        tag.putLong("MenuCooldownUntil", menuCooldownUntil);
        tag.putString("MarkedTargetName", markedTargetName);
        if (knifeGrant != null) {
            tag.putUUID("KnifeGrant", knifeGrant);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        disguise = tag.getInt("Disguise");
        menuCooldownUntil = tag.getLong("MenuCooldownUntil");
        markedTargetName = tag.getString("MarkedTargetName");
        knifeGrant = tag.hasUUID("KnifeGrant") ? tag.getUUID("KnifeGrant") : null;
    }

}
