package org.agmas.noellesroles.game.roles.killer.nine_mui;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 玖璃（9mui）— 平民陣營
 *
 * 主動技1（G）：行動敏捷。冷卻90秒，消耗100金幣，獲得速度II 7秒。
 * 被動技（回歸石化）：每一分鐘有33%機會進入石化狀態10秒。石化時無法說話、無法移動，同時無敵。
 * 標籤：香港Vtuber
 */
public class NineMuiPlayerComponent implements RoleComponent, ServerTickingComponent {

    private static final long PETRIFY_SCAN_INTERVAL_TICKS = 60 * 20; // 每一分鐘
    private static final int PETRIFY_DURATION_TICKS = 10 * 20;

    public static final ComponentKey<NineMuiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "9muimui"),
            NineMuiPlayerComponent.class);

    private final Player player;

    private long nextPetrifyScan = 0;
    private long petrifyEndTime = 0;

    public NineMuiPlayerComponent(Player player) {
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
        nextPetrifyScan = 0;
        petrifyEndTime = 0;
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            clearPetrifiedState(sp);
        }
        init();
    }

    public boolean isPetrified() {
        return petrifyEndTime > 0;
    }

    /** 主動技1：行動敏捷 — 消耗100金幣，獲得速度II 7秒 */
    public boolean useBlessingSkill(ServerPlayer sp, RoleSkillContext ctx) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        int cost = 100;
        var shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.9muimui.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 7 * 20, 1, false, false, true));
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.9muimui.blessing_on"), true);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.3F, 1.4F);
        return true;
    }

    /** 被動：石化 — 立即進入石化狀態（方便測試/供外部呼叫） */
    public void startPetrified(ServerPlayer sp) {
        if (isPetrified()) {
            return;
        }
        long now = sp.serverLevel().getGameTime();
        petrifyEndTime = now + PETRIFY_DURATION_TICKS;
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, PETRIFY_DURATION_TICKS, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, PETRIFY_DURATION_TICKS, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, PETRIFY_DURATION_TICKS, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, PETRIFY_DURATION_TICKS, 0, false, false, true));
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.9muimui.petrified_on"), true);
        sync();
    }

private void clearPetrifiedState(ServerPlayer sp) {
        if (sp.hasEffect(ModEffects.MOVE_BANED)) {
            sp.removeEffect(ModEffects.MOVE_BANED);
        }
        if (sp.hasEffect(ModEffects.VOICE_SILENCE)) {
            sp.removeEffect(ModEffects.VOICE_SILENCE);
        }
        if (sp.hasEffect(ModEffects.CHAT_BAN)) {
            sp.removeEffect(ModEffects.CHAT_BAN);
        }
        if (sp.hasEffect(ModEffects.INVINCIBLE)) {
            sp.removeEffect(ModEffects.INVINCIBLE);
        }
        petrifyEndTime = 0;
        sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
        // 僅在遊戲進行中且玩家確實為玖璃時才觸發被動，避免在大廳等場合誤石化
        if (!gameWorldComponent.isRunning() || !gameWorldComponent.isRole(sp, ModRoles.NINE_MUI)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return;
        }
        long now = sp.serverLevel().getGameTime();

        if (isPetrified()) {
            if (now >= petrifyEndTime) {
                clearPetrifiedState(sp);
            }
            return;
        }

        if (nextPetrifyScan == 0) {
            nextPetrifyScan = now + PETRIFY_SCAN_INTERVAL_TICKS;
            return;
        }
        if (now >= nextPetrifyScan) {
            nextPetrifyScan = now + PETRIFY_SCAN_INTERVAL_TICKS;
            if (sp.getRandom().nextFloat() < 0.33F) {
                startPetrified(sp);
            }
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("nextPetrifyScan", nextPetrifyScan);
        tag.putLong("petrifyEndTime", petrifyEndTime);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        nextPetrifyScan = tag.getLong("nextPetrifyScan");
        petrifyEndTime = tag.getLong("petrifyEndTime");
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
