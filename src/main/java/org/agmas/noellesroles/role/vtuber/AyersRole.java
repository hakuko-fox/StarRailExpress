package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

/** 艾尔斯：手动切换伪装人格和盲视杀手人格。 */
public class AyersRole extends NormalRole {
    public AyersRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
    }
    public static void giveInitialItems(Player player) {
        RoleUtils.insertStackInFreeSlot(player, TMMItems.KNIFE.getDefaultInstance());
    }
    public static boolean isEvil(Player player) {
        return RoleData.getOptional(VtuberRoleData.class, player).map(d -> d.ayersEvil).orElse(false);
    }
    @Override public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> onItemUse(
            Player player, net.minecraft.world.level.Level level, net.minecraft.world.InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        return !isEvil(player) && VtuberRoleSupport.isWeapon(stack.getItem())
                ? net.minecraft.world.InteractionResultHolder.fail(stack)
                : net.minecraft.world.InteractionResultHolder.pass(stack);
    }
    @Override public boolean onUseGun(Player player) { return isEvil(player); }
    @Override public boolean onUseKnife(Player player) { return isEvil(player); }
    @Override public boolean canUseInstinct(Player player) { return false; }
    @Override public boolean haveInstinctNightVision(Player player) { return false; }
    public static void registerSkills() {
        RoleSkill.register(ModRoles.AYERS, RoleSkill.skill(SRE.id("ayers_personality"),
                "skill.noellesroles.ayers.personality", context -> {
                    ServerPlayer player = context.player();
                    var data = RoleData.getNullable(VtuberRoleData.class, player);
                    if (data == null || !GameUtils.isPlayerAliveAndSurvival(player)) return false;
                    data.ayersEvil = !data.ayersEvil;
                    if (data.ayersEvil) player.addEffect(new MobEffectInstance(ModEffects.BLIND_VISION, -1, 0, false, false, true));
                    else player.removeEffect(ModEffects.BLIND_VISION);
                    data.sync();
                    return true;
                }).cooldownSeconds(10).showOnHud(true).build());
    }
    @Override public void onRemove(ServerPlayer player) { player.removeEffect(ModEffects.BLIND_VISION); }
}
