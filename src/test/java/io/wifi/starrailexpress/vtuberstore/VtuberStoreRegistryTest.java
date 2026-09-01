package io.wifi.starrailexpress.vtuberstore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtuberStoreRegistryTest {
    @Test
    void registrationUsesStableIdAndRejectsDuplicates() {
        VtuberStoreRegistry registry = VtuberStoreRegistry.getInstance();
        String skinId = "registry_test_" + System.nanoTime();

        assertTrue(registry.registerSkin("knife", skinId, VtuberStoreRegistry.Rarity.RARE));
        assertFalse(registry.registerSkin("knife", skinId, VtuberStoreRegistry.Rarity.RARE));

        VtuberStoreRegistry.SkinProduct product = registry.getRegisteredSkins().stream()
                .filter(candidate -> candidate.skinId().equals(skinId))
                .findFirst().orElseThrow();
        assertEquals("skin:knife:" + skinId, product.productId());
        assertEquals(14, product.rarity().defaultPrice());
    }

    @Test
    void registrationRejectsUnsafeSegments() {
        assertFalse(VtuberStoreRegistry.getInstance()
                .registerSkin("knife", "../invalid", VtuberStoreRegistry.Rarity.COMMON));
    }

    @Test
    void defaultPricesMatchEconomyPlan() {
        assertEquals(8, VtuberStoreRegistry.Rarity.COMMON.defaultPrice());
        assertEquals(14, VtuberStoreRegistry.Rarity.RARE.defaultPrice());
        assertEquals(22, VtuberStoreRegistry.Rarity.EPIC.defaultPrice());
        assertEquals(32, VtuberStoreRegistry.Rarity.LEGENDARY.defaultPrice());
    }
}
