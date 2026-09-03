package org.agmas.noellesroles.game.roles.vtuber;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.Scheduler;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.GlobalPos;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

/** Shared round runtime for the VTuber roster's server-only passives and skills. */
public final class VtuberRoleRuntime {
    private static final Map<UUID, Long> WEAPON_BLOCKED_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ALLIANCE_ROLL = new HashMap<>();
    private static final Map<UUID, Long> YUZU_SLEEP_DEADLINE = new HashMap<>();
    private static final Set<UUID> YUZU_SLEEP_WEAPON_BLOCKED = new HashSet<>();
    private static final Map<UUID, Integer> PASSERBY_TICKS = new HashMap<>();
    private static final Map<UUID, Long> AYERS_NEXT_SWITCH = new HashMap<>();
    private static final Map<UUID, Boolean> AYERS_FAST_MODE = new HashMap<>();
    private static final Map<UUID, ForcedMovement> FORCED_MOVEMENTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_DEATH_TICK = new HashMap<>();
    private static final Map<UUID, Deque<PlayerSnapshot>> PLAYER_SNAPSHOTS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> KANA_AFFECTED = new HashMap<>();
    private static final Map<UUID, UUID> BAIYU_MARKED_TARGETS = new HashMap<>();
    private static final Set<UUID> KANA_PARTY = new HashSet<>();
    private static final Map<UUID, Integer> KANA_INITIAL_PLAYERS = new HashMap<>();
    private static final Map<UUID, Long> KANA_MENU_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> MEOWLEN_MENU_COOLDOWN = new HashMap<>();
    private static final Map<GlobalPos, UUID> FOOD_TRAPS = new HashMap<>();
    private static final Map<UUID, Long> BLOOD_FOX_LAST_CONSUME = new HashMap<>();
    private static final Map<UUID, Long> HOSHIZORA_WEAPON_BLOCKED_UNTIL = new HashMap<>();
    private static final Set<UUID> SYMBIOSIS_GUARD = new HashSet<>();
    private static boolean registered;

    private record ForcedMovement(UUID source, boolean toward, long expiresAt) {
    }

    private record PlayerSnapshot(long tick, Vec3 position, float yRot, float xRot, float health,
            List<MobEffectInstance> effects, List<ItemStack> inventory, int selectedSlot,
            int balance, boolean alive) {
    }

