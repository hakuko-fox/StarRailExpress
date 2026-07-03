package org.agmas.noellesroles.game.roles.neutral.reincarnator;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 轮回者（中立·独立结算）。
 * <p>
 * 胜利：被其他玩家以"足够多种不同方式"杀死（数量按人数分档）。<br>
 * 反制：若连续两次以"相同方式"被杀，则真正死亡。<br>
 * 自残 / 无凶手环境死不计入进度，仅被送回房间。
 * <p>
 * 通过覆写 {@link #allowDeath} 把"非真死"的死亡转化为"伪死亡 + 送回房间"。
 */
public class ReincarnatorRole extends NormalRole {

    public ReincarnatorRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean allowDeath(Player victim, @Nullable Player killer, ResourceLocation deathReason, boolean spawnBody) {
        if (!(victim instanceof ServerPlayer sp)) {
            return true;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(victim, this)) {
            return true;
        }
        if (!ReincarnatorPlayerComponent.KEY.isProvidedBy(sp)) {
            return true;
        }
        ReincarnatorPlayerComponent comp = ReincarnatorPlayerComponent.KEY.get(sp);
        long now = victim.level().getGameTime();

        // 复活宽限期内：无视死亡，直接送回（不记录）。
        if (now < comp.graceUntil) {
            comp.pendingSelfInflicted = false;
            ReincarnatorManager.bounceToRoom(sp);
            return false;
        }

        // 自残 / 无凶手环境死：不记录、不致死，仅送回房间。
        boolean selfInflicted = comp.pendingSelfInflicted || killer == null || killer == victim;
        if (selfInflicted) {
            comp.pendingSelfInflicted = false;
            ReincarnatorManager.bounceToRoom(sp);
            return false;
        }

        // 他杀且与上一次死因相同 —— 连续相同方式，真正死亡。
        if (comp.lastDeathCause != null && comp.lastDeathCause.equals(deathReason)) {
            comp.trueDead = true;
            comp.sync();
            ReincarnatorManager.announceTrueDeath(sp, deathReason);
            return true; // 放行原版死亡流程
        }

        // 他杀且不同 —— 记录死因（伪死亡 + 送回房间）。
        boolean isNew = comp.addCause(deathReason);
        ReincarnatorManager.bounceToRoom(sp);
        if (isNew) {
            ReincarnatorManager.onNewCause(sp, deathReason);
            comp.checkWinCondition();
        }
        return false;
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        if (victim instanceof ServerPlayer sp && ReincarnatorPlayerComponent.KEY.isProvidedBy(sp)) {
            ReincarnatorPlayerComponent comp = ReincarnatorPlayerComponent.KEY.get(sp);
            comp.trueDead = true;
            comp.sync();
        }
    }
}
