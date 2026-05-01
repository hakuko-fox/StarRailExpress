package io.wifi.events.day_night_fight.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 里世界CCA组件
 * 管理死亡玩家在里世界的状态,包括:
 * - 复活倒计时
 * - 发光线索点位置
 * - 是否正在里世界中
 */
public class DNFUnderworldComponent implements RoleComponent , ServerTickingComponent {
    public static final ComponentKey<DNFUnderworldComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("dnf_underworld"), DNFUnderworldComponent.class);

    private final Player player;
    
    // 是否在里世界中
    private boolean inUnderworld = false;
    
    // 复活倒计时(秒)
    private int reviveCountdown = 0;
    
    // 发光线索点位置
    @Nullable
    private BlockPos cluePointPos = null;
    
    // 最大复活时间(3分钟 = 180秒)
    public static final int MAX_REVIVE_TIME = 180;
    
    // 被攻击减少的时间(30秒)
    public static final int ATTACK_TIME_REDUCTION = 30;

    public DNFUnderworldComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        clear();
    }

    @Override
    public void clear() {
        inUnderworld = false;
        reviveCountdown = 0;
        cluePointPos = null;
        sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        tag.putBoolean("in_underworld", inUnderworld);
        tag.putInt("revive_countdown", reviveCountdown);
        if (cluePointPos != null) {
            tag.putLong("clue_point_pos", cluePointPos.asLong());
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        inUnderworld = tag.getBoolean("in_underworld");
        reviveCountdown = tag.getInt("revive_countdown");
        if (tag.contains("clue_point_pos")) {
            cluePointPos = BlockPos.of(tag.getLong("clue_point_pos"));
        } else {
            cluePointPos = null;
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        tag.putBoolean("in_underworld", inUnderworld);
        tag.putInt("revive_countdown", reviveCountdown);
        if (cluePointPos != null) {
            tag.putLong("clue_point_pos", cluePointPos.asLong());
        }
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        inUnderworld = tag.getBoolean("in_underworld");
        reviveCountdown = tag.getInt("revive_countdown");
        if (tag.contains("clue_point_pos")) {
            cluePointPos = BlockPos.of(tag.getLong("clue_point_pos"));
        } else {
            cluePointPos = null;
        }
    }

    public void sync() {
        if (player instanceof ServerPlayer sp) {
            KEY.sync(sp);
        }
    }

    /**
     * 玩家死亡时进入里世界
     */
    public void enterUnderworld(BlockPos cluePoint) {
        this.inUnderworld = true;
        this.reviveCountdown = MAX_REVIVE_TIME;
        this.cluePointPos = cluePoint;
        sync();
    }

    /**
     * 每tick更新倒计时
     */
    public void tick() {
        if (!inUnderworld) return;
        
        if (reviveCountdown > 0) {
            reviveCountdown--;
            sync();
        }
    }

    /**
     * 被DNF_ABYSS攻击时减少时间
     */
    public void reduceTime() {
        if (!inUnderworld) return;
        
        reviveCountdown = Math.max(0, reviveCountdown - ATTACK_TIME_REDUCTION);
        sync();
    }

    /**
     * 复活玩家
     */
    public void revivePlayer() {
        this.inUnderworld = false;
        this.reviveCountdown = 0;
        this.cluePointPos = null;
        sync();
    }

    /**
     * 是否在里世界中
     */
    public boolean isInUnderworld() {
        return inUnderworld;
    }

    /**
     * 获取复活倒计时(秒)
     */
    public int getReviveCountdown() {
        return reviveCountdown;
    }

    /**
     * 获取发光线索点位置
     */
    @Nullable
    public BlockPos getCluePointPos() {
        return cluePointPos;
    }

    /**
     * 获取倒计时剩余分钟
     */
    public int getRemainingMinutes() {
        return reviveCountdown / 60;
    }

    /**
     * 获取倒计时剩余秒数
     */
    public int getRemainingSeconds() {
        return reviveCountdown % 60;
    }

    @Override
    public void serverTick() {
        DNFUnderworldComponent underworld = DNFUnderworldComponent.KEY.get(player);
        if (underworld.isInUnderworld()) {
            underworld.tick();

            // 检查倒计时是否结束
            if (underworld.getReviveCountdown() <= 0) {
                GameUtils.killPlayer(player, true, null);
            }
        }
    }
}
