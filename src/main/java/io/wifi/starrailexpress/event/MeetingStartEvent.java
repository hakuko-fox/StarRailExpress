package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 会议开始时触发的事件（非拦截型）。
 *
 * <p>Event interface fired when an emergency meeting starts (non-cancellable).
 */
public interface MeetingStartEvent {

    Event<MeetingStartEvent> EVENT = EventFactory.createArrayBacked(MeetingStartEvent.class,
            listeners -> (serverLevel, reporter) -> {
                for (MeetingStartEvent listener : listeners) {
                    listener.onMeetingStart(serverLevel, reporter);
                }
            });

    /**
     * 会议开始时的回调。
     *
     * @param serverLevel 会议所在的服务端世界
     * @param reporter    发起会议的玩家（报警人或摇铃人）
     */
    void onMeetingStart(ServerLevel serverLevel, ServerPlayer reporter);
}
