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
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.VtuberStoreCatalogPayload;
import io.wifi.starrailexpress.network.VtuberStorePurchaseResultPayload;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.util.SREPlayerUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VtuberStoreManager {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<ResourceKey<Level>, UUID> ACTIVE_ROUNDS = new HashMap<>();
    private static volatile VtuberStoreConfig config = new VtuberStoreConfig();

    private VtuberStoreManager() {
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            config = VtuberStoreConfig.loadAndMergeDefaults();
            warnUnknownConfigProducts();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE_ROUNDS.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendCatalog(handler.getPlayer()));
        OnGameStarted.EVENT.register(world -> ACTIVE_ROUNDS.put(world.dimension(), UUID.randomUUID()));
    }

    public static List<CatalogProduct> registeredProducts() {
        List<CatalogProduct> products = new ArrayList<>();
        for (VtuberStoreRegistry.SkinProduct skin : VtuberStoreRegistry.getInstance().getRegisteredSkins()) {
            products.add(CatalogProduct.skin(skin));
        }
        products.add(CatalogProduct.card("card:civilian", FactionCardType.CIVILIAN, 6));
        products.add(CatalogProduct.card("card:neutral", FactionCardType.NEUTRAL, 9));
        products.add(CatalogProduct.card("card:neutral_for_killer", FactionCardType.NEUTRAL_FOR_KILLER, 12));
        products.add(CatalogProduct.card("card:killer", FactionCardType.KILLER, 16));
        return List.copyOf(products);
    }

    public static void sendCatalog(ServerPlayer player) {
        ServerPlayNetworking.send(player, new VtuberStoreCatalogPayload(GSON.toJson(snapshot())));
    }

    public static void handlePurchase(ServerPlayer player, String productId) {
        if (SREGameWorldComponent.KEY.get(player.serverLevel()).isRunning()) {
            fail(player, productId, "message.sre.vtuber_store.game_running");
            return;
        }
        if (!BackpackManager.isLoaded(player.getUUID())) {
            fail(player, productId, "message.sre.vtuber_store.not_loaded");
            return;
        }
        CatalogProduct product = findProduct(productId);
        VtuberStoreConfig.ProductSetting setting = config.setting(productId);
        if (product == null || setting == null || !Boolean.TRUE.equals(setting.enabled)) {
            fail(player, productId, "message.sre.vtuber_store.unavailable");
            return;
        }

        boolean purchased;
        if (product.kind() == CatalogProduct.Kind.SKIN) {
            if (!ItemSkinManager.getSkins(product.subtype()).containsKey(product.value())) {
                fail(player, productId, "message.sre.vtuber_store.unavailable");
                return;
            }
            if (ownsSkin(player, product.subtype(), product.value())) {
                fail(player, productId, "message.sre.vtuber_store.already_owned");
                return;
            }
            purchased = BackpackManager.tryBuyStoreSkin(player, product.subtype(), product.value(), setting.price);
        } else {
            purchased = BackpackManager.tryBuyFactionCard(player, product.cardType(), setting.price);
        }

        if (!purchased) {
            fail(player, productId, "message.sre.vtuber_store.insufficient");
            return;
        }
        Component name = Component.translatable(product.translationKey());
        player.sendSystemMessage(Component.translatable("message.sre.vtuber_store.purchased", name, setting.price));
        player.displayClientMessage(Component.translatable("message.sre.vtuber_store.purchased", name, setting.price),
                true);
        int cardCount = product.kind() == CatalogProduct.Kind.CARD
                ? BackpackManager.getCardCount(player, product.cardType())
                : -1;
        ServerPlayNetworking.send(player, new VtuberStorePurchaseResultPayload(true, productId,
                "message.sre.vtuber_store.purchased", BackpackManager.getVtuberCoins(player), cardCount));
    }

    public static boolean ownsSkin(ServerPlayer player, String skinType, String skinId) {
        return BackpackManager.ownsStoreSkin(player, skinType, skinId)
                || SREPlayerSkinsComponent.KEY.get(player).isSkinUnlockedForItemType(skinType, skinId)
                || PlayerEconomyManager.isSkinUnlockedForItemType(player, skinType, skinId);
    }

    public static void rewardPlayerForRound(ServerLevel world, GameMode gameMode,
            SREGameRoundEndComponent roundEnd, SREGameWorldComponent gameComponent,
            ServerPlayer player, SRERole role, boolean winner) {
        if (roundEnd.getWinStatus() == GameUtils.WinStatus.NONE || role == null
                || gameComponent.getStartingPlayerCount() < gameMode.minPlayerCount) {
            return;
        }
        UUID roundId = ACTIVE_ROUNDS.computeIfAbsent(world.dimension(), ignored -> UUID.randomUUID());
        boolean alive = SREPlayerUtils.isPlayerAlive(world, player.getUUID());
        int amount = winner
                ? (alive ? config.rewards.winnerAlive : config.rewards.winnerDead)
                : config.rewards.loser;
        if (!BackpackManager.isLoaded(player.getUUID())) {
            SRE.LOGGER.warn("Skipped Vtuber Coin reward for {} in round {} because backpack data is not loaded",
                    player.getUUID(), roundId);
            return;
        }
        if (BackpackManager.awardVtuberCoins(player, roundId, amount)) {
            Component message = Component.translatable("message.sre.vtuber_store.round_reward", amount);
            player.sendSystemMessage(message);
            player.displayClientMessage(message, true);
        }
    }

    private static CatalogSnapshot snapshot() {
        List<CatalogEntry> entries = new ArrayList<>();
        for (CatalogProduct product : registeredProducts()) {
            VtuberStoreConfig.ProductSetting setting = config.setting(product.id());
            if (setting == null) {
                continue;
            }
            entries.add(new CatalogEntry(product.id(), product.kind().name(), product.subtype(), product.value(),
                    product.translationKey(), product.rarity() == null ? "" : product.rarity().name(),
                    Boolean.TRUE.equals(setting.enabled), setting.price));
        }
        return new CatalogSnapshot(entries);
    }

    private static CatalogProduct findProduct(String id) {
        if (id == null || id.length() > 192) {
            return null;
        }
        for (CatalogProduct product : registeredProducts()) {
            if (product.id().equals(id)) {
                return product;
            }
        }
        return null;
    }

    private static void warnUnknownConfigProducts() {
        Map<String, Boolean> known = new LinkedHashMap<>();
        registeredProducts().forEach(product -> known.put(product.id(), Boolean.TRUE));
        for (String id : config.products.keySet()) {
            if (!known.containsKey(id)) {
                SRE.LOGGER.warn("Ignoring unregistered Vtuber Store product {}", id);
            }
        }
    }

    private static void fail(ServerPlayer player, String productId, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
        ServerPlayNetworking.send(player, new VtuberStorePurchaseResultPayload(false,
                productId == null ? "" : productId, translationKey,
                BackpackManager.isLoaded(player.getUUID()) ? BackpackManager.getVtuberCoins(player) : 0, -1));
    }

    public record CatalogSnapshot(List<CatalogEntry> products) {
    }

    public record CatalogEntry(String id, String kind, String subtype, String value, String translationKey,
            String rarity, boolean enabled, int price) {
    }
}
