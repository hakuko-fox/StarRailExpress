package org.agmas.noellesroles.game.roles.vigilante.everly;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 芙妮（Everly）— 警長陣營
 *
 * 主動技1（G）：時間停止。使全場時間停止3秒，每局遊戲最多使用2次（由 charges(2) 控制），冷卻60秒。
 * 被動技：由於身為時間管理局成員，無視所有時間停止技能。
 * 標籤：香港Vtuber
 */
public class EverlyPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<EverlyPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "everly"),
            EverlyPlayerComponent.class);

    private final Player player;

    public EverlyPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /** 主動技1：時間停止 — 全場停止3秒（每局最多2次，由 charges(2) 控制） */
    public boolean useTimeStop(ServerPlayer sp, RoleSkillContext ctx) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        boolean ok = TimeStopEffect.tryTriggerStart(sp, 3 * 20,
                Component.translatable("skill.noellesroles.everly.timestop"));
        if (!ok) {
            return false;
        }
        // 次數上限由 charges(2) 控制；最後一次使用時提示
        var state = ctx.skillState();
        if (state != null && state.charges <= 1) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.everly.timestop_final"), true);
        }
        return true;
    }

    @Override
    public void serverTick() {
        // 被動（無視時間停止）在 TimeStopEffect 處理
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
    }
}
