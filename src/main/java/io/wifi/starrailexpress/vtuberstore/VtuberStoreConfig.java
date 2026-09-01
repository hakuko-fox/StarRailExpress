/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.vtuberstore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VtuberStoreConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("starrailexpress").resolve("vtuber_store.json");

    public Rewards rewards = new Rewards();
    public Map<String, ProductSetting> products = new LinkedHashMap<>();

    public static VtuberStoreConfig loadAndMergeDefaults() {
        VtuberStoreConfig config = new VtuberStoreConfig();
        boolean shouldWrite = !Files.exists(FILE);
        if (Files.exists(FILE)) {
            try {
                VtuberStoreConfig decoded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                        VtuberStoreConfig.class);
                if (decoded != null) {
                    config = decoded;
                }
            } catch (IOException | RuntimeException exception) {
                SRE.LOGGER.error("Failed to read {}; using in-memory defaults without overwriting it", FILE, exception);
                return defaultsWithCatalog();
            }
        }
        config.normalize();
        for (CatalogProduct product : VtuberStoreManager.registeredProducts()) {
            if (!config.products.containsKey(product.id())) {
                config.products.put(product.id(), new ProductSetting(true, product.defaultPrice()));
                shouldWrite = true;
            }
        }
        if (shouldWrite) {
            config.save();
        }
        return config;
    }

    private static VtuberStoreConfig defaultsWithCatalog() {
        VtuberStoreConfig config = new VtuberStoreConfig();
        for (CatalogProduct product : VtuberStoreManager.registeredProducts()) {
            config.products.put(product.id(), new ProductSetting(true, product.defaultPrice()));
        }
        return config;
    }

    private void normalize() {
        if (rewards == null) {
            rewards = new Rewards();
        }
        rewards.winnerAlive = Math.max(0, rewards.winnerAlive);
        rewards.winnerDead = Math.max(0, rewards.winnerDead);
        rewards.loser = Math.max(0, rewards.loser);
        if (products == null) {
            products = new LinkedHashMap<>();
        }
        products.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null);
        for (Map.Entry<String, ProductSetting> entry : products.entrySet()) {
            ProductSetting setting = entry.getValue();
            CatalogProduct product = VtuberStoreManager.registeredProducts().stream()
                    .filter(candidate -> candidate.id().equals(entry.getKey()))
                    .findFirst().orElse(null);
            setting.enabled = setting.enabled == null ? Boolean.TRUE : setting.enabled;
            setting.price = setting.price == null
                    ? (product == null ? 0 : product.defaultPrice())
                    : Math.max(0, setting.price);
        }
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            SRE.LOGGER.error("Failed to write Vtuber Store config {}", FILE, exception);
        }
    }

    public ProductSetting setting(String productId) {
        return products.get(productId);
    }

    public static final class Rewards {
        public int winnerAlive = 4;
        public int winnerDead = 2;
        public int loser = 1;
    }

    public static final class ProductSetting {
        public Boolean enabled;
        public Integer price;

        public ProductSetting() {
            this.enabled = null;
            this.price = null;
        }

        public ProductSetting(boolean enabled, int price) {
            this.enabled = enabled;
            this.price = price;
        }
    }
}
