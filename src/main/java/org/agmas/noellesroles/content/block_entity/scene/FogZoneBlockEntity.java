package org.agmas.noellesroles.content.block_entity.scene;

import java.util.List;

import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneRoleAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 迷雾区域方块实体：允许职业进入则致盲（低可视度）；非允许职业被向外推出。
 */
public class FogZoneBlockEntity extends BlockEntity {

    public FogZoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.FOG_ZONE_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FogZoneBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB box = org.agmas.noellesroles.scene.SceneParticles.sceneRegion(pos);
        // 区域内迷雾粒子（覆盖 3×3×4 范围）
        if (serverLevel.getGameTime() % 2 == 0) {
            org.agmas.noellesroles.scene.SceneParticles.regionScatter(serverLevel, box,
                    net.minecraft.core.particles.ParticleTypes.CLOUD, 5);
        }
        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator());
        for (Player player : players) {
            if (player.isCreative()) {
                continue;
            }
            if (SceneRoleAccess.canEnterRestricted(player, null)) {
                // 区域内低可视度（致盲），本能由客户端 SceneFogClient 关闭
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
            } else {
                // 非允许职业：被迷雾推出
                Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Vec3 away = player.position().subtract(center);
                Vec3 flat = new Vec3(away.x, 0, away.z);
                if (flat.lengthSqr() < 1.0e-3) {
                    flat = new Vec3(player.getRandom().nextDouble() - 0.5, 0, player.getRandom().nextDouble() - 0.5);
                }
                flat = flat.normalize().scale(0.55);
                player.setDeltaMovement(flat.x, 0.18, flat.z);
                player.hurtMarked = true;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, false));
                if (player instanceof ServerPlayer sp && player.tickCount % 20 == 0) {
                    sp.displayClientMessage(Component.translatable("message.noellesroles.fog.denied"), true);
                }
            }
        }
    }
}
