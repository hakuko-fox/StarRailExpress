package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;

public class KanaRole extends NormalRole {
    public KanaRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
    }

    @Override
    public boolean onUseGun(Player player) {
        return !identifier().equals(ModRoles.KANA_ID)
                && !VtuberRoleRuntime.isWeaponBlocked(player);
    }

    @Override
    public boolean onUseKnife(Player player) {
        return !VtuberRoleRuntime.isWeaponBlocked(player)
                && KanaRole.canUseKanaKnife(player);
    }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return identifier().equals(ModRoles.KANA_ID) && VtuberRoleSupport.isWeapon(item.getItem())
                ? TrueFalseResult.FALSE : TrueFalseResult.PASS;
    }

    @Override
    public java.util.List<ShopEntry> getShopEntries() {
        return VtuberRoleSupport.killerShopWithCrowbar();
    }

    public static boolean canUseKanaKnife(Player player) {
        ItemStack knife = player.getMainHandItem();
        var tag = knife.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        boolean kana = SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.KANA);
        if (!kana) {
            return !tag.hasUUID("KanaKnifeGrant");
        }
        UUID grant = RoleData.getOptional(VtuberRoleData.class, player).map(VtuberRoleData::getKnifeGrant).orElse(null);
        return knife.is(TMMItems.KNIFE) && grant != null && tag.hasUUID("KanaKnifeGrant")
                && grant.equals(tag.getUUID("KanaKnifeGrant"));
    }

    public static void consumeKanaKnife(ServerPlayer player) {
        if (SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.KANA)) {
            player.getMainHandItem().shrink(1);
            RoleData.getNullable(VtuberRoleData.class, player).setKnifeGrant(null);
        }
    }

    public static void finishKanaKnifeAttack(ServerPlayer player) {
        handleKanaKill(player);
    }

    public static void selectKanaTarget(ServerPlayer caster, ServerPlayer target, SREGameWorldComponent game) {
        long now = GameUtils.getTicksFromGameStart(caster.level());
        if (!GameUtils.isPlayerAliveAndSurvival(caster)
                    || RoleData.getNullable(VtuberRoleData.class, caster) == null) return;
        if (target == null || target == caster || target.level() != caster.level() || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        long cooldownUntil = RoleData.getNullable(VtuberRoleData.class, caster).getMenuCooldownUntil();
        if (now < cooldownUntil) {
            long remainingSeconds = (cooldownUntil - now + 19L) / 20L;
            caster.displayClientMessage(Component.translatable(
                    "message.sre.skill.cooldown", remainingSeconds), true);
            return;
        }
        target.removeEffect(ModEffects.VOICE_HELIUM);
        target.removeEffect(ModEffects.HEAVY_METAL_VOICE);
        target.addEffect(new MobEffectInstance(caster.getRandom().nextBoolean()
                ? ModEffects.VOICE_HELIUM : ModEffects.HEAVY_METAL_VOICE,
                Integer.MAX_VALUE, 0, false, false, true));
        RoleData.getNullable(VtuberRoleData.class, caster).kanaAffected
                .add(target.getUUID());

        RoleData.getNullable(VtuberRoleData.class, caster).setMenuCooldownUntil(now + 20L * 15L);
        updateKanaPartyMode(caster, game);
    }

    public static void updateKanaPartyMode(ServerPlayer caster, SREGameWorldComponent game) {
        if (!game.isRole(caster, ModRoles.KANA) || RoleData.getNullable(VtuberRoleData.class, caster).kanaParty) {
            return;
        }
        int initial = RoleData.getNullable(VtuberRoleData.class, caster).kanaInitialPlayers;
        Set<UUID> affectedByCaster = RoleData.getNullable(VtuberRoleData.class, caster).kanaAffected;
        int alive = (int) caster.serverLevel().players().stream()
                .filter(GameUtils::isPlayerAliveAndSurvival).count();
        long aliveAffected = caster.serverLevel().players().stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(player -> player == caster || affectedByCaster.contains(player.getUUID()))
                .count();
        boolean oneThird = aliveAffected >= (initial + 2) / 3;
        boolean lowAliveAllAffected = alive * 3 < initial && caster.serverLevel().players().stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .allMatch(player -> player == caster || affectedByCaster.contains(player.getUUID()));
        if (oneThird || lowAliveAllAffected) {
            RoleData.getNullable(VtuberRoleData.class, caster).kanaParty = true;
            SREItemUtils.clearItem(caster, TMMItems.KNIFE);
            UUID grant = UUID.randomUUID();
            ItemStack knife = TMMItems.KNIFE.getDefaultInstance();
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putUUID("KanaKnifeGrant", grant);
            knife.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            knife.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.translatable("item.noellesroles.kana_single_use_knife"));
            RoleData.getNullable(VtuberRoleData.class, caster).setKnifeGrant(grant);
            // Do not lose the earned knife when the inventory is full.
            if (!caster.addItem(knife)) {
                caster.drop(caster.getMainHandItem().copy(), false);
                caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, knife);
            }
            caster.displayClientMessage(Component.translatable("message.noellesroles.kana.party_started"), false);
        }
    }

    public static void handleKanaKill(Player killer) {
        if (!(killer instanceof ServerPlayer kana) || !RoleData.getOptional(VtuberRoleData.class, kana).map(d -> d.kanaParty).orElse(false)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(kana.level());
        if (!game.isRole(kana, ModRoles.KANA)) {
            return;
        }
        Set<UUID> affectedByKana = Set.copyOf(RoleData.getNullable(VtuberRoleData.class, kana).kanaAffected);
        RoleData.getNullable(VtuberRoleData.class, kana).kanaAffected.clear();
        for (UUID affectedUuid : affectedByKana == null ? Set.<UUID>of() : Set.copyOf(affectedByKana)) {
            ServerPlayer affected = kana.getServer().getPlayerList().getPlayer(affectedUuid);
            boolean affectedByAnotherKana = kana.getServer().getPlayerList().getPlayers().stream()
                    .map(p -> RoleData.getNullable(VtuberRoleData.class, p))
                    .anyMatch(d -> d != null && d.kanaAffected.contains(affectedUuid));
            if (affected != null && !affectedByAnotherKana) {
                affected.removeEffect(ModEffects.VOICE_HELIUM);
                affected.removeEffect(ModEffects.HEAVY_METAL_VOICE);
            }
        }
        RoleData.getNullable(VtuberRoleData.class, kana).kanaParty = false;
        RoleData.getNullable(VtuberRoleData.class, kana).setKnifeGrant(null);
        SREItemUtils.clearItem(kana, TMMItems.KNIFE);
        kana.displayClientMessage(Component.translatable("message.noellesroles.kana.party_finished"), false);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        updateKanaPartyMode(player, game);
    }

}
