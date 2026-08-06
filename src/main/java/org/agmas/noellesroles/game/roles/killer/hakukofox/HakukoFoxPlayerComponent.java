package org.agmas.noellesroles.game.roles.killer.hakukofox;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class HakukoFoxPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<HakukoFoxPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "hakukofox"),
            HakukoFoxPlayerComponent.class);

    private final Player player;

    private boolean beastFormActive = false;
    private boolean nineLivesUsed = false;

    public HakukoFoxPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        // 偽裝狀態必須同步給所有客戶端，否則其他玩家看不到狐狸模型，且遊戲結束時無法正確還原。
        return true;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        beastFormActive = false;
        nineLivesUsed = false;
        sync();
    }

    @Override
    public void clear() {
        removeBeastEffects();
        if (player instanceof ServerPlayer sp) {
            sp.refreshDimensions();
        }
        init();
    }

    public boolean isBeastFormActive() {
        return beastFormActive;
    }

    public boolean isDisguised() {
        return beastFormActive;
    }

    public static boolean isDisguised(Player player) {
        HakukoFoxPlayerComponent comp = KEY.maybeGet(player).orElse(null);
        return comp != null && comp.isDisguised();
    }

    public boolean toggleBeastForm(ServerPlayer sp, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (beastFormActive) {
            removeBeastEffects();
            beastFormActive = false;
            sp.refreshDimensions();
            context.setSkillCooldown(180 * 20);
            sync();
            return true;
        } else {
            if (!context.skillReady()) {
                sp.displayClientMessage(Component.translatable("message.sre.skill.cooldown",
                        String.format("%.1f", context.skillState().cooldown / 20.0F)), true);
                return false;
            }
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1, false, false, true));
            sp.addEffect(new MobEffectInstance(MobEffects.JUMP, -1, 1, false, false, true));
            beastFormActive = true;
            nineLivesUsed = false;
            sp.refreshDimensions();

            ServerLevel world = sp.serverLevel();
            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.FOX_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
            sp.displayClientMessage(
                    Component.translatable("skill.noellesroles.hakukofox.transform"),
                    true);
            sync();
            return true;
        }
    }

    public boolean useFreezeSkill(ServerPlayer sp, RoleSkillContext ctx) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;

        int cost = 100;
        var shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.hakukofox.not_enough_money", cost),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        ServerLevel world = sp.serverLevel();
        for (ServerPlayer other : world.players()) {
            if (other == sp) continue;
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 255, false, false, true));
            other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, true));
            other.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 128, false, false, true));
            other.displayClientMessage(
                    Component.translatable("skill.noellesroles.hakukofox.freeze_notify"),
                    true);
        }

        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.FOX_SCREECH, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(
                Component.translatable("skill.noellesroles.hakukofox.freeze_self"),
                true);
        ctx.setSkillCooldown(60 * 20);
        return true;
    }

    public boolean tryUseNineLives() {
        if (beastFormActive && !nineLivesUsed) {
            nineLivesUsed = true;
            sync();
            return true;
        }
        return false;
    }

    public boolean hasNineLivesRemaining() {
        return beastFormActive && !nineLivesUsed;
    }

    private void removeBeastEffects() {
        if (player instanceof ServerPlayer sp) {
            if (sp.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                sp.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
            if (sp.hasEffect(MobEffects.JUMP)) {
                sp.removeEffect(MobEffects.JUMP);
            }
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        if (beastFormActive && !sp.isAlive()) {
            removeBeastEffects();
            beastFormActive = false;
            sp.refreshDimensions();
            sync();
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("beastFormActive", beastFormActive);
        tag.putBoolean("nineLivesUsed", nineLivesUsed);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        beastFormActive = tag.getBoolean("beastFormActive");
        nineLivesUsed = tag.getBoolean("nineLivesUsed");
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