    private VtuberRoleRuntime() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(VtuberRoleRuntime::serverTick);
        OnGameTrueStarted.EVENT.register(level -> resetRound(level.getServer()));
        OnGameEnd.EVENT.register((level, game) -> resetRound(level.getServer()));
        OnPlayerDeath.EVENT.register((victim, reason) -> handleDeath(victim));
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            handleDeath(victim);
            handleKanaKill(killer);
        });
        AllowPlayerDeath.EVENT.register((victim, reason) -> allowAnimalFormDeath(victim));
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> allowAnimalFormDeath(victim));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                    && player.getMainHandItem().isEmpty()) {
                GlobalPos trapPos = GlobalPos.of(level.dimension(), hit.getBlockPos());
                UUID owner = FOOD_TRAPS.get(trapPos);
                if (owner != null) {
                    Scheduler.schedule(() -> tagTakenTrayItem(serverPlayer, trapPos, owner), 1);
                }
            }
            return InteractionResult.PASS;
        });
    }

    public static boolean isWeaponBlocked(Player player) {
        if (player == null || player.level() == null) {
            return false;
        }
        return WEAPON_BLOCKED_UNTIL.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime()
                || YUZU_SLEEP_WEAPON_BLOCKED.contains(player.getUUID())
                || isAnimalDisguised(player);
    }

    public static boolean canHoshizoraUseWeapon(Player player) {
        return player != null && player.level() != null
                && player.level().getGameTime() >= HOSHIZORA_WEAPON_BLOCKED_UNTIL
                        .getOrDefault(player.getUUID(), 0L);
    }

    public static boolean useAmiRepel(ServerPlayer caster) {
        if (!deduct(caster, 100)) {
            return false;
        }
        applyForcedMovement(caster, false, 20 * 3);
        return true;
    }

    public static boolean useTinalisAttract(ServerPlayer caster) {
        if (!deduct(caster, 100)) {
            return false;
        }
        applyForcedMovement(caster, true, 20 * 5);
        return true;
    }

    public static boolean useYuzuAgility(ServerPlayer player) {
        if (!deduct(player, 100)) {
            return false;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 7, 1, false, false, true));
        return true;
    }

    public static boolean usePairSkill(ServerPlayer caster) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(caster.level());
        var counterpartRole = game.isRole(caster, ModRoles.LUNA) ? ModRoles.YORU : ModRoles.LUNA;
        ServerPlayer counterpart = caster.serverLevel().players().stream()
                .filter(player -> GameUtils.isPlayerAliveAndSurvival(player) && game.isRole(player, counterpartRole))
                .findFirst().orElse(null);
        if (counterpart == null) {
            caster.displayClientMessage(Component.translatable("message.noellesroles.luna_yoru.missing_pair"), true);
            return false;
        }
        if (!deduct(caster, 200)) {
            return false;
        }
        if (caster.distanceToSqr(counterpart) > 9.0D) {
            Vec3 side = caster.getLookAngle().cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize().scale(0.8D);
            counterpart.teleportTo(caster.serverLevel(), caster.getX() + side.x, caster.getY(),
                    caster.getZ() + side.z, Set.of(), counterpart.getYRot(), counterpart.getXRot());
        } else {
            GameUtils.teleportBackToRoom(caster);
            GameUtils.teleportBackToRoom(counterpart);
        }
        return true;
    }

    public static boolean toggleBloodFox(ServerPlayer player) {
        VtuberRolePlayerComponent component = VtuberRolePlayerComponent.KEY.get(player);
        if (component.getDisguise() == VtuberRolePlayerComponent.BLOOD_FOX) {
            leaveAnimalForm(player);
            return true;
        }
        component.setDisguise(VtuberRolePlayerComponent.BLOOD_FOX);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1,
                false, false, true));
        return true;
    }

    public static boolean toggleYozoraCat(ServerPlayer player) {
        VtuberRolePlayerComponent component = VtuberRolePlayerComponent.KEY.get(player);
        if (component.getDisguise() == VtuberRolePlayerComponent.YOZORA_CAT) {
            leaveAnimalForm(player);
            return true;
        }
        component.setDisguise(VtuberRolePlayerComponent.YOZORA_CAT);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 2,
                false, false, true));
        return true;
    }

    public static boolean isAnimalDisguised(Player player) {
        return player != null && VtuberRolePlayerComponent.KEY.maybeGet(player)
                .map(VtuberRolePlayerComponent::isDisguised).orElse(false);
    }

    public static boolean useRewind(ServerPlayer caster, int seconds) {
        return useRewind(caster, seconds, 200);
    }

    public static boolean useRewind(ServerPlayer caster, int seconds, int cost) {
        if (!GameUtils.isPlayerAliveAndSurvival(caster)) {
            return false;
        }
        var casterShop = SREPlayerShopComponent.KEY.get(caster);
        if (casterShop.balance < cost) {
            caster.displayClientMessage(Component.translatable(
                    "message.noellesroles.vtuber.not_enough_coins", cost), true);
            return false;
        }
        long targetTick = caster.level().getGameTime() - 20L * seconds;
        int restored = 0;
        for (ServerPlayer player : caster.serverLevel().players()) {
            PlayerSnapshot snapshot = findSnapshot(player.getUUID(), targetTick);
            if (snapshot == null) {
                continue;
            }
            restoreSnapshot(player, snapshot);
            restored++;
        }
        if (restored == 0) {
            return false;
        }
        SREPlayerShopComponent.KEY.get(caster).addToBalance(-cost);
        caster.displayClientMessage(Component.translatable(
                "message.noellesroles.time_rewind.restored", seconds, restored), true);
        return restored > 0;
    }

    public static boolean useYoujinTrap(ServerPlayer player) {
        return useFoodTrap(player);
    }

    public static boolean useAmiTrap(ServerPlayer player) {
        return useFoodTrap(player);
    }

    private static boolean useFoodTrap(ServerPlayer player) {
        if (!(player.pick(5.0D, 0.0F, false) instanceof BlockHitResult hit)
                || !(player.level().getBlockEntity(hit.getBlockPos()) instanceof PlateTrayBlockEntity)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.youjin.invalid_tray"), true);
            return false;
        }
        GlobalPos pos = GlobalPos.of(player.level().dimension(), hit.getBlockPos());
        if (FOOD_TRAPS.containsKey(pos) || !deduct(player, 10)) {
            return false;
        }
        FOOD_TRAPS.put(pos, player.getUUID());
        player.displayClientMessage(Component.translatable("message.noellesroles.youjin.trap_set"), true);
        return true;
    }

    public static boolean useBaiyuExamine(ServerPlayer player) {
        var hit = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof ServerPlayer target
                        && target != player
                        && GameUtils.isPlayerAliveAndSurvival(target), 5.0F);
        if (!(hit instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof ServerPlayer target)) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.baiyu.no_target"), true);
            return false;
        }
        BAIYU_MARKED_TARGETS.put(player.getUUID(), target.getUUID());
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.baiyu.marked", target.getName()), true);
        return true;
    }

    private static void displayBaiyuDeathReason(ServerPlayer player, PlayerBodyEntity body) {
        String reason = body.getDeathReason();
        Component reasonText = reason == null || reason.isBlank()
                ? Component.translatable("message.death_reason.null")
                : Component.translatable("death_reason." + reason.replace(':', '.'));
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.baiyu.result", reasonText), false);
    }

    public static void onConsume(Player consumer, ItemStack stack) {
        if (!(consumer instanceof ServerPlayer player)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game.isRole(player, ModRoles.BLOOD_FOX)) {
            BLOOD_FOX_LAST_CONSUME.put(player.getUUID(), player.level().getGameTime());
        }
        String ownerText = stack.get(SREDataComponentTypes.OWNER);
        if (ownerText == null || !stack.getOrDefault(SREDataComponentTypes.TRAY_ITEM, false)) {
            return;
        }
        try {
            UUID ownerUuid = UUID.fromString(ownerText);
            ServerPlayer owner = player.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner == null || !GameUtils.isPlayerAliveAndSurvival(owner)
                    || (!SREGameWorldComponent.KEY.get(owner.level()).isRole(owner, ModRoles.YOUJIN)
                    && !SREGameWorldComponent.KEY.get(owner.level()).isRole(owner, ModRoles.AMI))) {
                return;
            }
            int duration = 20 * 5;
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0,
                    false, false, true));
            SREPlayerShopComponent.KEY.get(owner).addToBalance(20);
            owner.displayClientMessage(Component.translatable("message.noellesroles.youjin.trap_triggered",
                    player.getDisplayName()), true);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void tagTakenTrayItem(ServerPlayer player, GlobalPos trapPos, UUID owner) {
        if (!FOOD_TRAPS.containsKey(trapPos)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !stack.getOrDefault(SREDataComponentTypes.TRAY_ITEM, false)) {
            return;
        }
        stack.set(SREDataComponentTypes.OWNER, owner.toString());
        FOOD_TRAPS.remove(trapPos);
    }

    public static void handleMenuSelection(ServerPlayer caster, UUID firstUuid, UUID secondUuid) {
        if (!GameUtils.isPlayerAliveAndSurvival(caster)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(caster.level());
        ServerPlayer first = caster.getServer().getPlayerList().getPlayer(firstUuid);
        ServerPlayer second = secondUuid == null ? null : caster.getServer().getPlayerList().getPlayer(secondUuid);
        if (game.isRole(caster, ModRoles.KANA)) {
            selectKanaTarget(caster, first, game);
        } else if (game.isRole(caster, ModRoles.MAOLUN)) {
            selectMeowlenTargets(caster, first, second);
        }
    }

    private static void selectKanaTarget(ServerPlayer caster, ServerPlayer target, SREGameWorldComponent game) {
        long now = caster.level().getGameTime();
        if (target == null || target == caster || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        long cooldownUntil = KANA_MENU_COOLDOWN.getOrDefault(caster.getUUID(), 0L);
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
        KANA_AFFECTED.computeIfAbsent(caster.getUUID(), ignored -> new HashSet<>())
                .add(target.getUUID());
        KANA_MENU_COOLDOWN.put(caster.getUUID(), now + 20L * 15L);
        updateKanaPartyMode(caster, game);
    }

    private static void updateKanaPartyMode(ServerPlayer caster, SREGameWorldComponent game) {
        if (!game.isRole(caster, ModRoles.KANA) || KANA_PARTY.contains(caster.getUUID())) {
            return;
        }
        int initial = KANA_INITIAL_PLAYERS.getOrDefault(caster.getUUID(), game.getPlayerCount());
        Set<UUID> affectedByCaster = KANA_AFFECTED.getOrDefault(caster.getUUID(), Set.of());
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
        if ((oneThird || lowAliveAllAffected) && KANA_PARTY.add(caster.getUUID())) {
            if (!SREItemUtils.hasItem(caster, TMMItems.KNIFE)) {
                caster.addItem(TMMItems.KNIFE.getDefaultInstance());
            }
            caster.displayClientMessage(Component.translatable("message.noellesroles.kana.party_started"), false);
        }
    }

    private static void selectMeowlenTargets(ServerPlayer caster, ServerPlayer first, ServerPlayer second) {
        long now = caster.level().getGameTime();
        if (caster.hasEffect(ModEffects.SAFE_TIME)
                || io.wifi.starrailexpress.api.RoleSkill.blockForSpectator(caster)) {
            caster.displayClientMessage(Component.translatable("message.tip.cant_use_skill"), true);
            return;
        }
        long cooldownUntil = MEOWLEN_MENU_COOLDOWN.getOrDefault(caster.getUUID(), 0L);
        if (now < cooldownUntil) {
            long remainingSeconds = (cooldownUntil - now + 19L) / 20L;
            caster.displayClientMessage(Component.translatable(
                    "message.sre.skill.cooldown", remainingSeconds), true);
            return;
        }
        if (first == null || second == null || first == second || first == caster || second == caster
                || !GameUtils.isPlayerAliveAndSurvival(first) || !GameUtils.isPlayerAliveAndSurvival(second)) {
            return;
        }
        if (org.agmas.noellesroles.init.ModRolesInitialEventRegister
                .startMaolunSelection(caster, first, second)) {
            MEOWLEN_MENU_COOLDOWN.put(caster.getUUID(), now + 20L * 70L);
        }
    }

    private static void handleKanaKill(Player killer) {
        if (!(killer instanceof ServerPlayer kana) || !KANA_PARTY.contains(kana.getUUID())) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(kana.level());
        if (!game.isRole(kana, ModRoles.KANA)) {
            return;
        }
        Set<UUID> affectedByKana = KANA_AFFECTED.remove(kana.getUUID());
        for (UUID affectedUuid : affectedByKana == null ? Set.<UUID>of() : Set.copyOf(affectedByKana)) {
            ServerPlayer affected = kana.getServer().getPlayerList().getPlayer(affectedUuid);
            boolean affectedByAnotherKana = KANA_AFFECTED.values().stream()
                    .anyMatch(affectedPlayers -> affectedPlayers.contains(affectedUuid));
            if (affected != null && !affectedByAnotherKana) {
                affected.removeEffect(ModEffects.VOICE_HELIUM);
                affected.removeEffect(ModEffects.HEAVY_METAL_VOICE);
            }
        }
        KANA_PARTY.remove(kana.getUUID());
        SREItemUtils.clearItem(kana, TMMItems.KNIFE);
        kana.displayClientMessage(Component.translatable("message.noellesroles.kana.party_finished"), false);
    }

    private static boolean deduct(ServerPlayer player, int cost) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        var shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < cost) {
            player.displayClientMessage(Component.translatable("message.noellesroles.vtuber.not_enough_coins", cost), true);
            return false;
        }
        shop.addToBalance(-cost);
        return true;
    }

    private static void applyForcedMovement(ServerPlayer caster, boolean toward, int ticks) {
        long expiresAt = caster.level().getGameTime() + ticks;
        for (ServerPlayer target : caster.serverLevel().players()) {
            if (target != caster && GameUtils.isPlayerAliveAndSurvival(target)) {
                FORCED_MOVEMENTS.put(target.getUUID(), new ForcedMovement(caster.getUUID(), toward, expiresAt));
            }
        }
    }

    private static void serverTick(MinecraftServer server) {
        org.agmas.noellesroles.init.ModRolesInitialEventRegister.tickMaolunChallenges(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            if (!game.isRunning() || game.getRole(player) == null) {
                continue;
            }
            long now = player.level().getGameTime();
            captureSnapshot(player, now);
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            tickNineOneTaskConcealment(player, game);
            tickForcedMovement(server, player, now);
            tickUncontrolledAlliance(player, game, now);
            tickYuzuSleep(player, game, now);
            tickAyers(player, game, now);
            updateKanaPartyMode(player, game);
            tickHoshizora(player, game, now);
            tickPasserby(player, game);
            tickBloodFox(player, game);
            tickNocturnalAndStableSan(player, game);
        }
    }

    private static void tickNineOneTaskConcealment(ServerPlayer player, SREGameWorldComponent game) {
        if (game.isRole(player, ModRoles.SEPTEMBER_ONE)
                && !SREPlayerTaskComponent.KEY.get(player).tasks.isEmpty()) {
            player.addEffect(new MobEffectInstance(ModEffects.NINE_ONE_TASK_CONCEALMENT,
                    10, 0, false, false, false));
        } else {
            player.removeEffect(ModEffects.NINE_ONE_TASK_CONCEALMENT);
        }
    }

    private static void tickHoshizora(ServerPlayer player, SREGameWorldComponent game, long now) {
        if (!game.isRole(player, ModRoles.HOSHIZORA)) {
            return;
        }
        boolean nearby = player.serverLevel().players().stream()
                .anyMatch(other -> other != player && GameUtils.isPlayerAliveAndSurvival(other)
                        && other.distanceToSqr(player) <= 7.0D * 7.0D);
        if (nearby) {
            HOSHIZORA_WEAPON_BLOCKED_UNTIL.put(player.getUUID(), now + 20L);
        }
    }

    private static void tickBloodFox(ServerPlayer player, SREGameWorldComponent game) {
        if (!game.isRole(player, ModRoles.BLOOD_FOX)) {
            return;
        }
        long lastConsume = BLOOD_FOX_LAST_CONSUME.getOrDefault(player.getUUID(), player.level().getGameTime());
        BLOOD_FOX_LAST_CONSUME.putIfAbsent(player.getUUID(), player.level().getGameTime());
        if (player.level().getGameTime() - lastConsume >= 20L * 90L && player.tickCount % 20 == 0) {
            SREPlayerMoodComponent.KEY.get(player).addMood(-0.0033F);
        }
        VtuberRolePlayerComponent component = VtuberRolePlayerComponent.KEY.get(player);
        if (component.getDisguise() != VtuberRolePlayerComponent.BLOOD_FOX) {
            return;
        }
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
        if (mood.getMood() < 0.5F) {
            leaveAnimalForm(player);
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.blood_fox.low_san_exit"), true);
            return;
        }
        if (player.tickCount % 20 == 0) {
            mood.addMood(-0.005F);
            if (mood.getMood() < 0.5F) {
                leaveAnimalForm(player);
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.blood_fox.low_san_exit"), true);
            }
        }
    }

    private static boolean allowAnimalFormDeath(Player victim) {
        if (!(victim instanceof ServerPlayer player)) {
            return true;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        VtuberRolePlayerComponent component = VtuberRolePlayerComponent.KEY.get(player);
        if (game.isRole(player, ModRoles.BLOOD_FOX)
                && component.getDisguise() == VtuberRolePlayerComponent.BLOOD_FOX) {
            player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.5F));
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.blood_fox.fatal_immune"), true);
            return false;
        }
        return true;
    }

    private static void leaveAnimalForm(ServerPlayer player) {
        VtuberRolePlayerComponent.KEY.get(player).setDisguise(VtuberRolePlayerComponent.NONE);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0,
                false, false, true));
    }

    private static void captureSnapshot(ServerPlayer player, long now) {
        List<MobEffectInstance> effects = player.getActiveEffects().stream()
                .map(MobEffectInstance::new)
                .toList();
        List<ItemStack> inventory = new ArrayList<>(player.getInventory().getContainerSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            inventory.add(player.getInventory().getItem(slot).copy());
        }
        PlayerSnapshot snapshot = new PlayerSnapshot(now, player.position(), player.getYRot(), player.getXRot(),
                player.getHealth(), effects, inventory, player.getInventory().selected,
                SREPlayerShopComponent.KEY.get(player).balance, GameUtils.isPlayerAliveAndSurvival(player));
        Deque<PlayerSnapshot> snapshots = PLAYER_SNAPSHOTS.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
        snapshots.addLast(snapshot);
        while (!snapshots.isEmpty() && snapshots.peekFirst().tick() < now - 20L * 6L) {
            snapshots.removeFirst();
        }
    }

    private static PlayerSnapshot findSnapshot(UUID playerUuid, long targetTick) {
        Deque<PlayerSnapshot> snapshots = PLAYER_SNAPSHOTS.get(playerUuid);
        if (snapshots == null) {
            return null;
        }
        var iterator = snapshots.descendingIterator();
        while (iterator.hasNext()) {
            PlayerSnapshot snapshot = iterator.next();
            if (snapshot.tick() <= targetTick) {
                return snapshot;
            }
        }
        return snapshots.peekFirst();
    }

    private static void restoreSnapshot(ServerPlayer player, PlayerSnapshot snapshot) {
        if (snapshot.alive() && GameUtils.isPlayerEliminated(player)) {
            GameUtils.revivePlayerToItsRoom(player);
            var playArea = AreasWorldComponent.KEY.get(player.serverLevel()).getPlayArea().inflate(8.0D);
            for (PlayerBodyEntity body : player.serverLevel().getEntitiesOfClass(PlayerBodyEntity.class, playArea)) {
                if (player.getUUID().equals(body.getPlayerUuid())) {
                    body.discard();
                }
            }
        }
        if (!snapshot.alive()) {
            return;
        }
        player.teleportTo(player.serverLevel(), snapshot.position().x, snapshot.position().y, snapshot.position().z,
                Set.of(), snapshot.yRot(), snapshot.xRot());
        player.setHealth(Math.min(snapshot.health(), player.getMaxHealth()));
        player.removeAllEffects();
        for (MobEffectInstance effect : snapshot.effects()) {
            player.addEffect(new MobEffectInstance(effect));
        }
        player.getInventory().clearContent();
        for (int slot = 0; slot < Math.min(snapshot.inventory().size(),
                player.getInventory().getContainerSize()); slot++) {
            player.getInventory().setItem(slot, snapshot.inventory().get(slot).copy());
        }
        player.getInventory().selected = snapshot.selectedSlot();
        SREPlayerShopComponent.KEY.get(player).setBalance(snapshot.balance());
        player.containerMenu.broadcastChanges();
    }

    private static void tickForcedMovement(MinecraftServer server, ServerPlayer player, long now) {
        ForcedMovement movement = FORCED_MOVEMENTS.get(player.getUUID());
        if (movement == null) {
            return;
        }
        if (now >= movement.expiresAt()) {
            FORCED_MOVEMENTS.remove(player.getUUID());
            return;
        }
        ServerPlayer source = server.getPlayerList().getPlayer(movement.source());
        if (source == null || !GameUtils.isPlayerAliveAndSurvival(source)) {
            FORCED_MOVEMENTS.remove(player.getUUID());
            return;
        }
        Vec3 direction = source.position().subtract(player.position());
        if (!movement.toward()) {
            direction = direction.scale(-1.0D);
        }
        direction = new Vec3(direction.x, 0.0D, direction.z);
        if (direction.lengthSqr() > 0.0001D) {
            Vec3 velocity = direction.normalize().scale(0.23D);
            player.setDeltaMovement(velocity.x, player.getDeltaMovement().y, velocity.z);
            player.hurtMarked = true;
        }
    }

    private static void tickUncontrolledAlliance(ServerPlayer player, SREGameWorldComponent game, long now) {
        boolean member = game.isRole(player, ModRoles.XIAOYE)
                || game.isRole(player, ModRoles.XIANMIAO)
                || game.isRole(player, ModRoles.YUZU_FENGLING);
        if (!member || now < NEXT_ALLIANCE_ROLL.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        NEXT_ALLIANCE_ROLL.put(player.getUUID(), now + 20L * 20L);
        if (player.getRandom().nextBoolean()) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 10, 4, false, false, true));
            WEAPON_BLOCKED_UNTIL.put(player.getUUID(), now + 20L * 20L);
            player.displayClientMessage(Component.translatable("message.noellesroles.uncontrolled_alliance.triggered"), true);
        }
    }

    private static void tickYuzuSleep(ServerPlayer player, SREGameWorldComponent game, long now) {
        if (!game.isRole(player, ModRoles.YUZU_FENGLING)) {
            return;
        }
        if (player.isSleeping()) {
            YUZU_SLEEP_DEADLINE.put(player.getUUID(), now + 20L * 90L);
            YUZU_SLEEP_WEAPON_BLOCKED.remove(player.getUUID());
        } else if (now >= YUZU_SLEEP_DEADLINE.computeIfAbsent(
                player.getUUID(), ignored -> now + 20L * 90L)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 0, false, false, true));
            YUZU_SLEEP_WEAPON_BLOCKED.add(player.getUUID());
        }
    }

    private static void tickAyers(ServerPlayer player, SREGameWorldComponent game, long now) {
        if (!game.isRole(player, ModRoles.AYERS)) {
            return;
        }
        UUID id = player.getUUID();
        if (!AYERS_FAST_MODE.containsKey(id)) {
            AYERS_FAST_MODE.put(id, true);
            AYERS_NEXT_SWITCH.put(id, now + 20L * 30L);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    20 * 30 + 5, 0, false, false, true));
            return;
        }
        if (now < AYERS_NEXT_SWITCH.getOrDefault(id, now)) {
            return;
        }
        boolean fast = !AYERS_FAST_MODE.get(id);
        AYERS_FAST_MODE.put(id, fast);
        AYERS_NEXT_SWITCH.put(id, now + 20L * 30L);
        player.removeEffect(fast ? MobEffects.MOVEMENT_SLOWDOWN : MobEffects.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(fast ? MobEffects.MOVEMENT_SPEED : MobEffects.MOVEMENT_SLOWDOWN,
                20 * 30 + 5, 0, false, false, true));
    }

    private static void tickPasserby(ServerPlayer player, SREGameWorldComponent game) {
        if (!game.isRole(player, ModRoles.AMI) && !game.isRole(player, ModRoles.YOZORA)) {
            return;
        }
        boolean nearby = player.serverLevel().players().stream()
                .anyMatch(other -> other != player && GameUtils.isPlayerAliveAndSurvival(other)
                        && other.distanceToSqr(player) <= 9.0D);
        if (!nearby) {
            PASSERBY_TICKS.remove(player.getUUID());
            return;
        }
        int ticks = PASSERBY_TICKS.merge(player.getUUID(), 1, Integer::sum);
        if (ticks >= 20 * 10 && ticks % 20 == 0) {
            SREPlayerMoodComponent.KEY.get(player).addMood(-0.01F);
        }
    }

    private static void tickNocturnalAndStableSan(ServerPlayer player, SREGameWorldComponent game) {
        boolean nocturnal = game.isRole(player, ModRoles.AMI)
                || game.isRole(player, ModRoles.FU_TAI)
                || game.isRole(player, ModRoles.XIANMIAO)
                || game.isRole(player, ModRoles.YUYUE)
                || game.isRole(player, ModRoles.BLOOD_FOX)
                || game.isRole(player, ModRoles.MOCHEN)
                || game.isRole(player, ModRoles.LUNA)
                || game.isRole(player, ModRoles.YORU)
                || game.isRole(player, ModRoles.MAOLUN)
                || game.isRole(player, ModRoles.YOZORA);
        if (nocturnal) {
            player.removeEffect(MobEffects.BLINDNESS);
        }
        if (game.isRole(player, ModRoles.HAKUKO_FOX)
                && !org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent.KEY
                        .get(player).isCultivating()) {
            player.removeEffect(MobEffects.BLINDNESS);
        }
        if (game.isRole(player, ModRoles.SEPTEMBER_ONE)) {
            player.removeEffect(MobEffects.CONFUSION);
        }
    }

    private static void handleDeath(Player victim) {
        if (!(victim instanceof ServerPlayer dead) || dead.level() == null) {
            return;
        }
        long now = dead.level().getGameTime();
        if (LAST_DEATH_TICK.getOrDefault(dead.getUUID(), Long.MIN_VALUE) == now) {
            return;
        }
        LAST_DEATH_TICK.put(dead.getUUID(), now);
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(dead.level());
        for (var marked : new HashMap<>(BAIYU_MARKED_TARGETS).entrySet()) {
            if (!dead.getUUID().equals(marked.getValue())) {
                continue;
            }
            ServerPlayer baiyu = dead.getServer().getPlayerList().getPlayer(marked.getKey());
            if (baiyu != null && GameUtils.isPlayerAliveAndSurvival(baiyu)
                    && game.isRole(baiyu, ModRoles.BAIYU)) {
                baiyu.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                baiyu.displayClientMessage(Component.translatable(
                        "message.noellesroles.baiyu.target_died", dead.getName()), true);
            }
            BAIYU_MARKED_TARGETS.remove(marked.getKey());
        }
        boolean demon = game.isRole(dead, ModRoles.XIAOYE)
                || game.isRole(dead, ModRoles.TINALIS)
                || game.isRole(dead, ModRoles.AYERS);
        boolean spirit = game.isRole(dead, ModRoles.YOUJIN)
                || game.isRole(dead, ModRoles.SHENWU_BINGFENG);
        if (demon || spirit) {
            int skillBanTicks = demon ? 20 * 3 : 20 * 5;
            for (ServerPlayer player : dead.serverLevel().players()) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, skillBanTicks, 0,
                            false, false, true));
                    if (spirit) {
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 5, 2,
                                false, false, true));
                    }
                }
            }
        }
        if (game.isRole(dead, ModRoles.LUNA) || game.isRole(dead, ModRoles.YORU)) {
            killSymbioticPair(dead, game);
        }
    }

    private static void killSymbioticPair(ServerPlayer dead, SREGameWorldComponent game) {
        if (!SYMBIOSIS_GUARD.add(dead.getUUID())) {
            return;
        }
        try {
            var counterpartRole = game.isRole(dead, ModRoles.LUNA) ? ModRoles.YORU : ModRoles.LUNA;
            dead.serverLevel().players().stream()
                    .filter(player -> GameUtils.isPlayerAliveAndSurvival(player) && game.isRole(player, counterpartRole))
                    .findFirst()
                    .ifPresent(counterpart -> GameUtils.forceKillPlayer(counterpart, true, dead,
                            Noellesroles.id("luna_yoru_symbiosis")));
        } finally {
            SYMBIOSIS_GUARD.remove(dead.getUUID());
        }
    }

    private static void resetRound(MinecraftServer server) {
        if (server != null) {
            for (UUID affectedUuid : KANA_AFFECTED.values().stream()
                    .flatMap(Set::stream)
                    .collect(java.util.stream.Collectors.toSet())) {
                ServerPlayer affected = server.getPlayerList().getPlayer(affectedUuid);
                if (affected != null) {
                    affected.removeEffect(ModEffects.VOICE_HELIUM);
                    affected.removeEffect(ModEffects.HEAVY_METAL_VOICE);
                }
            }
        }
        WEAPON_BLOCKED_UNTIL.clear();
        NEXT_ALLIANCE_ROLL.clear();
        YUZU_SLEEP_DEADLINE.clear();
        YUZU_SLEEP_WEAPON_BLOCKED.clear();
        PASSERBY_TICKS.clear();
        AYERS_NEXT_SWITCH.clear();
        AYERS_FAST_MODE.clear();
        FORCED_MOVEMENTS.clear();
        LAST_DEATH_TICK.clear();
        PLAYER_SNAPSHOTS.clear();
        KANA_AFFECTED.clear();
        KANA_PARTY.clear();
        KANA_INITIAL_PLAYERS.clear();
        KANA_MENU_COOLDOWN.clear();
        MEOWLEN_MENU_COOLDOWN.clear();
        BAIYU_MARKED_TARGETS.clear();
        FOOD_TRAPS.clear();
        BLOOD_FOX_LAST_CONSUME.clear();
        HOSHIZORA_WEAPON_BLOCKED_UNTIL.clear();
        SYMBIOSIS_GUARD.clear();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                long now = player.level().getGameTime();
                NEXT_ALLIANCE_ROLL.put(player.getUUID(), now + 20L * 20L);
                YUZU_SLEEP_DEADLINE.put(player.getUUID(), now + 20L * 90L);
                AYERS_NEXT_SWITCH.put(player.getUUID(), now + 20L * 30L);
                BLOOD_FOX_LAST_CONSUME.put(player.getUUID(), now);
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
                if (game.isRole(player, ModRoles.KANA)) {
                    KANA_INITIAL_PLAYERS.put(player.getUUID(), game.getPlayerCount());
                }
                VtuberRolePlayerComponent.KEY.maybeGet(player)
                        .ifPresent(component -> component.setDisguise(VtuberRolePlayerComponent.NONE));
            }
        }
    }
}
