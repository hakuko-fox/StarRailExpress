package org.agmas.noellesroles.game.roles.killer.hakukofox;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class HakukoFoxPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<HakukoFoxPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "hakukofox"),
            HakukoFoxPlayerComponent.class);

    public enum CloneState {
        NONE,
        EXISTS,
        POV,
        POSSESSED
    }

    private final Player player;

    private boolean beastFormActive = false;
    private boolean nineLivesUsed = false;
    private int cloneId = -1;
    private CloneState cloneState = CloneState.NONE;
    private double originalX;
    private double originalY;
    private double originalZ;
    private float originalYaw;
    private float originalPitch;

    public HakukoFoxPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        beastFormActive = false;
        nineLivesUsed = false;
        cloneId = -1;
        cloneState = CloneState.NONE;
        originalX = 0;
        originalY = 0;
        originalZ = 0;
        originalYaw = 0;
        originalPitch = 0;
        sync();
    }

    @Override
    public void clear() {
        removeBeastEffects();
        despawnClone();
        init();
    }

    public boolean isBeastFormActive() {
        return beastFormActive;
    }

    public boolean isCloneActive() {
        return cloneState != CloneState.NONE;
    }

    public boolean isDisguised() {
        return beastFormActive;
    }

    public static boolean isDisguised(Player player) {
        HakukoFoxPlayerComponent comp = KEY.maybeGet(player).orElse(null);
        return comp != null && comp.isDisguised();
    }

    public boolean toggleBeastForm(ServerPlayer sp, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (beastFormActive) {
            removeBeastEffects();
            beastFormActive = false;
            sp.refreshDimensions();
            context.setSkillCooldown(180 * 20);
            sync();
            return true;
        } else {
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1, false, false, true));
            sp.addEffect(new MobEffectInstance(MobEffects.JUMP, -1, 1, false, false, true));
            beastFormActive = true;
            nineLivesUsed = false;
            sp.refreshDimensions();

            ServerLevel world = sp.serverLevel();
            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.FOX_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
            sp.displayClientMessage(
                    Component.translatable("skill.noellesroles.hakukofox.transform"),
                    true);
            sync();
            return true;
        }
    }

    public boolean useCloneSkill(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        return switch (cloneState) {
            case NONE -> spawnClone(sp);
            case EXISTS -> enterPOV(sp);
            case POV -> possessClone(sp);
            case POSSESSED -> revertToOriginal(sp);
        };
    }

    private boolean spawnClone(ServerPlayer sp) {
        int cost = 50;
        var shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.hakukofox.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        ServerLevel world = sp.serverLevel();
        Fox fox = EntityType.FOX.create(world);
        if (fox == null) return false;

        fox.setVariant(Fox.Type.SNOW);
        fox.setPos(sp.getX(), sp.getY(), sp.getZ());
        fox.setCustomName(null);
        fox.setCustomNameVisible(false);
        fox.setPersistenceRequired();

        world.addFreshEntity(fox);
        cloneId = fox.getId();
        cloneState = CloneState.EXISTS;
        sp.playNotifySound(SoundEvents.FOX_SPIT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.clone_spawn"),
                true);
        sync();
        return true;
    }

    private boolean enterPOV(ServerPlayer sp) {
        Fox fox = getCloneFox(sp);
        if (fox == null) {
            cloneState = CloneState.NONE;
            cloneId = -1;
            sync();
            return false;
        }
        originalX = sp.getX();
        originalY = sp.getY();
        originalZ = sp.getZ();
        originalYaw = sp.getYRot();
        originalPitch = sp.getXRot();
        sp.setCamera(fox);
        cloneState = CloneState.POV;
        sync();
        return true;
    }

    private boolean possessClone(ServerPlayer sp) {
        Fox fox = getCloneFox(sp);
        if (fox == null) {
            cloneState = CloneState.NONE;
            cloneId = -1;
            sync();
            return false;
        }
        double fx = fox.getX();
        double fy = fox.getY();
        double fz = fox.getZ();
        fox.discard();
        sp.setCamera(null);
        sp.teleportTo(fx, fy, fz);
        cloneState = CloneState.POSSESSED;
        cloneId = -1;
        sync();
        return true;
    }

    private boolean revertToOriginal(ServerPlayer sp) {
        sp.setCamera(null);
        sp.teleportTo(originalX, originalY, originalZ);
        sp.setYRot(originalYaw);
        sp.setXRot(originalPitch);
        cloneState = CloneState.NONE;
        cloneId = -1;
        sync();
        return true;
    }

    public boolean exitPOV(ServerPlayer sp) {
        if (cloneState != CloneState.POV) {
            return false;
        }
        sp.setCamera(null);
        cloneState = CloneState.EXISTS;
        sync();
        return true;
    }

    public boolean tryUseNineLives() {
        if (beastFormActive && !nineLivesUsed) {
            nineLivesUsed = true;
            sync();
            return true;
        }
        return false;
    }

    public boolean hasNineLivesRemaining() {
        return beastFormActive && !nineLivesUsed;
    }

    private Fox getCloneFox(ServerPlayer sp) {
        if (cloneId == -1) return null;
        Entity e = sp.serverLevel().getEntity(cloneId);
        if (e instanceof Fox fox) return fox;
        return null;
    }

    private void despawnClone() {
        if (player instanceof ServerPlayer sp) {
            Fox fox = getCloneFox(sp);
            if (fox != null) {
                fox.discard();
            }
        }
        cloneId = -1;
        cloneState = CloneState.NONE;
    }

    private void removeBeastEffects() {
        if (player instanceof ServerPlayer sp) {
            if (sp.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                sp.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
            if (sp.hasEffect(MobEffects.JUMP)) {
                sp.removeEffect(MobEffects.JUMP);
            }
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        if (cloneState != CloneState.NONE && getCloneFox(sp) == null) {
            if (cloneState == CloneState.POV) {
                sp.setCamera(null);
            }
            cloneState = CloneState.NONE;
            cloneId = -1;
            sync();
        }

        if (beastFormActive && !sp.isAlive()) {
            removeBeastEffects();
            beastFormActive = false;
            sp.refreshDimensions();
            sync();
        }

        if (cloneState == CloneState.POV && sp.getCamera() == sp) {
            exitPOV(sp);
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("beastFormActive", beastFormActive);
        tag.putBoolean("nineLivesUsed", nineLivesUsed);
        tag.putInt("cloneId", cloneId);
        tag.putString("cloneState", cloneState.name());
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        beastFormActive = tag.getBoolean("beastFormActive");
        nineLivesUsed = tag.getBoolean("nineLivesUsed");
        cloneId = tag.getInt("cloneId");
        try {
            cloneState = CloneState.valueOf(tag.getString("cloneState"));
        } catch (IllegalArgumentException e) {
            cloneState = CloneState.NONE;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
        tag.putDouble("originalX", originalX);
        tag.putDouble("originalY", originalY);
        tag.putDouble("originalZ", originalZ);
        tag.putFloat("originalYaw", originalYaw);
        tag.putFloat("originalPitch", originalPitch);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
        originalX = tag.getDouble("originalX");
        originalY = tag.getDouble("originalY");
        originalZ = tag.getDouble("originalZ");
        originalYaw = tag.getFloat("originalYaw");
        originalPitch = tag.getFloat("originalPitch");
    }
}