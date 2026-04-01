package org.agmas.noellesroles.roles.ninja;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.List;

/**
 * 忍者组件
 * 参考红美铃护盾逻辑实现格挡技能
 */
public class NinjaPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<NinjaPlayerComponent> KEY = ModComponents.NINJA;

    // ==================== 常量 ====================
    private static final int ABILITY_COOLDOWN = 300 * 20;  // 300秒
    private static final int ABILITY_DURATION = 15 * 20;   // 15秒
    private static final int BOUNTY_AMOUNT = 150;
    private static final double BOUNTY_RADIUS = 5.0;

    // 夜行被动常量
    private static final int SPEED_DURATION = 220;
    private static final int SPEED_REFRESH_INTERVAL = 200;

    // ==================== 状态变量 ====================
    private final Player player;

    // 格挡技能（参考红美铃护盾）
    public int cooldown = 0;
    public int duration = 0;
    public boolean hasShield = false;
    public boolean shieldUsed = false;

    // 夜行被动状态
    private boolean inDarkness = false;
    private int speedEffectCooldown = 0;

    public NinjaPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.cooldown = 0;
        this.duration = 0;
        this.hasShield = false;
        this.shieldUsed = false;
        this.inDarkness = false;
        this.speedEffectCooldown = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public void sync() {
        ModComponents.NINJA.sync(this.player);
    }

    // ==================== 夜行被动 ====================
    private void checkNightSpeed() {
        int lightLevel = player.level().getRawBrightness(player.blockPosition(),
                net.minecraft.world.level.LightLayer.BLOCK.ordinal());
        var blackOut = SREWorldBlackoutComponent.KEY.maybeGet(player.level()).orElse(null);

        if (lightLevel <= 5 || (blackOut != null && blackOut.isBlackoutActive())) {
            if (!inDarkness) {
                inDarkness = true;
            }

            if (speedEffectCooldown <= 0) {
                if (player instanceof ServerPlayer sp) {
                    sp.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            SPEED_DURATION,
                            1,
                            true,
                            false,
                            true
                    ));
                }
                speedEffectCooldown = SPEED_REFRESH_INTERVAL;
            } else {
                speedEffectCooldown--;
            }
        } else {
            if (inDarkness) {
                inDarkness = false;
                speedEffectCooldown = 0;
            }
        }
    }

    // ==================== 格挡技能（参考红美铃护盾） ====================
    public boolean canUseAbility() {
        return cooldown <= 0 && duration <= 0 && !hasShield;
    }

    public boolean useAbility() {
        if (!canUseAbility()) {
            if (player instanceof ServerPlayer sp && cooldown > 0) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.ninja.block_cooldown",
                                        (cooldown + 19) / 20)
                                .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.NINJA)) return false;

        // 激活护盾（参考红美铃）
        this.hasShield = true;
        this.shieldUsed = false;
        this.duration = ABILITY_DURATION;
        this.cooldown = ABILITY_COOLDOWN;
        sync();

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ninja.block_activate")
                            .withStyle(ChatFormatting.GREEN), true);
        }

        // 播放护盾激活音效
        player.level().playSound(null, player.blockPosition(),
                TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.PLAYERS, 1.0F, 1.0F);

        return true;
    }

    /**
     * 尝试格挡伤害（参考红美铃护盾触发）
     */
    public boolean tryBlockDamage() {
        if (hasShield && !shieldUsed) {
            this.shieldUsed = true;
            this.hasShield = false;
            this.duration = 0;
            sync();

            // 播放护盾破碎音效
            player.level().playSound(null, player.blockPosition(),
                    TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.PLAYERS, 1.0F, 0.8F);

            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.ninja.block_success")
                                .withStyle(ChatFormatting.GOLD), true);
            }
            return true;
        }
        return false;
    }

    // ==================== HUD 辅助方法 ====================
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    public float getDurationSeconds() {
        return duration / 20.0f;
    }

    public boolean isAbilityActive() {
        return duration > 0;
    }

    public boolean isOnCooldown() {
        return cooldown > 0;
    }

    // ==================== Tick 处理 ====================
    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.NINJA))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer))
            return;

        // 夜行被动
        checkNightSpeed();

        // 格挡持续时间（参考红美铃）
        if (duration > 0) {
            duration--;
            if (duration <= 0) {
                hasShield = false;
                shieldUsed = false;
                sync();
            } else if (duration % 20 == 0) {
                // 每秒同步一次剩余时间
                sync();
            }
        }

        // 格挡冷却
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 20 == 0 || cooldown == 0) sync();
        }
    }

    // ==================== 事件注册 ====================
    public static void registerEvents() {
        // 赏金被动
        OnPlayerKilledPlayer.EVENT.register((victim, killer, reason) -> {
            if (killer == null || victim == null) return;

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(killer.level());
            if (gameWorld == null) return;

            boolean isNinja = gameWorld.isRole(killer, ModRoles.NINJA);
            if (!isNinja) return;

            AABB bounds = killer.getBoundingBox().inflate(BOUNTY_RADIUS);
            List<Player> nearby = killer.level().getEntitiesOfClass(Player.class, bounds);

            boolean hasGoodPlayerNearby = false;
            for (Player p : nearby) {
                if (p.getUUID().equals(killer.getUUID())) continue;
                if (p.getUUID().equals(victim.getUUID())) continue;
                if (p.isSpectator()) continue;
                if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;

                var role = gameWorld.getRole(p);
                if (role != null && role.isInnocent() && !role.isNeutrals()) {
                    hasGoodPlayerNearby = true;
                    break;
                }
            }

            if (hasGoodPlayerNearby && killer instanceof ServerPlayer sp) {
                SREPlayerShopComponent shopComp = SREPlayerShopComponent.KEY.get(sp);
                shopComp.addToBalance(BOUNTY_AMOUNT);
                shopComp.sync();

                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.ninja.bounty", BOUNTY_AMOUNT)
                                .withStyle(ChatFormatting.GOLD), true);
            }
        });

        // 格挡技能
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            NinjaPlayerComponent comp = KEY.get(victim);
            if (comp != null && comp.tryBlockDamage()) return false;
            return true;
        });
    }

    // ==================== NBT 序列化 ====================
    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", cooldown);
        tag.putInt("duration", duration);
        tag.putBoolean("hasShield", hasShield);
        tag.putBoolean("shieldUsed", shieldUsed);
        tag.putBoolean("inDarkness", inDarkness);
        tag.putInt("speedEffectCooldown", speedEffectCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        cooldown = tag.getInt("cooldown");
        duration = tag.getInt("duration");
        hasShield = tag.getBoolean("hasShield");
        shieldUsed = tag.getBoolean("shieldUsed");
        inDarkness = tag.getBoolean("inDarkness");
        speedEffectCooldown = tag.getInt("speedEffectCooldown");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}