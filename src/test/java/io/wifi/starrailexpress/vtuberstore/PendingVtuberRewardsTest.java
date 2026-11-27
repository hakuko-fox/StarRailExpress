package io.wifi.starrailexpress.vtuberstore;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PendingVtuberRewardsTest {
    @Test
    void reloadKeepsUnacknowledgedRewardsInRoundOrder() {
        var pending = new PendingVtuberRewards();
        UUID player = UUID.randomUUID();
        var first = new PendingVtuberRewards.Reward(player, UUID.randomUUID(), 6, true, true);
        var second = new PendingVtuberRewards.Reward(player, UUID.randomUUID(), 1, false, false);
        pending.add(first);
        pending.add(second);
        pending.add(first);

        var reloaded = PendingVtuberRewards.load(pending.save(new CompoundTag(), null), null);
        assertEquals(List.of(first, second), reloaded.forPlayer(player));
        reloaded.acknowledge(first);

        var afterAcknowledgment = PendingVtuberRewards.load(reloaded.save(new CompoundTag(), null), null);
        assertEquals(List.of(second), afterAcknowledgment.forPlayer(player));
        afterAcknowledgment.acknowledge(second);
        assertFalse(afterAcknowledgment.hasPending(player));
    }
}
