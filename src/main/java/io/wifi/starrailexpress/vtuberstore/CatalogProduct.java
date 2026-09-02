/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.vtuberstore;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;

public record CatalogProduct(String id, Kind kind, String subtype, String value, String translationKey,
        VtuberStoreRegistry.Rarity rarity, FactionCardType cardType, int defaultPrice) {
    public enum Kind { SKIN, CARD, ROLE_CHOICE_CARD }

    public static CatalogProduct skin(VtuberStoreRegistry.SkinProduct skin) {
        return new CatalogProduct(skin.productId(), Kind.SKIN, skin.skinType(), skin.skinId(),
                skin.translationKey(), skin.rarity(), null, skin.rarity().defaultPrice());
    }

    public static CatalogProduct card(String id, FactionCardType type, int price) {
        return new CatalogProduct(id, Kind.CARD, "card", type.questKey,
                "sre.pass.faction." + type.questKey, null, type, price);
    }

    public static CatalogProduct roleChoiceCard(String id, int price) {
        return new CatalogProduct(id, Kind.ROLE_CHOICE_CARD, "role_choice", "role_choice",
                "sre.backpack.role_choice_card", null, null, price);
    }
}
