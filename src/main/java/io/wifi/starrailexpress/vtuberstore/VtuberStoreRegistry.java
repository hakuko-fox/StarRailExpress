/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.vtuberstore;

import java.util.List;

/** Public seam used by optional add-ons to expose sellable item skins. */
public interface VtuberStoreRegistry {
    static VtuberStoreRegistry getInstance() {
        return DefaultVtuberStoreRegistry.INSTANCE;
    }

    boolean registerSkin(String skinType, String skinId, Rarity rarity);

    List<SkinProduct> getRegisteredSkins();

    enum Rarity {
        COMMON(8), RARE(14), EPIC(22), LEGENDARY(32);

        private final int defaultPrice;

        Rarity(int defaultPrice) {
            this.defaultPrice = defaultPrice;
        }

        public int defaultPrice() {
            return defaultPrice;
        }
    }

    record SkinProduct(String productId, String skinType, String skinId, Rarity rarity) {
        public String translationKey() {
            return "screen.sre.skins." + skinType + "." + skinId + ".name";
        }
    }
}
