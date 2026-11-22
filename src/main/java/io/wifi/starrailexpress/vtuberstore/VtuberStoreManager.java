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
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.data.PlayerSkinEconomyManager;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.VtuberStoreCatalogPayload;
import io.wifi.starrailexpress.network.VtuberStorePurchaseResultPayload;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VtuberStoreManager {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<ResourceKey<Level>, RewardRound> ACTIVE_ROUNDS = new HashMap<>();
    private static final Map<UUID, LinkedHashMap<UUID, PendingRoundReward>> PENDING_ROUND_REWARDS = new HashMap<>();
    private static volatile VtuberStoreConfig config = new VtuberStoreConfig();

    private VtuberStoreManager() {
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            config = VtuberStoreConfig.loadAndMergeDefaults();
            warnUnknownConfigProducts();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE_ROUNDS.clear();
            PENDING_ROUND_REWARDS.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendCatalog(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(VtuberStoreManager::processPendingRewards);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE_ROUNDS.clear();
            PENDING_ROUND_REWARDS.clear();
        });
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
        products.add(CatalogProduct.roleChoiceCard("card:role_choice", 40));
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
        } else if (product.kind() == CatalogProduct.Kind.CARD) {
            purchased = BackpackManager.tryBuyFactionCard(player, product.cardType(), setting.price);
        } else {
            purchased = BackpackManager.tryBuyRoleChoiceCard(player, setting.price);
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
                : product.kind() == CatalogProduct.Kind.ROLE_CHOICE_CARD
                        ? BackpackManager.getRoleChoiceCards(player)
                        : -1;
        ServerPlayNetworking.send(player, new VtuberStorePurchaseResultPayload(true, productId,
                "message.sre.vtuber_store.purchased", BackpackManager.getVtuberCoins(player), cardCount));
    }

    public static boolean ownsSkin(ServerPlayer player, String skinType, String skinId) {
        return BackpackManager.ownsStoreSkin(player, skinType, skinId)
                || SREPlayerSkinsComponent.KEY.get(player).isSkinUnlockedForItemType(skinType, skinId)
                || PlayerSkinEconomyManager.isSkinUnlockedForItemType(player, skinType, skinId);
    }

    public static void beginRound(ServerLevel world, List<ServerPlayer> players) {
        var participants = new HashSet<UUID>();
        players.forEach(player -> participants.add(player.getUUID()));
        ACTIVE_ROUNDS.put(world.dimension(), new RewardRound(UUID.randomUUID(), Set.copyOf(participants)));
    }

    public static void rewardRound(ServerLevel world, SREGameRoundEndComponent roundEnd) {
        RewardRound round = ACTIVE_ROUNDS.remove(world.dimension());
        if (round == null) return;
        for (var result : roundEnd.players) {
            UUID playerId = result.player.getId();
            if (!round.canReward(playerId, roundEnd.getWinStatus())) continue;
            boolean winner = result.hasWin();
            boolean alive = !result.wasDead();
            int amount = config.rewards.amountForRound(winner, alive);
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || !BackpackManager.isLoaded(playerId)) {
                queuePendingReward(playerId, round.id(), amount, winner, alive);
            } else {
                awardRoundReward(player, round.id(), amount, winner, alive);
            }
        }
    }

    record RewardRound(UUID id, Set<UUID> participants) {
        boolean canReward(UUID playerId, GameUtils.WinStatus status) {
            return status != GameUtils.WinStatus.NONE && participants.contains(playerId);
        }
    }

    private static void queuePendingReward(UUID playerUuid, UUID roundId, int baseAmount, boolean winner,
            boolean alive) {
        PENDING_ROUND_REWARDS.computeIfAbsent(playerUuid, ignored -> new LinkedHashMap<>())
                .putIfAbsent(roundId, new PendingRoundReward(baseAmount, winner, alive));
    }

    private static void processPendingRewards(MinecraftServer server) {
        var playerEntries = PENDING_ROUND_REWARDS.entrySet().iterator();
        while (playerEntries.hasNext()) {
            var playerEntry = playerEntries.next();
            ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null || !BackpackManager.isLoaded(player.getUUID())) {
                continue;
            }
            var rewards = playerEntry.getValue();
            var rewardEntries = rewards.entrySet().iterator();
            while (rewardEntries.hasNext()) {
                var rewardEntry = rewardEntries.next();
                PendingRoundReward reward = rewardEntry.getValue();
                awardRoundReward(player, rewardEntry.getKey(), reward.baseAmount(), reward.winner(), reward.alive());
                rewardEntries.remove();
            }
            if (rewards.isEmpty()) {
                playerEntries.remove();
            }
        }
    }

    private static void awardRoundReward(ServerPlayer player, UUID roundId, int baseAmount, boolean winner,
            boolean alive) {
        int amount = BackpackManager.awardVtuberCoinsForRound(player, roundId, baseAmount, winner);
        if (amount >= 0) {
            String messageKey = winner
                    ? (alive ? "message.sre.vtuber_store.round_reward.winner_alive"
                            : "message.sre.vtuber_store.round_reward.winner_dead")
                    : (alive ? "message.sre.vtuber_store.round_reward.loser_alive"
                            : "message.sre.vtuber_store.round_reward.loser_dead");
            Component message = Component.translatable(messageKey, amount);
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
        return new CatalogSnapshot(entries,
                new RewardSummary(config.rewards.winnerAlive, config.rewards.winnerDead,
                        config.rewards.loserAlive, config.rewards.loserDead));
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

    public record CatalogSnapshot(List<CatalogEntry> products, RewardSummary rewards) {
    }

    public record RewardSummary(int winnerAlive, int winnerDead, int loserAlive, int loserDead) {
    }

    public record CatalogEntry(String id, String kind, String subtype, String value, String translationKey,
            String rarity, boolean enabled, int price) {
    }

    private record PendingRoundReward(int baseAmount, boolean winner, boolean alive) {
    }
}
