package org.agmas.noellesroles.game.roles.neutral.jester;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 小丑组件：靠近门框时获得无碰撞效果（不显示粒子）。
 */
public final class JesterPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<JesterPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("jester"), JesterPlayerComponent.class);

    private static final double DOOR_CHECK_RANGE = 1.5;

    private final Player player;
    private boolean nearDoor;

    public JesterPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return true;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        nearDoor = false;
        sync();
    }

    @Override
    public void clear() {
        player.removeEffect(ModEffects.NO_COLLIDE);
        nearDoor = false;
    }

    @Override
    public void serverTick() {
        Level level = player.level();
        BlockPos playerPos = player.blockPosition();
        boolean foundDoor = false;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                    double dist = Math.sqrt(
                            (checkPos.getX() + 0.5 - player.getX()) * (checkPos.getX() + 0.5 - player.getX())
                                    + (checkPos.getY() + 0.5 - player.getY()) * (checkPos.getY() + 0.5 - player.getY())
                                    + (checkPos.getZ() + 0.5 - player.getZ())
                                            * (checkPos.getZ() + 0.5 - player.getZ()));
                    if (dist <= DOOR_CHECK_RANGE) {
                        if (level.getBlockState(checkPos).getBlock() instanceof SmallDoorBlock) {
                            foundDoor = true;
                            break;
                        }
                    }
                }
                if (foundDoor) break;
            }
            if (foundDoor) break;
        }

        if (foundDoor != nearDoor) {
            nearDoor = foundDoor;
            if (nearDoor) {
                // 靠近门框：添加无碰撞效果，不显示粒子
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        ModEffects.NO_COLLIDE, 60, 0, true, false, false));
            } else {
                // 远离门框：移除无碰撞效果
                player.removeEffect(ModEffects.NO_COLLIDE);
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("NearDoor", nearDoor);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        nearDoor = tag.getBoolean("NearDoor");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
