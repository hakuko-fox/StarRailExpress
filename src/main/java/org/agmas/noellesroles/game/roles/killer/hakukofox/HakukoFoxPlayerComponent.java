package org.agmas.noellesroles.game.roles.killer.hakukofox;

import io.wifi.starrailexpress.api.RoleComponent;
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
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class HakukoFoxPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<HakukoFoxPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "hakukofox"),
            HakukoFoxPlayerComponent.class);

    private final Player player;
    private int foxFormRemainingTicks = 0;

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
        this.foxFormRemainingTicks = 0;
        sync();
    }

    @Override
    public void clear() {
        if (foxFormRemainingTicks > 0) {
            foxFormRemainingTicks = 0;
            if (player instanceof ServerPlayer sp) {
                if (sp.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    sp.removeEffect(MobEffects.MOVEMENT_SPEED);
                }
                sp.refreshDimensions();
            }
        }
        sync();
    }

    public boolean isFoxFormActive() {
        return foxFormRemainingTicks > 0;
    }

    public boolean isDisguised() {
        return foxFormRemainingTicks > 0;
    }

    public static boolean isDisguised(Player player) {
        HakukoFoxPlayerComponent comp = KEY.maybeGet(player).orElse(null);
        return comp != null && comp.isDisguised();
    }

    public boolean transformFox(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (sp.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            sp.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 2, false, false, true));
        this.foxFormRemainingTicks = 20 * 20;
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

    public boolean foxFire(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        int cost = 40;
        var shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.hakukofox.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        double range = 6.0;
        for (ServerPlayer target : sp.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                sp.getBoundingBox().inflate(range),
                p -> !p.getUUID().equals(sp.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
            if (sp.distanceToSqr(target) > range * range) continue;
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 3 * 20, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 8 * 20, 0, false, false, true));
        }
        sp.playNotifySound(SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.foxfire"),
                true);
        return true;
    }

    @Override
    public void serverTick() {
        if (foxFormRemainingTicks <= 0) return;
        foxFormRemainingTicks--;
        if (foxFormRemainingTicks <= 0) {
            if (player instanceof ServerPlayer sp) {
                if (sp.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    sp.removeEffect(MobEffects.MOVEMENT_SPEED);
                }
                sp.refreshDimensions();
            }
            sync();
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("foxFormRemainingTicks", foxFormRemainingTicks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        foxFormRemainingTicks = tag.getInt("foxFormRemainingTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
