package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnGetInstinctHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.diviner.DivinerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Map;
import java.util.UUID;

/**
 * 占卜家 HUD：把被揭示杀手的头像悬浮在屏幕对应方向的边缘，并标注距离与剩余揭示时间。
 *
 * <p>同时注册 {@link OnGetInstinctHighlight} 监听：当本地玩家是占卜家、且目标处于被揭示状态时返回
 * 占卜家颜色，使该杀手仅在占卜师自己客户端发光（per-viewer 透视）。其他玩家客户端不会命中此分支，
 * 因此看不到发光。
 */
public class DivinerHud {

    public static void register() {
        // per-viewer 发光：仅占卜师本人客户端为被揭示杀手着色描边
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player tp)) {
                return -1;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || SREClient.gameComponent == null) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(mc.player, ModRoles.DIVINER)) {
                return -1;
            }
            DivinerPlayerComponent comp = DivinerPlayerComponent.KEY.get(mc.player);
            if (comp != null && comp.isRevealed(tp.getUUID())) {
                return ModRoles.DIVINER.color();
            }
            return -1;
        });

        RoleHudRenderCallback.EVENT.register(ModRoles.DIVINER_ID, (context, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer self = mc.player;
            if (self == null) {
                return;
            }
            DivinerPlayerComponent comp = DivinerPlayerComponent.KEY.get(self);
            if (comp == null || comp.revealedKillers.isEmpty()) {
                return;
            }

            final Font font = mc.font;
            int screenW = context.guiWidth();
            int screenH = context.guiHeight();
            int centerX = screenW / 2;
            int centerY = screenH / 2;
            int radius = Math.min(centerX, centerY) - 40;

            Vec3 eye = self.getEyePosition();

            for (Map.Entry<UUID, DivinerPlayerComponent.RevealedKiller> entry : comp.revealedKillers.entrySet()) {
                UUID id = entry.getKey();
                DivinerPlayerComponent.RevealedKiller info = entry.getValue();

                // 位置：优先使用客户端已加载实体的实时坐标，远处用同步坐标
                Vec3 targetPos;
                Player live = mc.level == null ? null : mc.level.getPlayerByUUID(id);
                if (live != null) {
                    targetPos = live.position().add(0, live.getBbHeight() * 0.5, 0);
                } else {
                    targetPos = new Vec3(info.x, info.y + 1.0, info.z);
                }

                Vec3 dir = targetPos.subtract(eye);
                double distance = dir.length();

                // 与 WaypointHUD.renderScreenIndicator 一致的屏幕方向映射
                float angle = (float) Math.atan2(dir.z, dir.x);
                angle -= self.getYRot() * Mth.DEG_TO_RAD;
                angle += Mth.PI;

                int ex = (int) (centerX + Math.cos(angle) * radius);
                int ey = (int) (centerY + Math.sin(angle) * radius);
                ex = Mth.clamp(ex, 20, screenW - 20);
                ey = Mth.clamp(ey, 20, screenH - 20);

                int faceSize = 16;
                int fx = ex - faceSize / 2;
                int fy = ey - faceSize / 2;

                // 头像背景框（紫水晶/暗红描边以示警示）
                context.fill(fx - 2, fy - 2, fx + faceSize + 2, fy + faceSize + 2, 0xCC4B0082);
                PlayerInfo pInfo = self.connection == null ? null : self.connection.getPlayerInfo(id);
                if (pInfo != null) {
                    context.drawPlayerFace(pInfo.getSkin().texture(), fx, fy, faceSize);
                }

                // 剩余时间（头像上方）
                int secs = (info.ticksLeft + 19) / 20;
                String t = secs + "s";
                int tw = font.width(t);
                context.drawString(font, t, ex - tw / 2, fy - font.lineHeight - 1, 0xFFFFD0D0, true);

                // 距离（头像下方）
                String dist = String.format("%.0fm", distance);
                int dw = font.width(dist);
                context.drawString(font, dist, ex - dw / 2, fy + faceSize + 3, 0xFFFF5555, true);
            }
        });
    }
}
