package org.agmas.noellesroles.game.roles.innocence.halic;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class HalicPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<HalicPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "halic"),
            HalicPlayerComponent.class);

    private final Player player;
    private int decoyRemainingTicks = 0;
    private int decoyId = -1;

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
        this.decoyRemainingTicks = 0;
        this.decoyId = -1;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean hasActiveDecoy() {
        return decoyRemainingTicks > 0;
    }

    public int getDecoyRemainingTicks() {
        return decoyRemainingTicks;
    }

    public boolean createDecoy(ServerPlayer sp) {
        if (hasActiveDecoy()) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.halic.decoy_already_active"),
                    true);
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        ServerLevel world = sp.serverLevel();
        PuppeteerBodyEntity decoy = new PuppeteerBodyEntity(ModEntities.PUPPETEER_BODY, world);
        decoy.setPos(sp.getX(), sp.getY(), sp.getZ());
        decoy.setYRot(sp.getYRot());
        decoy.setXRot(sp.getXRot());
        decoy.setOwner(sp);
        world.addFreshEntity(decoy);

        this.decoyRemainingTicks = 30 * 20;
        this.decoyId = decoy.getId();
        sync();

        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.halic.decoy_created"),
                true);
        return true;
    }

    public boolean restoreSanity(ServerPlayer sp) {
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
        double range = 8.0;
        int restored = 0;
        for (ServerPlayer target : sp.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                sp.getBoundingBox().inflate(range),
                p -> !p.getUUID().equals(sp.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
            if (sp.distanceToSqr(target) > range * range) continue;
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(target);
            mood.addMood(0.3f);
            restored++;
        }
        sp.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.halic.sanity_restored", restored, cost),
                true);
        return true;
    }

    @Override
    public void serverTick() {
        if (decoyRemainingTicks <= 0) return;
        decoyRemainingTicks--;
        if (decoyRemainingTicks <= 0) {
            if (player.level() instanceof ServerLevel sl) {
                var e = sl.getEntity(decoyId);
                if (e != null) e.discard();
            }
            decoyId = -1;
            sync();
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("decoyRemainingTicks", decoyRemainingTicks);
        tag.putInt("decoyId", decoyId);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        decoyRemainingTicks = tag.getInt("decoyRemainingTicks");
        decoyId = tag.getInt("decoyId");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
