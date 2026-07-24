package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import org.agmas.noellesroles.client.NoellesrolesClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

/**
 * 通用自定义职业 HUD
 * 和其他职业 HUD 一样，通过 CommonHudRenderCallback 注册，渲染时自行判断角色
 */
@Environment(EnvType.CLIENT)
public class CustomRoleHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null)
                return;
            if (!SREClient.isPlayerAliveAndInSurvival())
                return;

            // 判断当前玩家是否为启用了技能的自定义职业
            var role = SREClient.getCachedPlayerRole();
            if (role == null || !"customrole".equals(role.identifier().getNamespace())
                    || !(role instanceof CustomNormalRole))
                return;
            if (!RoleSkill.isRegistered(role))
                return; // enableAbility=false 的角色不会注册技能

            CustomRoleData data = CustomRoleLoader.getCustomRoleData(role.identifier().getPath());
            if (data == null)
                return;

            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(client.player);
            int cooldownTicks = ability.cooldown;
            var font = client.font;

            // 渲染在屏幕右下角
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 30;

            // 计算当前选中技能名称（与 RoleSkill 定义顺序一致）
            var effective = data.getEffectiveSkills();
            int selected = Math.floorMod(ability.getSelectedSkill(), Math.max(1, effective.size()));
            String skillName = effective.isEmpty() ? "" : (effective.get(selected).name == null ? "" : effective.get(selected).name);
            Component nameComponent = skillName.isEmpty()
                    ? Component.translatable("hud.sre.custom_role.skill_default")
                    : Component.literal(skillName);

            if (data.enableSkillSwitch && !data.skillModules.isEmpty()) {
                // 启用切换技能：显示「按 <切换键> 切换技能: <技能名>」
                Component switchHint = Component.translatable("hud.sre.custom_role.skill_switch_hint",
                        NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage(),
                        nameComponent);
                guiGraphics.drawString(font, switchHint, x - font.width(switchHint), y - 12, 0xFFFFAA);
            } else {
                // 单技能：直接在冷却上方显示技能名称
                guiGraphics.drawString(font, nameComponent, x - font.width(nameComponent), y - 12, 0xFFFFFF);
            }

            if (cooldownTicks <= 0) {
                Component readyText = Component.translatable("hud.sre.custom_role.skill_ready");
                guiGraphics.drawString(font, readyText, x - font.width(readyText), y, 0x55FF55);
            } else {
                double seconds = cooldownTicks / 20.0;
                Component cooldownText = Component.translatable("hud.sre.custom_role.skill_cooldown",
                        String.format("%.1f", seconds));
                guiGraphics.drawString(font, cooldownText, x - font.width(cooldownText), y, 0xFF5555);
            }
        });
    }
}
