/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.vtuberstore.VtuberStoreManager;

import java.util.List;

public final class ClientVtuberStoreCache {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile List<VtuberStoreManager.CatalogEntry> products = List.of();

    private ClientVtuberStoreCache() {
    }

    public static void update(String json) {
        try {
            VtuberStoreManager.CatalogSnapshot snapshot = GSON.fromJson(json,
                    VtuberStoreManager.CatalogSnapshot.class);
            products = snapshot == null || snapshot.products() == null ? List.of() : List.copyOf(snapshot.products());
        } catch (RuntimeException ignored) {
            products = List.of();
        }
    }

    public static List<VtuberStoreManager.CatalogEntry> products() {
        return products;
    }

    public static void clear() {
        products = List.of();
    }
}
