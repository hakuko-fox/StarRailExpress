package io.wifi.starrailexpress.compat;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import io.wifi.starrailexpress.SRE;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TrainVoicePlugin implements VoicechatPlugin {
    public static final UUID GROUP_ID = UUID.randomUUID();
    public static VoicechatServerApi SERVER_API;
    public static VoicechatClientApi CLIENT_API;
    
    public static Group GROUP;

    public static boolean isVoiceChatMissing() {
        return SERVER_API == null;
    }

    public static boolean isPlayerInGroup(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return false;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null) {
            if (GROUP == null)
                return false;
            if (GROUP != null) {
                return connection.isInGroup();
            }
        }
        return false;
    }

    public static void addPlayer(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null) {
            if (GROUP == null)
                GROUP = SERVER_API.groupBuilder().setHidden(true).setId(GROUP_ID).setName("Train Spectators")
                        .setPersistent(true).setType(Group.Type.OPEN).build();
            if (GROUP != null)
                connection.setGroup(GROUP);
        }
    }

    public static void resetPlayer(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null)
            connection.setGroup(null);
    }

    @Override
    public void registerEvents(@NotNull EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            SERVER_API = event.getVoicechat();
        });
        registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
            CLIENT_API = event.getVoicechat();
        });
        // 会议系统：语音活动 → 标记发言者（镜头对准正在说话的人）
        registration.registerEvent(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender != null && sender.getPlayer() != null) {
                net.exmo.sre.meeting.MeetingManager.onVoiceActivity(sender.getPlayer().getUuid());
            }
        });
    }

    @Override
    public String getPluginId() {
        return SRE.MOD_ID;
    }
}