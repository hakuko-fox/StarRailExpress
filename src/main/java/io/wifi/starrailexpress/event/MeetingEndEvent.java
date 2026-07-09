package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;

/**
 * 会议结束时触发的事件（非拦截型）。
 *
 * <p>Event interface fired when an emergency meeting ends (non-cancellable).
 */
public interface MeetingEndEvent {

    Event<MeetingEndEvent> EVENT = EventFactory.createArrayBacked(MeetingEndEvent.class,
            listeners -> (serverLevel) -> {
                for (MeetingEndEvent listener : listeners) {
                    listener.onMeetingEnd(serverLevel);
                }
            });

    /**
     * 会议结束时的回调。
     *
     * @param serverLevel 会议所在的服务端世界
     */
    void onMeetingEnd(ServerLevel serverLevel);
}
