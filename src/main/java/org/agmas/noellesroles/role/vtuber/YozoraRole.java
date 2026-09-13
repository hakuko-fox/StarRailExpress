package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

public class YozoraRole extends NormalRole {
    public YozoraRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public boolean onUseGun(Player player) {
        return !VtuberRoleRuntime.isAnimalDisguised(player);
    }

    @Override
    public boolean onUseKnife(Player player) {
        return !VtuberRoleRuntime.isAnimalDisguised(player);
    }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return VtuberRoleRuntime.isAnimalDisguised(player)
                && VtuberRoleSupport.isWeapon(item.getItem()) ? TrueFalseResult.FALSE : TrueFalseResult.PASS;
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.YOZORA,
                RoleSkill.skill(SRE.id("yozora_cat_sixth_sense"), "skill.noellesroles.yozora.cat_sixth_sense", context -> {
                    return YozoraRole.toggleYozoraCat(context);
                }).cooldownSeconds(10).toggleable(true).manualCooldown().showOnHud(true).build());
    }

    public static void giveInitialItems(Player player) {
        if (!SREItemUtils.hasItem(player, TMMItems.REVOLVER)) {
            RoleUtils.insertOrDropItem(player, TMMItems.REVOLVER.getDefaultInstance());
        }
    }

    public static void registerEvents() {
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
                    if (!(victim.level() instanceof net.minecraft.server.level.ServerLevel serverLevel))
                        return;
                    var game = SREGameWorldComponent.KEY.get(serverLevel);
                    for (ServerPlayer observer : serverLevel.players()) {
                        if (!GameUtils.isPlayerAliveAndSurvival(observer)
                            || !game.isRole(observer, ModRoles.YOZORA)
                        || RoleData.getNullable(VtuberRoleData.class, observer) == null
                        || RoleData.getNullable(VtuberRoleData.class, observer)
                        .getDisguise()
                        != VtuberRoleData.YOZORA_CAT)
                        continue;
                        int notices = ++RoleData.getNullable(VtuberRoleData.class, observer).yozoraDeathNotices;
                        observer.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
                        observer.displayClientMessage(Component.translatable("message.noellesroles.yozora.death_notice",
                        Math.max(0, 9 - notices)), true);
                        if (notices >= 9 && GameUtils.isPlayerAliveAndSurvival(observer)) {
                            GameUtils.killPlayer(observer, true, null,
                            Noellesroles.id("yozora_nine_lives"));
                        }
                    }
                });
    }

    public static boolean toggleYozoraCat(RoleSkillContext context) {
        ServerPlayer player = context.player();
        VtuberRoleData component = RoleData.getNullable(VtuberRoleData.class, player);
        if (component == null) return false;
        if (component.getDisguise() == VtuberRoleData.YOZORA_CAT) {
            VtuberRoleSupport.leaveAnimalForm(player);
            return true;
        }
        if (!context.skillReady()) {
            return false;
        }
        component.setDisguise(VtuberRoleData.YOZORA_CAT);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 2,
                false, false, true));
        return true;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || RoleData.getNullable(VtuberRoleData.class, player) == null) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        long now = player.level().getGameTime();
        VtuberRoleSupport.tickPasserby(player, game);
        player.removeEffect(MobEffects.BLINDNESS);
    }

}
