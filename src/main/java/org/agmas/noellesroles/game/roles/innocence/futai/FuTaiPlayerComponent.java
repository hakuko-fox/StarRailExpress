package org.agmas.noellesroles.game.roles.innocence.futai;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 風太（Fu_Tai）— 平民陣營
 *
 * 主動技1（G）：神諭。冷卻120秒，消耗200金幣，獲得目前殺手以及中立尚餘數量。
 * 被動技（巫女祝福）：抵擋一次任何方式死亡。
 * 標籤：香港Vtuber
 */
public class FuTaiPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<FuTaiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "futai"),
            FuTaiPlayerComponent.class);

    private final Player player;
    private boolean blessingUsed = false;

    public FuTaiPlayerComponent(Player player) {
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
        blessingUsed = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean isBlessingUsed() {
        return blessingUsed;
    }

    /** 主動技1：神諭 — 消耗200金幣得知剩餘殺手與中立數量 */
    public boolean useOracleSkill(ServerPlayer sp, RoleSkillContext ctx) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        int cost = 200;
        var shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.futai.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        int killers = 0;
        int neutrals = 0;
        for (var p : sp.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            var role = gameWorld.getRole(p);
            if (role == null) {
                continue;
            }
            if (role.canUseKiller() && !role.isInnocent() && !role.isNeutrals()) {
                killers++;
            } else if (role.isNeutrals()) {
                neutrals++;
            }
        }

        sp.displayClientMessage(
                Component.translatable("message.noellesroles.futai.oracle_result", killers, neutrals),
                true);
        return true;
    }

    /** 被動祝福消耗：抵擋一次死亡。成功返回 true（死亡被攔截）。 */
    public boolean tryConsumeBlessing(ServerPlayer sp) {
        if (blessingUsed) {
            return false;
        }
        blessingUsed = true;
        sync();
        sp.setHealth(sp.getMaxHealth());
        sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.futai.bless_saved"), true);
        return true;
    }

    @Override
    public void serverTick() {
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("blessingUsed", blessingUsed);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        blessingUsed = tag.getBoolean("blessingUsed");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
    }

    // 被動：抵擋一次任何方式死亡
    static {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim instanceof ServerPlayer sp) {
                FuTaiPlayerComponent comp = KEY.maybeGet(sp).orElse(null);
                if (comp != null && comp.tryConsumeBlessing(sp)) {
                    return false;
                }
            }
            return true;
        });
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (victim instanceof ServerPlayer sp) {
                FuTaiPlayerComponent comp = KEY.maybeGet(sp).orElse(null);
                if (comp != null && comp.tryConsumeBlessing(sp)) {
                    return false;
                }
            }
            return true;
        });
    }
}