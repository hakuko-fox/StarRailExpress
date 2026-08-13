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
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 哈力克 2.0 — 平民陣營
 *
 * 主動技1（G）：如兔子般量產的威力。冷卻10秒（由框架 cooldownSeconds(10) 控制），消耗10金幣，
 *   生產一隻分身哈力克（直到遊戲結束）。分身被攻擊時分身會消失，攻擊者失明5秒。
 * 主動技2（Shift+G）：漏電仿生人。每局遊戲最多1次（由框架 charges(1) 控制），消耗50金幣，
 *   令所有哈力克附近7格玩家停止行動7秒。
 * 被動技（和平統治）：如小透明般無法被殺手透視所在點，且無法購買武器。
 * 標籤：香港Vtuber
 */
public class HalicPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<HalicPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "halic"),
            HalicPlayerComponent.class);

    private final Player player;

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
        sync();
    }

    @Override
    public void clear() {
        clearDecoys();
        init();
    }

    private void clearDecoys() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        for (PuppeteerBodyEntity decoy : sp.serverLevel().getEntitiesOfClass(
                PuppeteerBodyEntity.class,
                new AABB(sp.blockPosition()).inflate(10000),
                entity -> entity.isHalicDecoy()
                        && sp.getUUID().equals(entity.getOwnerUuid().orElse(null)))) {
            decoy.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    /** 主動技1：量產分身 — 冷卻由框架 cooldownSeconds(10) 控制 */
    public boolean createDecoy(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
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

        ServerLevel level = sp.serverLevel();
        PuppeteerBodyEntity decoy = new PuppeteerBodyEntity(ModEntities.PUPPETEER_BODY, level);
        decoy.setPos(sp.getX(), sp.getY(), sp.getZ());
        decoy.setYRot(sp.getYRot());
        decoy.setXRot(sp.getXRot());
        decoy.setOwner(sp);
        decoy.setHalicDecoy(true);
        decoy.setPersistenceRequired();
        level.addFreshEntity(decoy);

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.halic.decoy_created"),
                true);
        return true;
    }

    /** 主動技2：漏電 — 每局1次由框架 charges(1) 控制 */
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

        double range = 7.0;
        java.util.Set<ServerPlayer> targets = new java.util.HashSet<>();

        for (ServerPlayer target : sp.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                sp.getBoundingBox().inflate(range),
                p -> !p.getUUID().equals(sp.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
            if (sp.distanceToSqr(target) <= range * range) {
                targets.add(target);
            }
        }

        var decoys = sp.serverLevel().getEntitiesOfClass(
                PuppeteerBodyEntity.class,
                new AABB(sp.blockPosition()).inflate(10000),
                p -> p.isHalicDecoy()
                        && sp.getUUID().equals(p.getOwnerUuid().orElse(null)));
        for (var decoy : decoys) {
            for (ServerPlayer target : sp.serverLevel().getEntitiesOfClass(
                    ServerPlayer.class,
                    decoy.getBoundingBox().inflate(range),
                    p -> !p.getUUID().equals(sp.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
                if (decoy.distanceToSqr(target) <= range * range) {
                    targets.add(target);
                }
            }
        }

        int count = 0;
        for (ServerPlayer target : targets) {
            target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 140, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 140, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, 140, 0, false, false, true));
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
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
