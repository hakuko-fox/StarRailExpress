package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.HashSet;
import java.util.Set;

/**
 * 小游戏任务 / 游戏代币组件（当局制，每局清零）。
 * <p>
 * 玩家每完成 2 个普通任务（{@link SREPlayerTaskComponent}）会被派发一个「小游戏任务」。
 * 玩家可前往地图中<b>任意</b>小游戏任务点方块完成小游戏来兑现该任务并获得游戏代币；
 * 但每个任务点本局对每名玩家只能使用一次（已用方块记录在 {@link #usedBlocks} 中）。
 * 组件随 CCA 自动同步到本人客户端，因此金色标记与 HUD 无需额外网络包。
 */
public class SREPlayerMinigameTaskComponent implements RoleComponent {
    public static final ComponentKey<SREPlayerMinigameTaskComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("minigame_task"), SREPlayerMinigameTaskComponent.class);

    private final Player player;

    /** 当局游戏代币。 */
    public int tokens = 0;
    /** 已完成的普通任务累计数（每 2 个派发一次小游戏任务）。 */
    public int normalTaskCounter = 0;
    /** 已派发但尚未兑现的小游戏任务数。 */
    public int pendingMinigameTasks = 0;
    /** 本局该玩家已使用过的小游戏任务点（以 {@link BlockPos#asLong()} 存储）。 */
    public final Set<Long> usedBlocks = new HashSet<>();

    public SREPlayerMinigameTaskComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.tokens = 0;
        this.normalTaskCounter = 0;
        this.pendingMinigameTasks = 0;
        this.usedBlocks.clear();
        this.sync();
    }

    @Override
    public void clear() {
        init();
    }

    public int getTokens() {
        return this.tokens;
    }

    public int getPendingMinigameTasks() {
        return this.pendingMinigameTasks;
    }

    public boolean hasPendingTask() {
        return this.pendingMinigameTasks > 0;
    }

    /** 该任务点本局是否已被本玩家使用过。 */
    public boolean isBlockUsed(BlockPos pos) {
        return this.usedBlocks.contains(pos.asLong());
    }

    public void addTokens(int amount) {
        setTokens(this.tokens + amount);
    }

    public void setTokens(int amount) {
        if (this.tokens != amount) {
            this.tokens = amount;
            this.sync();
        }
    }

    /** 一个普通任务完成时调用：每累计满 2 个，派发一个小游戏任务。 */
    public void onNormalTaskCompleted(ServerPlayer sp) {
        this.normalTaskCounter++;
        if (this.normalTaskCounter % 2 == 0) {
            this.pendingMinigameTasks++;
            this.sync();
            net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(sp,
                    Component.translatable("hud.sre.minigame_task"),
                    Component.translatable("subtitle.minigame_task.new"), 60);
        }
    }

    /**
     * 完成某个小游戏方块时调用：若玩家有待办小游戏任务且该方块本局未被本玩家用过，
     * 则兑现一个任务、标记该方块已用并发放代币。
     *
     * @return 是否发放了奖励
     */
    public boolean onMinigameBlockCompleted(ServerPlayer sp, BlockPos pos, int reward) {
        if (this.pendingMinigameTasks <= 0) {
            return false;
        }
        long key = pos.asLong();
        if (!this.usedBlocks.add(key)) {
            // 该任务点本局已被本玩家使用过
            return false;
        }
        this.pendingMinigameTasks--;
        addTokens(reward);
        this.sync();
        net.exmo.sre.subtitle.SubtitleCommand.sendToPlayerTop(sp,
                Component.translatable("hud.sre.minigame_task"),
                Component.translatable("subtitle.minigame_task.done", reward), 60);
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Tokens", this.tokens);
        tag.putInt("Counter", this.normalTaskCounter);
        tag.putInt("Pending", this.pendingMinigameTasks);
        long[] used = new long[this.usedBlocks.size()];
        int i = 0;
        for (long v : this.usedBlocks) {
            used[i++] = v;
        }
        tag.putLongArray("Used", used);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.tokens = tag.getInt("Tokens");
        this.normalTaskCounter = tag.getInt("Counter");
        this.pendingMinigameTasks = tag.getInt("Pending");
        this.usedBlocks.clear();
        for (long v : tag.getLongArray("Used")) {
            this.usedBlocks.add(v);
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 当局制，无需持久化
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 当局制，无需持久化
    }
}
