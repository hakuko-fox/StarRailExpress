package org.agmas.noellesroles.game.roles.innocence.futai;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/** Fu Tai's fixed-cost oracle skill. Blindness immunity is handled by the shared VTuber runtime. */
public class FuTaiPlayerComponent implements RoleComponent {
    public static final ComponentKey<FuTaiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fu_tai"),
            FuTaiPlayerComponent.class);

    private final Player player;
    private long nextOracleTick;

    public FuTaiPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer other) {
        return other == player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        nextOracleTick = 0L;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean useOracleSkill(ServerPlayer user, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return false;
        }
        long now = user.level().getGameTime();
        if (now < nextOracleTick) {
            long seconds = (nextOracleTick - now + 19L) / 20L;
            user.displayClientMessage(Component.translatable(
                    "message.noellesroles.fu_tai.oracle_cooldown", seconds), true);
            return false;
        }

        int cost = 200;
        var shop = SREPlayerShopComponent.KEY.get(user);
        if (shop.balance < cost) {
            user.displayClientMessage(Component.translatable(
                    "message.noellesroles.fu_tai.not_enough_money", cost), true);
            return false;
        }
        if (cost > 0) {
            shop.addToBalance(-cost);
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(user.level());
        int killers = 0;
        int neutrals = 0;
        for (ServerPlayer target : user.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            var role = game.getRole(target);
            if (role == null) {
                continue;
            }
            if (role.isNeutrals()) {
                neutrals++;
            } else if (game.isKillerTeamRole(role)) {
                killers++;
            }
        }
        nextOracleTick = now + 20L * 150L;
        user.displayClientMessage(Component.translatable(
                "message.noellesroles.fu_tai.oracle_result", killers, neutrals), true);
        return true;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("NextOracleTick", nextOracleTick);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        nextOracleTick = tag.getLong("NextOracleTick");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
    }

    static {
        OnGameTrueStarted.EVENT.register(level -> {
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
            for (ServerPlayer candidate : level.players()) {
                if (game.isRole(candidate, ModRoles.FU_TAI)) {
                    KEY.get(candidate).init();
                }
            }
        });
    }
}
