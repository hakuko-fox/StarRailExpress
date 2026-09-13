package org.agmas.noellesroles.role_data.vtuber;

import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

/**
 * 白狐 2.0 — 殺手陣營
 *
 * 主動技1（G）：獸化之力（可隨時關閉）關閉後冷卻20秒。變身成獸化型態—雪狐：
 *   - 無法攻擊
 *   - 所受攻擊不會使你死亡
 *   - 獲得速度II
 * 主動技2（Shift+G）：瞬結。冷卻60秒，消耗100金幣，令其他玩家緩速、失明3秒。
 * 被動技（修仙成狐）：開局時失明60秒，60秒後自動化身為獸化型態。
 * 標籤：香港Vtuber
 */
public class HakukoFoxRoleData extends SimpleRoleData {

    private boolean beastFormActive = false;
    // 修仙成狐：開局失明 60 秒，時間到後自動化身
    private boolean cultivating = false;
    private long cultivateEndTime = 0;

    public HakukoFoxRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        // 偽裝狀態必須同步給所有客戶端，否則其他玩家看不到狐狸模型，且遊戲結束時無法正確還原。
        return true;
    }

    @Override
    public void clear() {
        beastFormActive = false;
        cultivating = false;
        cultivateEndTime = 0;
        removeBeastEffects();
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.BLINDNESS);
            sp.refreshDimensions();
            sync();
        }

    }

    public boolean isBeastFormActive() {
        return beastFormActive;
    }

    public boolean isCultivating() {
        return cultivating;
    }

    public boolean isDisguised() {
        return beastFormActive;
    }

    public static boolean isDisguised(Player player) {
        HakukoFoxRoleData comp = RoleData.getOptional(HakukoFoxRoleData.class, player).orElse(null);
        return comp != null && comp.isDisguised();
    }

    /** 被動：修仙之狐 — 開局給予失明60秒，倒數結束後自動化身 */
    public void startCultivation(ServerPlayer sp) {
        if (cultivating) return;
        cultivating = true;
        cultivateEndTime = sp.serverLevel().getGameTime() + 60 * 20;
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60 * 20, 0, false, false, true));
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.hakukofox.cultivation_start"), true);
        sync();
    }

    /** 進入獸化型態（不消耗技能冷卻，供被動自動化身使用） */
    public void enterBeastForm(ServerPlayer sp) {
        if (beastFormActive) return;
        beastFormActive = true;
        // 修仙成狐被動一旦完成（手動或自動變身），清除倒數與失明，避免離開型態後又被自動變身
        if (cultivating) {
            cultivating = false;
            cultivateEndTime = 0;
            if (sp.hasEffect(MobEffects.BLINDNESS)) {
                sp.removeEffect(MobEffects.BLINDNESS);
            }
        }
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1, false, false, true));
        sp.refreshDimensions();

        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.FOX_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.transform_on"), true);
        sync();
    }

    private void leaveBeastForm(ServerPlayer sp) {
        removeBeastEffects();
        beastFormActive = false;
        // 離開型態時清除被動倒數（防禦性：正常流程已在 enterBeastForm 清除）
        cultivating = false;
        cultivateEndTime = 0;
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, false, true));
        sp.refreshDimensions();
        sync();
    }

    public boolean toggleBeastForm(ServerPlayer sp, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (beastFormActive) {
            leaveBeastForm(sp);
            context.setSkillCooldown(20 * 20);
            return true;
        }
        if (cultivating) {
            return false;
        }
        if (!context.skillReady()) {
            sp.displayClientMessage(Component.translatable("message.sre.skill.cooldown",
                    String.format("%.1f", context.skillState().cooldown / 20.0F)), true);
            return false;
        }
        enterBeastForm(sp);
        return true;
    }

    public boolean useFreezeSkill(ServerPlayer sp, RoleSkillContext ctx) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;

        int cost = 100;
        int balance = MoneyUtils.getBalance(sp);
        if (balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.hakukofox.not_enough_money", cost),
                    true);
            return false;
        }
        MoneyUtils.addToBalance(sp, -cost);

        ServerLevel level = sp.serverLevel();
        for (ServerPlayer other : level.players()) {
            if (other == sp || !GameUtils.isPlayerAliveAndSurvival(other)) continue;
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3 * 20, 0, false, false, true));
            other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 3 * 20, 0, false, false, true));
            other.displayClientMessage(
                    Component.translatable("skill.noellesroles.hakukofox.freeze_notify"), true);
        }

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.FOX_SCREECH, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.freeze_self"), true);
        return true;
    }

    private void removeBeastEffects() {
        if (player instanceof ServerPlayer sp) {
            var speed = sp.getEffect(MobEffects.MOVEMENT_SPEED);
            if (speed != null && speed.getDuration() < 0 && speed.getAmplifier() == 1) {
                sp.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        if (beastFormActive && !sp.isAlive()) {
            removeBeastEffects();
            beastFormActive = false;
            sp.refreshDimensions();
            sync();
            return;
        }

        // 修仙之狐：開出60秒後自動化身
        if (cultivating && !beastFormActive && GameUtils.isPlayerAliveAndSurvival(sp)
                    && sp.serverLevel().getGameTime() >= cultivateEndTime) {
            cultivating = false;
            if (sp.hasEffect(MobEffects.BLINDNESS)) {
                sp.removeEffect(MobEffects.BLINDNESS);
            }
            enterBeastForm(sp);
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("beastFormActive", beastFormActive);
        tag.putBoolean("cultivating", cultivating);
        tag.putLong("cultivateEndTime", cultivateEndTime);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        beastFormActive = tag.getBoolean("beastFormActive");
        cultivating = tag.getBoolean("cultivating");
        cultivateEndTime = tag.getLong("cultivateEndTime");
    }

    private static boolean isBeastForm(Player player) {
        HakukoFoxRoleData comp = RoleData.getOptional(HakukoFoxRoleData.class, player).orElse(null);
        return comp != null && comp.isBeastFormActive()
                && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.HAKUKO_FOX);
    }

    //
    // 被動（獸化）：所受攻擊不會死亡。
    // 在獸化型態下每一種死因都會被否決，並回滿血量（同原版白狐的「狐有九命」，但期間內持續免疫）。
    //
    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
                    if (victim instanceof ServerPlayer sp && isBeastForm(sp)) {
                        sp.setHealth(sp.getMaxHealth());
                        return false;
                    }
                    return true;
                });
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
                    if (victim instanceof ServerPlayer sp && isBeastForm(sp)) {
                        sp.setHealth(sp.getMaxHealth());
                        return false;
                    }
                    return true;
                });
    }

    public static boolean isCultivating(Player player) {
        return RoleData.getOptional(HakukoFoxRoleData.class, player).map(HakukoFoxRoleData::isCultivating).orElse(false);
    }
}
