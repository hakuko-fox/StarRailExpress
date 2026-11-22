package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vtuber.VtuberRoleData;

/** 佳奈：对最近且尚未被影响的玩家施加随机变声。 */
public class KanaRole extends NormalRole {
    public static final List<Holder<MobEffect>> VOICES = List.of(ModEffects.HEAVY_METAL_VOICE,
            ModEffects.VOICE_STUTTER, ModEffects.VOICE_BEEP, ModEffects.VOICE_HELMET,
            ModEffects.VOICE_UNDERWATER, ModEffects.VOICE_REVERB, ModEffects.VOICE_HELIUM);

    public KanaRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(VtuberRoleData::new);
    }

    public static void registerSkills() {
        RoleSkill.register(ModRoles.KANA,
                RoleSkill.skill(SRE.id("kana_voice_changer"), "skill.noellesroles.kana.voice_changer",
                        context -> useVoiceChanger(context.player()))
                        .cooldownSeconds(10).showOnHud(true).build());
    }

    public static boolean useVoiceChanger(ServerPlayer caster) {
        VtuberRoleData data = RoleData.getNullable(VtuberRoleData.class, caster);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(caster)) return false;
        ServerPlayer target = caster.serverLevel().players().stream()
                .filter(p -> p != caster && GameUtils.isPlayerAliveAndSurvival(p))
                .filter(p -> !data.kanaAffected.contains(p.getUUID()))
                .min(Comparator.comparingDouble(caster::distanceToSqr)).orElse(null);
        if (target == null || !VtuberRoleSupport.deduct(caster, 30)) return false;
        target.addEffect(new MobEffectInstance(VOICES.get(caster.getRandom().nextInt(VOICES.size())),
                -1, 0, false, false, true));
        data.kanaAffected.add(target.getUUID());
        return true;
    }

    /** 保留旧封包入口，取消旧版派对与专属刀机制。 */
    public static boolean canUseKanaKnife(Player player) {
        return !player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().hasUUID("KanaKnifeGrant");
    }
    public static void consumeKanaKnife(ServerPlayer player) {}
    public static void finishKanaKnifeAttack(ServerPlayer player) {}
    public static void handleKanaKill(Player player) {}
}
