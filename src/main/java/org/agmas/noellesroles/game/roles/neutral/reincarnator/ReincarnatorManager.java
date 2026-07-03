package org.agmas.noellesroles.game.roles.neutral.reincarnator;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 轮回者服务端辅助：送回房间（伪死亡复活）、全局音效与播报。
 */
public final class ReincarnatorManager {

    private ReincarnatorManager() {
    }

    /**
     * 将轮回者送回其房间并满血复位（伪死亡），施加短暂无敌宽限，并对全场播放复活音效。
     */
    public static void bounceToRoom(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        Vec3 pos = GameUtils.getSpawnPos(areas, GameUtils.roomToPlayer.getOrDefault(sp.getUUID(), 1));

        sp.setGameMode(GameType.ADVENTURE);
        if (pos != null) {
            sp.teleportTo(level, pos.x(), pos.y() + 1, pos.z(), sp.getYRot(), sp.getXRot());
        }
        sp.setHealth(sp.getMaxHealth());
        sp.setRemainingFireTicks(0);
        sp.getFoodData().setFoodLevel(20);

        ReincarnatorPlayerComponent comp = ReincarnatorPlayerComponent.KEY.get(sp);
        comp.graceUntil = level.getGameTime() + ReincarnatorPlayerComponent.GRACE_TICKS;
        comp.trueDead = false;
        comp.sync();

        // 复活宽限期内的高抗性无敌（隐藏图标/粒子）
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                ReincarnatorPlayerComponent.GRACE_TICKS, 4, false, false, false));

        playGlobalSound(level, SoundEvents.TOTEM_USE, 0.7F, 1.0F);
    }

    /** 收集到新死因后的反馈：阶段增益刷新、播报进度。 */
    public static void onNewCause(ServerPlayer sp, ResourceLocation cause) {
        ReincarnatorPlayerComponent comp = ReincarnatorPlayerComponent.KEY.get(sp);
        comp.applyStageBuffs(sp);

        Component msg = Component.translatable(
                "message.noellesroles.reincarnator.new_cause",
                Component.translatable(causeNameKey(cause)),
                comp.deathCausesSeen.size(), comp.requiredCauses)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        sp.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    /** 连续以相同方式死亡——真正死亡的全场播报与音效。 */
    public static void announceTrueDeath(ServerPlayer sp, ResourceLocation cause) {
        ServerLevel level = sp.serverLevel();
        Component msg = Component.translatable(
                "message.noellesroles.reincarnator.true_death",
                sp.getName(), Component.translatable(causeNameKey(cause)))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        sp.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        playGlobalSound(level, SoundEvents.WITHER_DEATH, 0.8F, 1.0F);
    }

    public static String causeNameKey(ResourceLocation cause) {
        return "death_reason." + cause.getNamespace() + "." + cause.getPath();
    }

    /** 对全场每位玩家在其位置播放音效，确保所有人都能听到。 */
    public static void playGlobalSound(ServerLevel level, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.MASTER, volume, pitch);
        }
    }
}
