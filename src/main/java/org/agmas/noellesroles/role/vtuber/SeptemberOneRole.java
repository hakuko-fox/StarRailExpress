package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.SeptemberOneRoleData;

public class SeptemberOneRole extends CustomWinnerRole {
    public SeptemberOneRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(SeptemberOneRoleData::new);
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        super.onFinishQuest(player, quest);
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        var data = RoleData.getNullable(SeptemberOneRoleData.class, serverPlayer);
        if (data == null) return;
        data.tasksCompleted++;
        serverPlayer.displayClientMessage(Component.translatable(
                "message.noellesroles.nine_one.task_progress",
                Component.literal(Integer.toString(data.tasksCompleted)), Component.literal("15")), true);
        if (data.tasksCompleted >= 15) {
            win(serverPlayer);
        }
    }

    @Override
    public WinStatus checkWin(ServerPlayer player,
            WinStatus winStatus) {
        return RoleData.getOptional(SeptemberOneRoleData.class, player).map(data -> data.tasksCompleted >= 15).orElse(false)
                ? WinStatus.CUSTOM
                : WinStatus.NOT_MODIFY;
    }

    @Override
    public boolean onUseGun(Player player) { return false; }

    @Override
    public boolean onUseKnife(Player player) { return false; }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return VtuberRoleSupport.isWeapon(item.getItem()) ? TrueFalseResult.FALSE : TrueFalseResult.PASS;
    }

    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
                    if (!(victim instanceof ServerPlayer nineOne) || killer == null) {
                        return true;
                    }
                    var game = SREGameWorldComponent.KEY.get(victim.level());
                    if (RoleData.getNullable(SeptemberOneRoleData.class, nineOne) == null
                    || !game.isRole(nineOne, ModRoles.SEPTEMBER_ONE)
                    || RoleData.getNullable(SeptemberOneRoleData.class, nineOne).fatalShieldUsed) {
                        return true;
                    }
                    net.minecraft.world.phys.Vec3 toAttacker = killer.position().subtract(nineOne.position())
                    .multiply(1.0D, 0.0D, 1.0D).normalize();
                    net.minecraft.world.phys.Vec3 facing = nineOne.getLookAngle()
                    .multiply(1.0D, 0.0D, 1.0D).normalize();
                    if (facing.dot(toAttacker) <= 0.0D) {
                        return true;
                    }
                    RoleData.getNullable(SeptemberOneRoleData.class, nineOne).fatalShieldUsed = true;
                    killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 6, 0,
                    false, false, true));
                    nineOne.displayClientMessage(Component.translatable(
                    "message.noellesroles.nine_one.front_shield"), true);
                    return false;
                });
    }

    public static void recordAttack(Player victim, Player attacker) {
        if (!(victim instanceof ServerPlayer damaged) || attacker == null)
            return;
        var game = SREGameWorldComponent.KEY.get(victim.level());
        if (game.isRole(damaged, ModRoles.SEPTEMBER_ONE)
                    && RoleData.getNullable(SeptemberOneRoleData.class, damaged) != null) {
            if (!RoleData.getNullable(SeptemberOneRoleData.class, damaged).attacked) {
                RoleData.getNullable(SeptemberOneRoleData.class, damaged).attacked = true;
                SREPlayerTaskComponent tasks = SREPlayerTaskComponent.KEY.get(damaged);
                tasks.currentTaskAge = 0;
                tasks.nextTaskTimer = 40 * 20;
                tasks.sync();
            }
            damaged.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, Integer.MAX_VALUE, 0,
                    false, false, true));
            damaged.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, Integer.MAX_VALUE, 0,
                    false, false, true));
        }
    }

    public static void tickNineOneTaskConcealment(ServerPlayer player, SREGameWorldComponent game) {
        if (game.isRole(player, ModRoles.SEPTEMBER_ONE)
                    && !SREPlayerTaskComponent.KEY.get(player).tasks.isEmpty()) {
            player.addEffect(new MobEffectInstance(ModEffects.NINE_ONE_TASK_CONCEALMENT,
                    10, 0, false, false, false));
        } else {
            player.removeEffect(ModEffects.NINE_ONE_TASK_CONCEALMENT);
        }
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        tickNineOneTaskConcealment(player, game);
        player.removeEffect(MobEffects.CONFUSION);
    }
}
