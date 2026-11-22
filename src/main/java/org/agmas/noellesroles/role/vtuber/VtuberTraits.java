package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.GameConstants;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.MoneyUtils;

/** v2.1 共同种族被动，只有列出的角色会应用。 */
public final class VtuberTraits {
    private static final Set<String> CATS = Set.of("xianmiao", "mochen", "meowlen", "yozora", "yoru", "ziyuan");
    private static final Set<String> FOXES = Set.of("amimi", "fu_tai", "yuyue", "blood_fox", "hakukofox");
    private static final Set<String> DEMONS = Set.of("xiaoye", "tinalis", "ayers");
    private static final Set<String> ELVES = Set.of("youjin", "kamikiri_ice");
    private VtuberTraits() {}
    private static String role(Player player) {
        SRERole role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        return role == null ? "" : role.identifier().getPath();
    }
    public static boolean isDemon(Player player) { return DEMONS.contains(role(player)); }
    public static boolean isFox(SRERole role) { return role != null && FOXES.contains(role.identifier().getPath()); }
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 5 != 0) return;
        String id = role(player);
        if (CATS.contains(id)) effect(player, ModEffects.FOOTSTEP_VANISH);
        if (ELVES.contains(id)) effect(player, ModEffects.FAIRYLAND_FILTER);
        if (id.equals("halic")) effect(player, ModEffects.VOICE_ROBOT);
        if (id.equals("baiyu")) {
            effect(player, ModEffects.VOICE_CHORUS);
            effect(player, ModEffects.VOICE_ECHO);
        }
        if (id.equals("fu_tai")) effect(player, ModEffects.SKIN_MASK);
        if (id.equals("ayers") && AyersRole.isEvil(player)) {
            for (var stack : player.getInventory().items) if (VtuberRoleSupport.isWeapon(stack.getItem())) player.getCooldowns().removeCooldown(stack.getItem());
        }
    }
    private static void effect(ServerPlayer player, Holder<MobEffect> effect) {
        player.addEffect(new MobEffectInstance(effect, 10, 0, false, false, false));
    }
    public static void catTaskBonus(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        var mode = SREGameWorldComponent.KEY.get(sp.level()).getGameMode();
        if (mode != null && !mode.shouldGrantDefaultQuestMoneyOnFinish()) return;
        int streak = SREPlayerTaskComponent.KEY.get(sp).taskStreak;
        int reward = SREConfig.instance().civilianTaskReward + Math.min(streak * GameConstants.STREAK_BONUS_PER_LEVEL, GameConstants.MAX_STREAK_BONUS);
        MoneyUtils.addToBalance(sp, reward / 2);
    }
}
