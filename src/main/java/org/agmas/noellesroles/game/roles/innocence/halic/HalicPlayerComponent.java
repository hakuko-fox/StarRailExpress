package org.agmas.noellesroles.game.roles.innocence.halic;

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
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class HalicPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<HalicPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "halic"),
            HalicPlayerComponent.class);

    private final Player player;
    private long lastDecoyTime = 0;

    public HalicPlayerComponent(Player player) {
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
        this.lastDecoyTime = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean createDecoy(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (now - lastDecoyTime < 200) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.halic.decoy_cooldown"),
                    true);
            return false;
        }
        var shop = SREPlayerShopComponent.KEY.get(sp);
        int cost = 10;
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.halic.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        PuppeteerBodyEntity decoy = new PuppeteerBodyEntity(ModEntities.PUPPETEER_BODY, level);
        decoy.setPos(sp.getX(), sp.getY(), sp.getZ());
        decoy.setYRot(sp.getYRot());
        decoy.setXRot(sp.getXRot());
        decoy.setOwner(sp);
        decoy.setHalicDecoy(true);
        decoy.setPersistenceRequired();
        level.addFreshEntity(decoy);

        lastDecoyTime = now;
        sync();

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.halic.decoy_created"),
                true);
        return true;
    }

    public boolean electrocute(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        int cost = 50;
        var shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.halic.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        double range = 10.0;
        int count = 0;
        for (ServerPlayer target : sp.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                sp.getBoundingBox().inflate(range),
                p -> !p.getUUID().equals(sp.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
            if (sp.distanceToSqr(target) > range * range) continue;
            target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 200, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 200, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, 200, 0, false, false, true));
            count++;
        }

        sp.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.halic.electrocuted", count),
                true);
        return true;
    }

    @Override
    public void serverTick() {
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("lastDecoyTime", lastDecoyTime);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        lastDecoyTime = tag.getLong("lastDecoyTime");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
