/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.exmo.sre.nametag;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared title catalog used by both server unlock logic and the client UI. */
public final class NameTagTitleCatalog {
    private static final List<TitleDefinition> TITLES = buildTitles();
    private static final Map<String, TitleDefinition> TITLES_BY_ID = indexTitles();
    private static final Map<String, String> ADMIN_TITLE_ALIASES = Map.of(
            "香港Vtuber", "starrailexpress.nametag.admin_hong_kong_vtuber",
            "台灣Vtuber", "starrailexpress.nametag.admin_taiwan_vtuber",
            "馬來西亞Vtuber", "starrailexpress.nametag.admin_malaysia_vtuber",
            "企劃統籌", "starrailexpress.nametag.admin_project_coordinator");

    private NameTagTitleCatalog() {
    }

    public static List<TitleDefinition> all() {
        return TITLES;
    }

    public static String resolveCommandInput(String input) {
        return ADMIN_TITLE_ALIASES.getOrDefault(input, input);
    }

    public static TitleTier tierOf(String id) {
        TitleDefinition definition = TITLES_BY_ID.get(id);
        return definition == null ? TitleTier.COMMON : definition.tier();
    }

    public static MutableComponent displayText(String id) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        String fallback = resourceLocation == null ? id : resourceLocation.getPath();
        return Component.translatableWithFallback(id, fallback).withColor(tierOf(id).color());
    }

    public static MutableComponent tierLabel(TitleTier tier) {
        return Component.translatable("screen.sre.skins.title_tier." + tier.name().toLowerCase(Locale.ROOT))
                .withColor(tier.color());
    }

    private static List<TitleDefinition> buildTitles() {
        List<TitleDefinition> titles = new ArrayList<>();
        addMilestones(titles, Criterion.KILLER_WINS, "killer_wins", 1, 10, 20, 50, 100, 200, 500, 1000);
        addMilestones(titles, Criterion.POLICE_WINS, "police_wins", 1, 10, 20, 50, 100, 200, 500, 1000);
        addMilestones(titles, Criterion.NEUTRAL_WINS, "neutral_wins", 1, 10, 20, 50, 100, 200, 500, 1000);
        addMilestones(titles, Criterion.GAMES_PLAYED, "games_played", 1, 10, 20, 50, 100, 200, 500, 1000,
                2000, 5000, 10000);

        add(titles, Criterion.KILLER_STREAK, "killer_streak_3", 3, TitleTier.RARE);
        add(titles, Criterion.KILLER_STREAK, "killer_streak_5", 5, TitleTier.EPIC);
        add(titles, Criterion.KILLER_STREAK, "killer_streak_10", 10, TitleTier.MYTHIC);
        add(titles, Criterion.POLICE_STREAK, "police_streak_3", 3, TitleTier.RARE);
        add(titles, Criterion.POLICE_STREAK, "police_streak_5", 5, TitleTier.EPIC);
        add(titles, Criterion.POLICE_STREAK, "police_streak_10", 10, TitleTier.MYTHIC);
        add(titles, Criterion.NEUTRAL_STREAK, "neutral_streak_2", 2, TitleTier.RARE);
        add(titles, Criterion.NEUTRAL_STREAK, "neutral_streak_3", 3, TitleTier.EPIC);
        add(titles, Criterion.NEUTRAL_STREAK, "neutral_streak_5", 5, TitleTier.LEGENDARY);
        add(titles, Criterion.ALL_FACTION_WINS, "all_factions_20", 20, TitleTier.RARE);
        add(titles, Criterion.ALL_FACTION_WINS, "all_factions_100", 100, TitleTier.EPIC);
        add(titles, Criterion.ALL_FACTION_WINS, "all_factions_500", 500, TitleTier.LEGENDARY);
        add(titles, Criterion.ALL_FACTION_WINS, "all_factions_1000", 1000, TitleTier.MYTHIC);
        add(titles, Criterion.LOSS_STREAK, "loss_streak_5", 5, TitleTier.COMMON);
        add(titles, Criterion.LOSS_STREAK, "loss_streak_10", 10, TitleTier.UNCOMMON);
        add(titles, Criterion.LOSS_STREAK, "loss_streak_20", 20, TitleTier.RARE);
        add(titles, Criterion.LOW_WIN_RATE, "low_win_rate_100", 100, TitleTier.COMMON);
        add(titles, Criterion.GAMES_PLAYED, "games_special_1000", 1000, TitleTier.LEGENDARY);
        add(titles, Criterion.GAMES_PLAYED, "games_special_5000", 5000, TitleTier.MYTHIC);
        add(titles, Criterion.GAMES_PLAYED, "games_special_10000", 10000, TitleTier.MYTHIC);
        add(titles, Criterion.FIRST_DEATH, "first_death", 1, TitleTier.COMMON);
        add(titles, Criterion.FIRST_DEATH_STREAK, "first_death_streak_3", 3, TitleTier.UNCOMMON);
        add(titles, Criterion.KILLER_PERFECT_WIN, "killer_perfect_win", 1, TitleTier.EPIC);
        add(titles, Criterion.POLICE_PERFECT_WIN, "police_perfect_win", 1, TitleTier.EPIC);
        add(titles, Criterion.ADMIN_GRANTED, "admin_hong_kong_vtuber", 0, TitleTier.EXCLUSIVE);
        add(titles, Criterion.ADMIN_GRANTED, "admin_taiwan_vtuber", 0, TitleTier.EXCLUSIVE);
        add(titles, Criterion.ADMIN_GRANTED, "admin_malaysia_vtuber", 0, TitleTier.EXCLUSIVE);
        add(titles, Criterion.ADMIN_GRANTED, "admin_project_coordinator", 0, TitleTier.EXCLUSIVE);
        return List.copyOf(titles);
    }

    private static void addMilestones(List<TitleDefinition> titles, Criterion criterion, String prefix,
            int... thresholds) {
        for (int threshold : thresholds) {
            add(titles, criterion, prefix + "_" + threshold, threshold,
                    milestoneTier(criterion, threshold));
        }
    }

    private static TitleTier milestoneTier(Criterion criterion, int threshold) {
        if (criterion == Criterion.GAMES_PLAYED) {
            if (threshold <= 10) return TitleTier.COMMON;
            if (threshold <= 50) return TitleTier.UNCOMMON;
            if (threshold <= 200) return TitleTier.RARE;
            if (threshold <= 500) return TitleTier.EPIC;
            if (threshold <= 2000) return TitleTier.LEGENDARY;
            return TitleTier.MYTHIC;
        }
        if (threshold <= 1) return TitleTier.COMMON;
        if (threshold <= 20) return TitleTier.UNCOMMON;
        if (threshold <= 50) return TitleTier.RARE;
        if (threshold <= 200) return TitleTier.EPIC;
        if (threshold <= 500) return TitleTier.LEGENDARY;
        return TitleTier.MYTHIC;
    }

    private static void add(List<TitleDefinition> titles, Criterion criterion, String suffix, int threshold,
            TitleTier tier) {
        titles.add(new TitleDefinition("starrailexpress.nametag." + suffix, criterion, threshold, tier));
    }

    private static Map<String, TitleDefinition> indexTitles() {
        Map<String, TitleDefinition> titlesById = new HashMap<>();
        for (TitleDefinition title : TITLES) {
            titlesById.put(title.id(), title);
        }
        return Map.copyOf(titlesById);
    }

    public record TitleDefinition(String id, Criterion criterion, int threshold, TitleTier tier) {
    }

    public enum TitleTier {
        COMMON(0xAAAAAA),
        UNCOMMON(0x55FF55),
        RARE(0x55FFFF),
        EPIC(0xAA55FF),
        LEGENDARY(0xFFFF55),
        MYTHIC(0xFF5555),
        EXCLUSIVE(0xFFAA00);

        private final int color;

        TitleTier(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    public enum Criterion {
        KILLER_WINS,
        POLICE_WINS,
        NEUTRAL_WINS,
        GAMES_PLAYED,
        KILLER_STREAK,
        POLICE_STREAK,
        NEUTRAL_STREAK,
        ALL_FACTION_WINS,
        LOSS_STREAK,
        LOW_WIN_RATE,
        FIRST_DEATH,
        FIRST_DEATH_STREAK,
        KILLER_PERFECT_WIN,
        POLICE_PERFECT_WIN,
        ADMIN_GRANTED
    }
}
