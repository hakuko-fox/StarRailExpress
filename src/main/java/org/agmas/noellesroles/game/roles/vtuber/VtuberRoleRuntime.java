package org.agmas.noellesroles.game.roles.vtuber;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.Scheduler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.vtuber.BloodFoxRole;
import org.agmas.noellesroles.role.vtuber.HalicRole;
import org.agmas.noellesroles.role.vtuber.KanaRole;
import org.agmas.noellesroles.role.vtuber.MaolunRole;
import org.agmas.noellesroles.role.vtuber.VtuberRoleSupport;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;

/** Shared round runtime for the VTuber roster's server-only passives and skills. */
public final class VtuberRoleRuntime {
    private static final Map<UUID, ForcedMovement> FORCED_MOVEMENTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_DEATH_TICK = new HashMap<>();
    private static final Map<UUID, Deque<PlayerSnapshot>> PLAYER_SNAPSHOTS = new HashMap<>();
    private static final Map<GlobalPos, UUID> FOOD_TRAPS = new HashMap<>();
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
            KanaRole.handleKanaKill(killer);
        });
        AllowPlayerDeath.EVENT.register((victim, reason) -> BloodFoxRole.allowAnimalFormDeath(victim));
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> BloodFoxRole.allowAnimalFormDeath(victim));
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
        VtuberRoleData data = player == null ? null : RoleData.getNullable(VtuberRoleData.class, player);
        return data != null && (data.weaponBlockedUntil > player.level().getGameTime()
                || data.yuzuSleepWeaponBlocked || data.isDisguised());
    }

    public static boolean isAnimalDisguised(Player player) {
        return player != null && RoleData.getOptional(VtuberRoleData.class, player)
                .map(VtuberRoleData::isDisguised).orElse(false);
    }

    public static boolean useRewind(ServerPlayer caster, int seconds) {
        return useRewind(caster, seconds, 200);
    }

    public static boolean useRewind(ServerPlayer caster, int seconds, int cost) {
        if (!GameUtils.isPlayerAliveAndSurvival(caster)) {
            return false;
        }
        int casterBalance = MoneyUtils.getBalance(caster);
        if (casterBalance < cost) {
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
        MoneyUtils.addToBalance(caster, -cost);
        caster.displayClientMessage(Component.translatable(
                "message.noellesroles.time_rewind.restored", seconds, restored), true);
        return restored > 0;
    }

    public static boolean useFoodTrap(ServerPlayer player) {
        if (!(player.pick(5.0D, 0.0F, false) instanceof BlockHitResult hit)
                || !(player.level().getBlockEntity(hit.getBlockPos()) instanceof PlateTrayBlockEntity)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.youjin.invalid_tray"), true);
            return false;
        }
        GlobalPos pos = GlobalPos.of(player.level().dimension(), hit.getBlockPos());
        if (FOOD_TRAPS.containsKey(pos) || !VtuberRoleSupport.deduct(player, 10)) {
            return false;
        }
        FOOD_TRAPS.put(pos, player.getUUID());
        player.displayClientMessage(Component.translatable("message.noellesroles.youjin.trap_set"), true);
        return true;
    }

    public static void onConsume(Player consumer, ItemStack stack) {
        if (!(consumer instanceof ServerPlayer player)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game.isRole(player, ModRoles.BLOOD_FOX)) {
            RoleData.getOptional(VtuberRoleData.class, player)
                    .ifPresent(data -> data.bloodFoxLastConsume = player.level().getGameTime());
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
            MoneyUtils.addToBalance(owner, 20);
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
        if (!GameUtils.isPlayerAliveAndSurvival(caster) || firstUuid == null
                || RoleData.getNullable(VtuberRoleData.class, caster) == null) {
            return;
        }
        if (RoleSkill.blockForSpectator(caster, false)) {
            caster.displayClientMessage(Component.translatable("message.tip.cant_use_skill"), true);
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(caster.level());
        ServerPlayer first = caster.getServer().getPlayerList().getPlayer(firstUuid);
        ServerPlayer second = secondUuid == null ? null : caster.getServer().getPlayerList().getPlayer(secondUuid);
        if (game.isRole(caster, ModRoles.KANA)) {
            KanaRole.selectKanaTarget(caster, first, game);
        } else if (game.isRole(caster, ModRoles.MAOLUN)) {
            MaolunRole.selectMeowlenTargets(caster, first, second);
        }
    }

    public static void applyForcedMovement(ServerPlayer caster, boolean toward, int ticks) {
        long expiresAt = caster.level().getGameTime() + ticks;
        for (ServerPlayer target : caster.serverLevel().players()) {
            if (target != caster && GameUtils.isPlayerAliveAndSurvival(target)) {
                FORCED_MOVEMENTS.put(target.getUUID(), new ForcedMovement(caster.getUUID(), toward, expiresAt));
            }
        }
    }

    private static void serverTick(MinecraftServer server) {
        MaolunRole.tickMaolunChallenges(server);
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

            tickForcedMovement(server, player, now);

        }
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
                MoneyUtils.getBalance(player), GameUtils.isPlayerAliveAndSurvival(player));
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
            GameUtils.revivePlayer(player, snapshot.position().x, snapshot.position().y, snapshot.position().z);
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
        MoneyUtils.setBalance(player, snapshot.balance());
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
        for (ServerPlayer baiyu : dead.serverLevel().players()) {
            VtuberRoleData data = RoleData.getNullable(VtuberRoleData.class, baiyu);
            if (data == null || !dead.getUUID().equals(data.baiyuMarkedTarget)) continue;
            if (GameUtils.isPlayerAliveAndSurvival(baiyu) && game.isRole(baiyu, ModRoles.BAIYU)) {
                baiyu.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                baiyu.displayClientMessage(Component.translatable(
                        "message.noellesroles.baiyu.target_died", dead.getName()), true);
            }
            data.baiyuMarkedTarget = null;
            data.setMarkedTargetName("");
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
        FORCED_MOVEMENTS.clear();
        LAST_DEATH_TICK.clear();
        PLAYER_SNAPSHOTS.clear();
        FOOD_TRAPS.clear();
        SYMBIOSIS_GUARD.clear();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RoleData.getOptional(VtuberRoleData.class, player).ifPresent(data -> {
                    data.clear();
                    data.init();
                    data.sync();
                });
                HalicRole.clearDecoys(player);
            }
        }
    }

    /** Compatibility entry points used by the core knife payload. */
    public static boolean canUseKanaKnife(Player player) { return KanaRole.canUseKanaKnife(player); }
    public static void consumeKanaKnife(ServerPlayer player) { KanaRole.consumeKanaKnife(player); }
    public static void finishKanaKnifeAttack(ServerPlayer player) { KanaRole.finishKanaKnifeAttack(player); }
}
