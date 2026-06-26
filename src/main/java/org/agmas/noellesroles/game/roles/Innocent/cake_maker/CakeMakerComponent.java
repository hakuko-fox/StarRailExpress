package org.agmas.noellesroles.game.roles.Innocent.cake_maker;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.packet.CakeMakerBlockS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;

public final class CakeMakerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<CakeMakerComponent> KEY = ComponentRegistry.getOrCreate(Noellesroles.id("cake_maker"), CakeMakerComponent.class);
    private final Player player;
    public int cooldown, smokerTicks, lockedTicks, stage, wheat, sugar, milk;
    public BlockPos smokerPos;
    public UUID smokerId;
    public final Map<UUID, Cake> cakes = new HashMap<>();
    public final Map<UUID, Integer> eatCooldowns = new HashMap<>();
    public CakeMakerComponent(Player player) { this.player = player; }
    @Override public Player getPlayer() { return player; }
    public boolean useSmoker() {
        if (player.getMainHandItem().is(Items.CAKE)) return placeCake();
        if (!(player instanceof ServerPlayer sp) || cooldown > 0 || smokerTicks > 0 || !sp.getMainHandItem().is(Items.SMOKER)) return false;
        smokerPos = sp.blockPosition(); smokerId = UUID.randomUUID(); smokerTicks = 40 * 20; cooldown = 60 * 20; stage = wheat = sugar = milk = lockedTicks = 0;
        broadcast(new CakeMakerBlockS2CPacket(smokerId, smokerPos, false, 0, smokerTicks, false));
        sp.displayClientMessage(Component.translatable("message.noellesroles.cake_maker.smoker_ready").withStyle(ChatFormatting.GOLD), true);
        return true;
    }
    private boolean placeCake() { if (!(player instanceof ServerPlayer sp)) return false; BlockPos pos=sp.blockPosition(); if (!sp.serverLevel().getBlockState(pos.below()).isSolidRender(sp.serverLevel(),pos.below()) || !sp.serverLevel().getBlockState(pos).isAir()) return false; UUID id=UUID.randomUUID(); cakes.put(id,new Cake(pos)); sp.getMainHandItem().shrink(1); broadcast(new CakeMakerBlockS2CPacket(id,pos,true,0,12000,false)); return true; }
    public boolean eat(UUID id, ServerPlayer eater) { Cake c=cakes.get(id); if(c==null||eater.distanceToSqr(c.pos.getCenter())>16||eatCooldowns.getOrDefault(eater.getUUID(),0)>0)return false; eatCooldowns.put(eater.getUUID(),100); eater.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*20,0)); var mood=io.wifi.starrailexpress.cca.SREPlayerMoodComponent.KEY.get(eater); mood.setMood(Math.min(1,mood.getMood()+.3f)); eater.getFoodData().eat(20,20); if(++c.bites>6){cakes.remove(id);broadcast(new CakeMakerBlockS2CPacket(id,c.pos,true,c.bites,0,true));}else broadcast(new CakeMakerBlockS2CPacket(id,c.pos,true,c.bites,12000,false)); return true; }
    public boolean addIngredient(Player p) {
        if (smokerTicks <= 0 || smokerPos == null || p.distanceToSqr(smokerPos.getCenter()) > 16 || lockedTicks > 0) return false;
        var held = p.getMainHandItem();
        boolean accepted = false;
        if (stage == 0 && held.is(Items.WHEAT) && wheat < 3) { wheat++; accepted = true; if (wheat == 3) waitFor(1, 60); }
        else if (stage == 2 && held.is(ModItems.CAKE_EGG) && sugar == 0) { stage = 21; accepted = true; }
        else if (stage == 21 && held.is(Items.SUGAR) && sugar < 2) { sugar++; accepted = true; if (sugar == 2) waitFor(3, 60); }
        else if (stage == 4 && held.is(ModItems.CAKE_MILK_BUCKET) && milk < 3) { milk++; accepted = true; if (milk == 3) waitFor(5, 100); }
        if (!accepted) { p.displayClientMessage(Component.translatable("message.noellesroles.cake_maker.wrong_ingredient").withStyle(ChatFormatting.RED), true); return true; }
        held.shrink(1); return true;
    }
    private void waitFor(int nextStage, int ticks) { stage = nextStage; lockedTicks = ticks; }
    @Override public void serverTick() {
        if (!SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.CAKE_MAKER)) return;
        if (cooldown > 0) cooldown--; if (smokerTicks > 0) smokerTicks--;
        eatCooldowns.replaceAll((id,t)->Math.max(0,t-1));
        if (lockedTicks > 0 && --lockedTicks == 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1, 1);
            if (stage == 1) { stage = 2; message("message.noellesroles.cake_maker.add_egg_sugar"); }
            else if (stage == 3) { stage = 4; message("message.noellesroles.cake_maker.add_milk"); }
            else if (stage == 5) { player.getInventory().add(Items.CAKE.getDefaultInstance()); smokerTicks = 0; removeSmoker(); stage = 0; message("message.noellesroles.cake_maker.complete"); }
        }
        if (smokerTicks == 0 && smokerId != null) removeSmoker();
    }
    private void message(String key) { if (player instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GREEN), true); }
    private void removeSmoker() { if (smokerId != null) broadcast(new CakeMakerBlockS2CPacket(smokerId, smokerPos, false, 0, 0, true)); smokerId = null; smokerPos = null; }
    private void broadcast(CakeMakerBlockS2CPacket packet) { if (player instanceof ServerPlayer sp) for (ServerPlayer target : sp.serverLevel().players()) ServerPlayNetworking.send(target, packet); }
    public static final class Cake { final BlockPos pos; int bites; Cake(BlockPos pos){this.pos=pos;} }
    @Override public void init() { cooldown=smokerTicks=lockedTicks=stage=wheat=sugar=milk=0; smokerPos=null; }
    @Override public void clear() { init(); }
    @Override public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) { tag.putInt("cooldown", cooldown); tag.putInt("smoker", smokerTicks); }
    @Override public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) { cooldown=tag.getInt("cooldown"); smokerTicks=tag.getInt("smoker"); }
    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider p) { }
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider p) { }
}
