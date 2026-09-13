package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.game.GameUtils;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

public class HalicRole extends NormalRole {
    public HalicRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public ResourceLocation getNormalSkin(Player player, boolean isSlim) {
        return SRE.id("textures/entity/custom_psycho/halic.png");
    }

    public static void clearDecoys(Player player) {
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

    public static boolean createDecoy(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        int balance = MoneyUtils.getBalance(sp);
        int cost = 10;
        if (balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.halic.not_enough_money", cost),
                    true);
            return false;
        }

        ServerLevel level = sp.serverLevel();
        PuppeteerBodyEntity decoy = new PuppeteerBodyEntity(ModEntities.PUPPETEER_BODY, level);
        decoy.setPos(sp.getX(), sp.getY(), sp.getZ());
        decoy.setYRot(sp.getYRot());
        decoy.setXRot(sp.getXRot());
        decoy.setOwner(sp);
        decoy.setHalicDecoy(true);
        decoy.setPersistenceRequired();
        if (!level.addFreshEntity(decoy)) return false;
        MoneyUtils.addToBalance(sp, -cost);

        playSoundToPlayers(level, sp.getX(), sp.getY(), sp.getZ(), 5.0, NRSounds.HALIC_HELLO);

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.halic.decoy_created"),
                true);
        return true;
    }

    public static boolean electrocute(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        int cost = 50;
        int balance = MoneyUtils.getBalance(sp);
        if (balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.halic.not_enough_money", cost),
                    true);
            return false;
        }
        MoneyUtils.addToBalance(sp, -cost);

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

        var soundRecipients = new java.util.HashSet<ServerPlayer>();
        addSoundRecipients(soundRecipients, sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(), range);
        for (var decoy : decoys) {
            addSoundRecipients(soundRecipients, sp.serverLevel(), decoy.getX(), decoy.getY(), decoy.getZ(), range);
        }
        for (ServerPlayer recipient : soundRecipients) {
            recipient.playNotifySound(NRSounds.HALIC_ARRR, SoundSource.PLAYERS, 1.0F, 1.0F);
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

    private static void playSoundToPlayers(ServerLevel level, double x, double y, double z,
            double range, net.minecraft.sounds.SoundEvent sound) {
        var recipients = new java.util.HashSet<ServerPlayer>();
        addSoundRecipients(recipients, level, x, y, z, range);
        for (ServerPlayer recipient : recipients) {
            recipient.playNotifySound(sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static void addSoundRecipients(java.util.Set<ServerPlayer> recipients, ServerLevel level,
            double x, double y, double z, double range) {
        double rangeSquared = range * range;
        for (ServerPlayer recipient : level.players()) {
            if (recipient.distanceToSqr(x, y, z) <= rangeSquared) {
                recipients.add(recipient);
            }
        }
    }

    @Override
    public void onRemove(net.minecraft.server.level.ServerPlayer player) {
        clearDecoys(player);
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.HALIC,
                RoleSkill.skill(SRE.id("halic_decoy"), "skill.noellesroles.halic.decoy", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return false;
                    return HalicRole.createDecoy(player);
                }).cooldownSeconds(10).showOnHud(true).build(),
                RoleSkill.skill(SRE.id("halic_electrocute"), "skill.noellesroles.halic.sanity", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return false;
                    return HalicRole.electrocute(player);
                }).shifted(true).charges(1).showOnHud(true).build());
    }
}
