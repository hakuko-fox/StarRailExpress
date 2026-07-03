package org.agmas.noellesroles.game.roles.neutral.reincarnator;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.LinkedHashSet;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 轮回者（中立·独立结算）的每玩家状态：
 * <ul>
 * <li>{@link #deathCausesSeen} —— 已收集的不同死因（仅"被他人杀死"且不同才计入）。</li>
 * <li>{@link #lastDeathCause} —— 上一次记录的死因，用于"连续相同方式 = 真死"判定。</li>
 * <li>{@link #requiredCauses} —— 胜利所需的不同死因数量，按人数分档，服务端 tick 重算。</li>
 * <li>{@link #graceUntil} —— 复活后的短暂宽限（无敌且不记录）窗口的结束游戏刻。</li>
 * </ul>
 */
public class ReincarnatorPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ReincarnatorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("reincarnator"), ReincarnatorPlayerComponent.class);

    /** 复活宽限时长（刻）：期间无敌、死亡不记录，避免同一击连续触发。 */
    public static final int GRACE_TICKS = 60;

    private final Player player;

    public Set<ResourceLocation> deathCausesSeen = new LinkedHashSet<>();
    public ResourceLocation lastDeathCause = null;
    public int requiredCauses = 3;
    public boolean trueDead = false;
    /** 仅供经由 killPlayer 流程的自杀标记使用；技能"舍身"直接送回房间不依赖它。 */
    public boolean pendingSelfInflicted = false;
    public long graceUntil = 0L;

    public ReincarnatorPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        deathCausesSeen.clear();
        lastDeathCause = null;
        requiredCauses = 3;
        trueDead = false;
        pendingSelfInflicted = false;
        graceUntil = 0L;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    /** 当前阶段 = 已收集死因数。 */
    public int getStage() {
        return deathCausesSeen.size();
    }

    /** 诱导技能解锁所需阶段（接近胜利才解锁，故较难）。 */
    public int getLureUnlockStage() {
        return Math.max(2, requiredCauses - 1);
    }

    public boolean isLureUnlocked() {
        return getStage() >= getLureUnlockStage();
    }

    /**
     * 记录一次死因。无论是否新增都会把 {@link #lastDeathCause} 设为该死因（用于连续判定）。
     *
     * @return 是否为本局首次出现的新死因（用于推进阶段 / 播报）。
     */
    public boolean addCause(ResourceLocation reason) {
        boolean isNew = deathCausesSeen.add(reason);
        lastDeathCause = reason;
        sync();
        return isNew;
    }

    public void checkWinCondition() {
        if (deathCausesSeen.size() >= requiredCauses && requiredCauses > 0
                && player.level() instanceof ServerLevel serverLevel) {
            RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM,
                    ModRoles.REINCARNATOR_ID.getPath(), OptionalInt.of(ModRoles.REINCARNATOR.color()));
        }
    }

    /** 按当前在场人数计算所需死因数量（分档，受配置上下限约束）。 */
    public int computeRequired(int playerCount) {
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        int min = Math.max(1, cfg.reincarnatorMinCauses);
        int max = Math.max(min, cfg.reincarnatorMaxCauses);
        int required = min;
        if (playerCount >= 7) required = min + 1;
        if (playerCount >= 10) required = min + 2;
        if (playerCount >= 13) required = min + 3;
        if (playerCount >= 16) required = min + 4;
        return Math.min(max, Math.max(min, required));
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.REINCARNATOR)) return;
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) return;

        int newRequired = computeRequired(gameWorld.getPlayerCount());
        if (requiredCauses != newRequired) {
            requiredCauses = newRequired;
            sync();
        }

        // 每 2 秒刷新一次阶段性增益
        if (player.level().getGameTime() % 40L == 0L) {
            applyStageBuffs(sp);
        }
    }

    /** 阶段性增益：随收集到的死因数提升，越接近胜利越强。 */
    public void applyStageBuffs(ServerPlayer sp) {
        int stage = getStage();
        if (stage <= 0) return;
        int dur = 60; // 3 秒，持续刷新
        boolean ambient = false, particles = false, icon = false;
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur,
                Math.min(stage - 1, 1), ambient, particles, icon));
        if (stage >= 2) {
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur,
                    Math.min((stage - 1) / 2, 2), ambient, particles, icon));
        }
        if (stage >= 3) {
            sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, dur,
                    Math.min(stage - 3, 1), ambient, particles, icon));
        }
        if (stage >= 4) {
            sp.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, dur, 0, ambient, particles, icon));
        }
    }

    /** 兜底胜利检查（由 CustomWinnerClass 统一轮询调用）。 */
    public static boolean checkVictory(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer sp : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) continue;
            if (!gameWorld.isRole(sp, ModRoles.REINCARNATOR)) continue;
            if (!KEY.isProvidedBy(sp)) continue;
            ReincarnatorPlayerComponent comp = KEY.get(sp);
            if (comp.deathCausesSeen.size() >= comp.requiredCauses && comp.requiredCauses > 0) {
                RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM,
                        ModRoles.REINCARNATOR_ID.getPath(), OptionalInt.of(ModRoles.REINCARNATOR.color()));
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("RequiredCauses", requiredCauses);
        tag.putBoolean("TrueDead", trueDead);
        tag.putLong("GraceUntil", graceUntil);
        if (lastDeathCause != null) {
            tag.putString("LastCause", lastDeathCause.toString());
        }
        ListTag list = new ListTag();
        for (ResourceLocation cause : deathCausesSeen) {
            list.add(StringTag.valueOf(cause.toString()));
        }
        tag.put("Causes", list);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        requiredCauses = tag.getInt("RequiredCauses");
        trueDead = tag.getBoolean("TrueDead");
        graceUntil = tag.getLong("GraceUntil");
        lastDeathCause = tag.contains("LastCause", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("LastCause")) : null;
        deathCausesSeen.clear();
        if (tag.contains("Causes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Causes", Tag.TAG_STRING);
            for (Tag t : list) {
                ResourceLocation parsed = ResourceLocation.tryParse(t.getAsString());
                if (parsed != null) deathCausesSeen.add(parsed);
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
