/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.vtuberstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DefaultVtuberStoreRegistry implements VtuberStoreRegistry {
    static final DefaultVtuberStoreRegistry INSTANCE = new DefaultVtuberStoreRegistry();
    private static final Logger LOGGER = LoggerFactory.getLogger("starrailexpress-vtuber-store-registry");
    private final Map<String, SkinProduct> skins = new LinkedHashMap<>();

    private DefaultVtuberStoreRegistry() {
    }

    @Override
    public synchronized boolean registerSkin(String skinType, String skinId, Rarity rarity) {
        if (!validSegment(skinType) || !validSegment(skinId) || rarity == null) {
            LOGGER.error("Rejected invalid Vtuber Store skin registration: type={}, id={}", skinType, skinId);
            return false;
        }
        String type = skinType.toLowerCase(Locale.ROOT);
        String id = skinId.toLowerCase(Locale.ROOT);
        String productId = "skin:" + type + ":" + id;
        if (skins.containsKey(productId)) {
            LOGGER.error("Rejected duplicate Vtuber Store product id {}", productId);
            return false;
        }
        skins.put(productId, new SkinProduct(productId, type, id, rarity));
        return true;
    }

    @Override
    public synchronized List<SkinProduct> getRegisteredSkins() {
        return List.copyOf(new ArrayList<>(skins.values()));
    }

    private static boolean validSegment(String value) {
        return value != null && value.matches("[a-z0-9_\\-]+") && value.length() <= 64;
    }
}
