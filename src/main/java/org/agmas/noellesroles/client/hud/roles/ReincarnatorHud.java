package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.reincarnator.ReincarnatorPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 轮回者 HUD：左下角显示进度/阶段、已收集死因图标行，以及三个技能图标（含冷却与诱导锁定）。
 */
public class ReincarnatorHud {

    private static final ResourceLocation SKILL_BLINK = SRE.id("reincarnator_blink");
    private static final ResourceLocation SKILL_CASTOFF = SRE.id("reincarnator_castoff");
    private static final ResourceLocation SKILL_LURE = SRE.id("reincarnator_lure");

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.REINCARNATOR_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator()) return;

            ReincarnatorPlayerComponent comp = ReincarnatorPlayerComponent.KEY.maybeGet(client.player).orElse(null);
            if (comp == null) return;

            int guiWidth = context.guiWidth();
            int guiHeight = context.guiHeight();
            int color = ModRoles.REINCARNATOR.color();

            // 进度 + 阶段
            Component progress = Component.translatable("hud.noellesroles.reincarnator.progress",
                    comp.deathCausesSeen.size(), comp.requiredCauses, comp.getStage());
            int progressX = 10;
            int progressY = guiHeight - 28;
            context.drawString(client.font, progress, progressX, progressY, color, true);

            // 已收集死因图标行
            int iconY = progressY - 20;
            int iconX = 10;
            for (ResourceLocation cause : comp.deathCausesSeen) {
                Item item = ReincarnatorIcons.icon(cause);
                context.renderFakeItem(item.getDefaultInstance(), iconX, iconY);
                iconX += 18;
            }

            // 技能图标行（冷却灰罩 + 诱导锁）
            int skillY = iconY - 22;
            renderSkill(context, client, comp, 10, skillY, SKILL_BLINK,
                    Items.ENDER_PEARL.getDefaultInstance(), false);
            renderSkill(context, client, comp, 30, skillY, SKILL_CASTOFF,
                    Items.TOTEM_OF_UNDYING.getDefaultInstance(), false);
            renderSkill(context, client, comp, 50, skillY, SKILL_LURE,
                    Items.FERMENTED_SPIDER_EYE.getDefaultInstance(), !comp.isLureUnlocked());
        });
    }

    private static void renderSkill(io.wifi.utils.client.betterrender.FakeGuiGraphics context, Minecraft client,
            ReincarnatorPlayerComponent comp, int x, int y, ResourceLocation skillId, ItemStack icon, boolean locked) {
        context.renderFakeItem(icon, x, y);

        if (locked) {
            // 锁定：暗化 + 显示锁标
            context.fill(x, y, x + 16, y + 16, 0xB0000000);
            context.drawString(client.font, Component.literal("⚿"), x + 4, y + 4, 0xFFFF5555, true);
            return;
        }

        int cooldown = 0;
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.maybeGet(client.player).orElse(null);
        if (ability != null) {
            SREAbilityPlayerComponent.SkillState state = ability.getSkillState(skillId);
            if (state != null) cooldown = state.cooldown;
        }
        if (cooldown > 0) {
            context.fill(x, y, x + 16, y + 16, 0x80000000);
            context.drawString(client.font, Component.literal(String.valueOf(cooldown / 20)),
                    x + 3, y + 4, 0xFFFFFFFF, true);
        }
    }
}
