package io.wifi.starrailexpress.backpack;

import com.google.gson.Gson;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackpackSavedDataTest {
    @Test
    void localFallbackSurvivesSaveAndReload() {
        UUID playerUuid = UUID.randomUUID();
        BackpackSavedData original = new BackpackSavedData();
        original.put(playerUuid,
                "{\"vtuberCoins\":88,\"cards\":{\"KILLER\":2},\"migrated\":true}");

        CompoundTag tag = original.save(new CompoundTag(), null);
        BackpackSavedData reloaded = BackpackSavedData.load(tag, null);

        BackpackState state = new Gson().fromJson(reloaded.get(playerUuid), BackpackState.class).normalized();
        assertEquals(88, state.vtuberCoins);
        assertEquals(2, state.cards.get(FactionCardType.KILLER));
    }
}
