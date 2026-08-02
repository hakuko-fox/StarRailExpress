package org.agmas.noellesroles.game.roles.killer.hakukofox2;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
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

/**
 * 白狐 2.0 — 殺手陣營
 *
 * 主動技1（G）：獸化之力（可隨時關閉）冷卻180秒。變身成獸化型態—雪狐：
 *   - 無法攻擊
 *   - 所受攻擊不會使你死亡
 *   - 獲得 速度II、跳躍II
 * 主動技2（Shift+G）：瞬真的。冷卻60秒，消耗100金幣，令其他玩家緩速、失明、無法跳躍5秒。
 * 被動技（修仙成狐）：開局時失明60秒，60秒後自動化身為獸化型態。
 * 標籤：香港Vtuber
 */
public class Hakukofox2PlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<Hakukofox2PlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "hakukofox2"),
            Hakukofox2PlayerComponent.class);

    private final Player player;

    private boolean beastFormActive = false;
    // 修仙成狐：開局失明 60 秒，時間到後自動化身
    private boolean cultivating = false;
    private long cultivateEndTime = 0;

    public Hakukofox2PlayerComponent(Player player) {
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
        cultivating = false;
        cultivateEndTime = 0;
        sync();
    }

    @Override
    public void clear() {
        removeBeastEffects();
        if (player instanceof ServerPlayer sp) {
            sp.refreshDimensions();
        }
        init();
    }

    public boolean isBeastFormActive() {
        return beastFormActive;
    }

    public boolean isDisguised() {
        return beastFormActive;
    }

    public static boolean isDisguised(Player player) {
        Hakukofox2PlayerComponent comp = KEY.maybeGet(player).orElse(null);
        return comp != null && comp.isDisguised();
    }

    /** 被動：修仙之狐 — 開局給予失明60秒，倒數結束後自動化身 */
    public void startCultivation(ServerPlayer sp) {
        if (cultivating) return;
        cultivating = true;
        cultivateEndTime = sp.serverLevel().getGameTime() + 60 * 20;
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60 * 20, 0, false, false, true));
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.hakukofox2.cultivation_start"), true);
        sync();
    }

    /** 進入獸化型態（不消耗技能冷卻，供被動自動化身使用） */
    public void enterBeastForm(ServerPlayer sp) {
        if (beastFormActive) return;
        beastFormActive = true;
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.JUMP, -1, 1, false, false, true));
        sp.refreshDimensions();

        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.FOX_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox2.transform_on"), true);
        sync();
    }

    private void leaveBeastForm(ServerPlayer sp) {
        removeBeastEffects();
        beastFormActive = false;
        sp.refreshDimensions();
        sync();
    }

    public boolean toggleBeastForm(ServerPlayer sp, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (beastFormActive) {
            leaveBeastForm(sp);
            context.setSkillCooldown(180 * 20);
            return true;
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
        var shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.hakukofox2.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        ServerLevel level = sp.serverLevel();
        for (ServerPlayer other : level.players()) {
            if (other == sp) continue;
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 255, false, false, true));
            other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, true));
            other.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 128, false, false, true));
            other.displayClientMessage(
                    Component.translatable("skill.noellesroles.hakukofox2.freeze_notify"), true);
        }

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.FOX_SCREECH, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox2.freeze_self"), true);
        ctx.setSkillCooldown(60 * 20);
        return true;
    }

    private void removeBeastEffects() {
        if (player instanceof ServerPlayer sp) {
            var speed = sp.getEffect(MobEffects.MOVEMENT_SPEED);
            if (speed != null && speed.getDuration() < 0 && speed.getAmplifier() == 1) {
                sp.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
            var jump = sp.getEffect(MobEffects.JUMP);
            if (jump != null && jump.getDuration() < 0 && jump.getAmplifier() == 1) {
                sp.removeEffect(MobEffects.JUMP);
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

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
    }

    private static boolean isBeastForm(Player player) {
        Hakukofox2PlayerComponent comp = KEY.maybeGet(player).orElse(null);
        return comp != null && comp.isBeastFormActive();
    }

    //
    // 被動（獸化）：所受攻擊不會死亡。
    // 在獸化型態下每一種死因都會被否決，並回滿血量（同原版白狐的「狐有九命」，但期間內持續免疫）。
    //
    static {
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
}