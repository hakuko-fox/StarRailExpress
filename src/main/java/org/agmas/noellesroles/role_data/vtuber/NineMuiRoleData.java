package org.agmas.noellesroles.role_data.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.game.roles.vtuber.VtuberV21Rules;
import org.agmas.noellesroles.init.ModEffects;

/** 玖璃：企鹅充能每秒1%，石化每秒消耗4%，切换后冷却5秒。 */
public class NineMuiRoleData extends SimpleRoleData {
    private int energy = VtuberV21Rules.MAX_STONE_ENERGY;
    private boolean petrified;
    public NineMuiRoleData(RoleDataContext context) { super(context); }
    public boolean isPetrified() { return petrified; }
    public boolean useBlessingSkill(ServerPlayer player, RoleSkillContext context) {
        if (!GameUtils.isPlayerAliveAndSurvival(player) || (!petrified && energy == 0)) return false;
        setPetrified(player, !petrified);
        return true;
    }
    private void setPetrified(ServerPlayer player, boolean value) {
        petrified = value;
        if (!value) {
            player.removeEffect(ModEffects.MOVE_BANED);
            player.removeEffect(ModEffects.INVINCIBLE);
        }
        sync();
    }
    @Override public void clear() {
        if (player instanceof ServerPlayer sp && petrified) setPetrified(sp, false);
    }
    @Override public void serverTick() {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(sp)) return;
        energy = VtuberV21Rules.stoneEnergy(energy, petrified, 1);
        if (petrified && energy == 0) {
            setPetrified(sp, false);
            SREAbilityPlayerComponent.KEY.get(sp).setSkillCooldown(SRE.id("9muimui_blessing"), 5 * 20);
        }
        if (petrified) {
            sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 5, 0, false, false, false));
            sp.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 5, 0, false, false, false));
        }
        if (sp.tickCount % 20 == 0) {
            sp.displayClientMessage(Component.translatable("hud.noellesroles.9muimui.energy", energy / 20), true);
            sync();
        }
    }
    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Energy", energy); tag.putBoolean("Petrified", petrified);
    }
    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        energy = tag.getInt("Energy"); petrified = tag.getBoolean("Petrified");
    }
}
