package org.agmas.noellesroles.game.c4;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.noellesroles.cca.C4BackComponent;
import org.agmas.noellesroles.init.ModItems;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 钳子拆除管理器 - 管理C4拆除过程
 */
public final class PliersDefuseManager {
    private static final int DEFUSE_TICKS = 60; // 3秒拆除时间
    private static final double MAX_DEFUSE_DISTANCE_SQ = 4.0D * 4.0D;
    private static final Map<UUID, DefuseAttempt> ATTEMPTS = new HashMap<>();

    private PliersDefuseManager() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(PliersDefuseManager::tick);
    }

    public static ActionResult beginPlayerDefuse(ItemStack stack, PlayerEntity user, PlayerEntity target, Hand hand) {
        if (!(user instanceof ServerPlayerEntity serverUser)) return ActionResult.SUCCESS;
        if (target == null) return ActionResult.PASS;
        C4BackComponent comp = C4BackComponent.KEY.getNullable(serverUser.getWorld());
        if (comp == null || !comp.hasC4(target.getUuid())) return ActionResult.FAIL;
        if (!stack.isOf(ModItems.PLIERS)) return ActionResult.FAIL;
        start(serverUser, hand, target.getUuid(), null);
        return ActionResult.CONSUME;
    }

    public static ActionResult beginBlockDefuse(ServerPlayerEntity user, ItemStack stack, ItemEntity charge, Hand hand) {
        if (user == null || charge == null || !C4Detonation.isDefusableBlockCharge(charge)) return ActionResult.PASS;
        if (!stack.isOf(ModItems.PLIERS)) return ActionResult.FAIL;
        start(user, hand, null, charge.getUuid());
        return ActionResult.CONSUME;
    }

    private static void start(ServerPlayerEntity user, Hand hand, UUID playerTargetId, UUID blockChargeId) {
        ATTEMPTS.put(user.getUuid(), new DefuseAttempt(user.getUuid(), hand, playerTargetId, blockChargeId,
            user.getPos(), user.getWorld().getTime()));
        user.stopUsingItem();
        user.sendMessage(Text.translatable("c4.defusing"), true);
        user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
            SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.PLAYERS, 0.65F, 1.45F);
    }

    private static void tick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD || ATTEMPTS.isEmpty()) return;
        MinecraftServer server = world.getServer();
        Iterator<Map.Entry<UUID, DefuseAttempt>> iterator = ATTEMPTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DefuseAttempt> entry = iterator.next();
            DefuseAttempt attempt = entry.getValue();
            ServerPlayerEntity defuser = server.getPlayerManager().getPlayer(attempt.defuserId());
            if (defuser == null || defuser.getWorld() != world || !isStillHoldingPliers(defuser, attempt.hand())) {
                iterator.remove();
                continue;
            }

            DefuseTarget target = locateTarget(world, server, attempt);
            if (target == null || defuser.squaredDistanceTo(target.pos()) > MAX_DEFUSE_DISTANCE_SQ
                    || defuser.getPos().squaredDistanceTo(attempt.startPos()) > MAX_DEFUSE_DISTANCE_SQ) {
                defuser.sendMessage(Text.translatable("c4.defuse_cancelled"), true);
                iterator.remove();
                continue;
            }

            long elapsed = world.getTime() - attempt.startedAt();
            if (elapsed >= DEFUSE_TICKS) {
                complete(world, defuser, attempt, target);
                iterator.remove();
                continue;
            }

            showProgress(world, defuser, target, elapsed);
        }
    }

    private static boolean isStillHoldingPliers(ServerPlayerEntity player, Hand hand) {
        return player.getStackInHand(hand).isOf(ModItems.PLIERS);
    }

    private static DefuseTarget locateTarget(ServerWorld world, MinecraftServer server, DefuseAttempt attempt) {
        if (attempt.playerTargetId() != null) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(attempt.playerTargetId());
            if (target == null || target.getWorld() != world || target.isRemoved()) return null;
            C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
            if (comp == null || !comp.hasC4(target.getUuid())) return null;
            return new DefuseTarget(target.getUuid(), target.getPos().add(0.0D, 1.0D, 0.0D), true);
        }
        if (attempt.blockChargeId() == null) return null;
        if (!(world.getEntity(attempt.blockChargeId()) instanceof ItemEntity charge)
                || !C4Detonation.isDefusableBlockCharge(charge)) {
            return null;
        }
        return new DefuseTarget(charge.getUuid(), charge.getPos(), false);
    }

    private static void showProgress(ServerWorld world, ServerPlayerEntity defuser, DefuseTarget target, long elapsed) {
        int remainingTicks = Math.max(0, DEFUSE_TICKS - (int) elapsed);
        double seconds = remainingTicks / 20.0D;
        defuser.sendMessage(Text.translatable("c4.defusing_progress", seconds), true);
        Vec3d pos = target.pos();
        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.1D, pos.z,
            4, 0.12D, 0.12D, 0.12D, 0.02D);
        if (elapsed % 10L == 0L) {
            world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.35F, 1.8F);
        }
    }

    private static void complete(ServerWorld world, ServerPlayerEntity defuser, DefuseAttempt attempt, DefuseTarget target) {
        // 20%概率剪错线导致引爆
        boolean clippedWrongWire = world.getRandom().nextInt(100) < 20;
        if (target.playerTarget()) {
            completePlayerDefuse(world, defuser, attempt, clippedWrongWire);
        } else {
            completeBlockDefuse(world, defuser, attempt, clippedWrongWire);
        }
        consumePliers(defuser, attempt.hand());
    }

    private static void completePlayerDefuse(ServerWorld world, ServerPlayerEntity defuser,
            DefuseAttempt attempt, boolean clippedWrongWire) {
        MinecraftServer server = world.getServer();
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(attempt.playerTargetId());
        C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
        if (target == null || comp == null || !comp.hasC4(target.getUuid())) return;
        comp.removeC4(target.getUuid());
        if (clippedWrongWire) {
            // 剪错线，立即引爆
            C4Detonation.detonateAt(world, target, defuser);
            target.sendMessage(Text.translatable("c4.wrong_wire_explode"), false);
            defuser.sendMessage(Text.translatable("c4.you_cut_wrong_wire"), false);
            return;
        }
        // 拆除成功
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.BLOCK_TRIPWIRE_CLICK_OFF, SoundCategory.PLAYERS, 0.9F, 1.2F);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0F, 1.2F);
        target.sendMessage(Text.translatable("c4.defuse_success"), false);
        defuser.sendMessage(Text.translatable("c4.defuse_success_self"), false);
    }

    private static void completeBlockDefuse(ServerWorld world, ServerPlayerEntity defuser,
            DefuseAttempt attempt, boolean clippedWrongWire) {
        if (!(world.getEntity(attempt.blockChargeId()) instanceof ItemEntity charge)) return;
        if (clippedWrongWire) {
            C4Detonation.misfireBlockCharge(world, charge, defuser);
        } else {
            C4Detonation.defuseBlockCharge(defuser, charge);
        }
    }

    private static void consumePliers(ServerPlayerEntity player, Hand hand) {
        if (player.getAbilities().instabuild) return;
        player.getStackInHand(hand).damageAndBreak(1, player, p -> {});
    }

    private record DefuseAttempt(UUID defuserId, Hand hand, UUID playerTargetId, UUID blockChargeId,
            Vec3d startPos, long startedAt) {}

    private record DefuseTarget(UUID targetId, Vec3d pos, boolean playerTarget) {}
}
