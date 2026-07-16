package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.client.gui.screen.gamemode.volunteer.VolunteerDraftScreen;
import io.wifi.starrailexpress.game.modes.funny.volunteer.VolunteerDraftState.Phase;
import io.wifi.starrailexpress.network.packet.VolunteerDraftSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class VolunteerModeClientReceiver {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(VolunteerDraftSyncS2CPacket.TYPE, (packet, context) -> {
            context.client().execute(() -> {
                VolunteerCache.updateFromPacket(packet);
                Minecraft client = context.client();
                Phase phase = packet.phase();

                switch (phase) {
                    case COMMIT, ADJUST -> {
                        if (client.screen instanceof VolunteerDraftScreen) {
                            // 已在界面中，不做重复打开（若有数据更新可调用screen.refresh()）
                        } else {
                            client.setScreen(new VolunteerDraftScreen());
                        }
                    }
                    case RESULT -> {
                        // 结果阶段：若当前是VolunteerDraftScreen则直接更新结果，否则打开新界面
                        if (client.screen instanceof VolunteerDraftScreen screen) {
                            screen.updateResult(); // 需要Screen提供该方法
                        } else {
                            VolunteerDraftScreen resultScreen = new VolunteerDraftScreen();
                            resultScreen.updateResult(); // 强制立即加载结果数据
                            client.setScreen(resultScreen);
                        }
                    }
                    case WAITING -> {
                        // 等待阶段：可以关闭现有界面或显示等待提示
                        if (client.screen instanceof VolunteerDraftScreen) {
                            client.screen.onClose();
                        }
                    }
                }
            });
        });
    }
}
