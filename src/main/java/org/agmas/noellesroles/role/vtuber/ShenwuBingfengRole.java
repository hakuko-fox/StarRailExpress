package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.TrueFalseResult;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.ShenwuBingfengRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

public class ShenwuBingfengRole extends CustomWinnerRole {
    public ShenwuBingfengRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(ShenwuBingfengRoleData::new);
    }

    @Override
    public WinStatus checkWin(ServerPlayer player,
            WinStatus winStatus) {
        return WinStatus.NOT_MODIFY;
    }

    @Override
    public boolean onUseGun(Player player) { return false; }

    @Override
    public boolean onUseKnife(Player player) { return false; }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return VtuberRoleSupport.isWeapon(item.getItem()) ? TrueFalseResult.FALSE : TrueFalseResult.PASS;
    }

    public static void giveInitialItems(Player player) {

        boolean sheriffVariant = player.getRandom().nextFloat() < 0.70F;
        RoleUtils.insertStackInFreeSlot(player,
                sheriffVariant
                ? ModItems.FAKE_REVOLVER.getDefaultInstance()
                : ModItems.FAKE_KNIFE.getDefaultInstance());
        player.displayClientMessage(Component.translatable(sheriffVariant
                ? "message.noellesroles.kamikiri_ice.sheriff_variant"
                : "message.noellesroles.kamikiri_ice.killer_variant"), true);

    }

    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
                    if (!(victim instanceof ServerPlayer shenwu)
                        || !SREGameWorldComponent.KEY.get(victim.level()).isRole(shenwu, ModRoles.SHENWU_BINGFENG)
                    || !(killer instanceof ServerPlayer)) {
                        return true;
                    }
                    var data = RoleData.getNullable(ShenwuBingfengRoleData.class, shenwu);
                    if (data == null) return true;
                    Set<ShenwuDamageGroup> groups = data.damageGroups;
                    if (groups.size() >= ShenwuDamageGroup.values().length
                    && hasNonKillerPlayerBesides(shenwu)) {
                        RoleUtils.customWinnerWin(shenwu.serverLevel(),
                        ModRoles.SHENWU_BINGFENG.identifier().getPath(), ModRoles.SHENWU_BINGFENG.color());
                        return false;
                    }
                    ShenwuDamageGroup attackerGroup = getShenwuDamageGroup(
                    SREGameWorldComponent.KEY.get(victim.level()).getRole(killer));
                    if (attackerGroup == null || !groups.add(attackerGroup)) {
                        return true;
                    }
                    shenwu.displayClientMessage(Component.translatable("message.noellesroles.kamikiri_ice.fatal_saved"), true);
                    return false;
                });
    }

    private static ShenwuDamageGroup getShenwuDamageGroup(SRERole attackerRole) {
        if (attackerRole == null) {
            return null;
        }
        if (attackerRole.isKiller() && !attackerRole.isNeutrals()) {
            return ShenwuDamageGroup.KILLER;
        }
        if (attackerRole.isVigilanteTeam()) {
            return ShenwuDamageGroup.SHERIFF;
        }
        if (attackerRole.isInnocent() && !attackerRole.isNeutrals()) {
            return ShenwuDamageGroup.CIVILIAN;
        }
        return null;
    }

    private static boolean hasNonKillerPlayerBesides(ServerPlayer shenwu) {
        var game = SREGameWorldComponent.KEY.get(shenwu.level());
        return shenwu.level().players().stream()
                .filter(player -> player != shenwu && GameUtils.isPlayerAliveAndSurvival(player))
                .map(game::getRole)
                .anyMatch(role -> role != null && !(role.isKiller() && !role.isNeutrals()));
    }

    public enum ShenwuDamageGroup { CIVILIAN, SHERIFF, KILLER }

}
