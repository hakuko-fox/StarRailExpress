package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnPlayerUsedSkill {

    Event<OnPlayerUsedSkill> EVENT = createArrayBacked(OnPlayerUsedSkill.class,
            listeners -> (player) -> {
                for (OnPlayerUsedSkill listener : listeners) {
                    if(listener.onPlayerUsedSkill(player)){
                        return true;
                    }
                }
                return false;
            });

    boolean onPlayerUsedSkill(ServerPlayer player);
}
