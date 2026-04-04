package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 棒球员组件
 *
 * 技能：开局自带一个球棒
 */
public class BaseballPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<BaseballPlayerComponent> KEY = ModComponents.BASEBALL_PLAYER;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 是否已给予开局球棒 */
    public boolean givenBat = false;

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 构造函数
     */
    public BaseballPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.givenBat = false;
        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    /**
     * 清除所有状态
     */
    public void clearAll() {
        this.givenBat = false;
        this.sync();
    }

    /**
     * 检查是否是活跃的棒球员
     */
    public boolean isActiveBaseballPlayer() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.BASEBALL_PLAYER);
    }

    /**
     * 给予开局球棒
     */
    public void giveStartingBat() {
        if (givenBat)
            return;
        if (!(player instanceof ServerPlayer))
            return;

        // 给予球棒
        player.getInventory().add(new ItemStack(TMMItems.BAT));

        givenBat = true;
        this.sync();
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.BASEBALL_PLAYER.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        if (!isActiveBaseballPlayer())
            return;

        // 给予开局球棒
        giveStartingBat();
    }

    // ==================== 序列化 ====================

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.givenBat = tag.getBoolean("givenBat");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("givenBat", this.givenBat);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("givenBat", this.givenBat);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.givenBat = tag.contains("givenBat") && tag.getBoolean("givenBat");
    }
}