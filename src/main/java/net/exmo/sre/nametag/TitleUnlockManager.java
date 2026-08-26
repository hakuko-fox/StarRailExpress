/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.exmo.sre.nametag;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.stats.PlayerStats;
import io.wifi.starrailexpress.stats.PlayerStatsManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Grants persistent titles after a recorded round has settled. */
public final class TitleUnlockManager {
    private TitleUnlockManager() {
    }

    public static void processRound(ServerLevel world, SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent gameComponent) {
        UUID firstDeath = findFirstDeath();
        Map<UUID, Boolean> deadPlayers = new HashMap<>();
        for (SREGameRoundEndComponent.RoundEndData data : roundEnd.players) {
            deadPlayers.put(data.player().getId(), data.wasDead());
        }

        boolean hasKiller = false;
        boolean allKillersAlive = true;
        boolean allKillersDead = true;
        for (ServerPlayer player : world.players()) {
            SRERole role = gameComponent.getRole(player);
            if (role != null && !role.isVigilanteTeam() && role.canUseKiller()) {
                hasKiller = true;
                boolean dead = deadPlayers.getOrDefault(player.getUUID(), false);
                allKillersAlive &= !dead;
                allKillersDead &= dead;
            }
        }

        for (ServerPlayer player : world.players()) {
            SRERole role = gameComponent.getRole(player);
            if (role == null) {
                continue;
            }
            boolean police = role.isVigilanteTeam();
            boolean killer = !police && role.canUseKiller();
            boolean neutral = !police && !killer && role.isNeutrals();
            boolean won = roundEnd.didWin(player.getUUID());
            boolean wasFirstDeath = player.getUUID().equals(firstDeath);

            NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(player);
            component.updateAchievementStreaks(killer, police, neutral, won, wasFirstDeath);

            PlayerStats stats = PlayerStatsManager.get(player);
            for (NameTagTitleCatalog.TitleDefinition title : NameTagTitleCatalog.all()) {
                if (meetsRequirement(title, stats, component, killer, police, won, wasFirstDeath,
                        deadPlayers.getOrDefault(player.getUUID(), false), hasKiller,
                        allKillersAlive, allKillersDead)) {
                    grant(player, component, title.id());
                }
            }
        }
    }

    /** Grants every title that can be proven from the player's stored data. */
    public static List<String> backfillStoredTitles(ServerPlayer player) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(player);
        PlayerStats stats = PlayerStatsManager.get(player);
        List<String> eligibleTitles = new ArrayList<>();
        for (NameTagTitleCatalog.TitleDefinition title : NameTagTitleCatalog.all()) {
            if (meetsStoredRequirement(title, stats, component)) {
                eligibleTitles.add(title.id());
            }
        }
        return component.addNameTagsSilently(eligibleTitles);
    }

    private static boolean meetsRequirement(NameTagTitleCatalog.TitleDefinition title, PlayerStats stats,
            NameTagInventoryComponent component, boolean killer, boolean police, boolean won,
            boolean wasFirstDeath, boolean playerDead, boolean hasKiller,
            boolean allKillersAlive, boolean allKillersDead) {
        if (meetsStoredRequirement(title, stats, component)) {
            return true;
        }
        return switch (title.criterion()) {
            case FIRST_DEATH -> wasFirstDeath;
            case KILLER_PERFECT_WIN -> killer && won && hasKiller && allKillersAlive;
            case POLICE_PERFECT_WIN -> police && won && !playerDead && hasKiller && allKillersDead;
            default -> false;
        };
    }

    private static boolean meetsStoredRequirement(NameTagTitleCatalog.TitleDefinition title, PlayerStats stats,
            NameTagInventoryComponent component) {
        int minimumWins = Math.min(stats.getTotalKillerWins(),
                Math.min(stats.getTotalSheriffWins(), stats.getTotalNeutralWins()));
        return switch (title.criterion()) {
            case KILLER_WINS -> stats.getTotalKillerWins() >= title.threshold();
            case POLICE_WINS -> stats.getTotalSheriffWins() >= title.threshold();
            case NEUTRAL_WINS -> stats.getTotalNeutralWins() >= title.threshold();
            case GAMES_PLAYED -> stats.getTotalGamesPlayed() >= title.threshold();
            case KILLER_STREAK -> component.getKillerWinStreak() >= title.threshold();
            case POLICE_STREAK -> component.getPoliceWinStreak() >= title.threshold();
            case NEUTRAL_STREAK -> component.getNeutralWinStreak() >= title.threshold();
            case ALL_FACTION_WINS -> minimumWins >= title.threshold();
            case LOSS_STREAK -> component.getLossStreak() >= title.threshold();
            case LOW_WIN_RATE -> stats.getTotalGamesPlayed() >= title.threshold()
                    && (long) stats.getTotalWins() * 10L <= stats.getTotalGamesPlayed();
            case FIRST_DEATH_STREAK -> component.getFirstDeathStreak() >= title.threshold();
            case FIRST_DEATH, KILLER_PERFECT_WIN, POLICE_PERFECT_WIN, ADMIN_GRANTED -> false;
        };
    }

    private static void grant(ServerPlayer player, NameTagInventoryComponent component, String key) {
        if (component.nameTags.contains(key)) {
            return;
        }
        component.addNameTag(key);
    }

    private static UUID findFirstDeath() {
        UUID firstDeath = null;
        long firstDeathTime = Long.MAX_VALUE;
        for (GameUtils.PlayerKillInfo info : GameUtils.serverCacheKillState.values()) {
            for (GameUtils.PlayerKillResultInfo death : info.deaths) {
                if (death.dead() && death.time() < firstDeathTime) {
                    firstDeathTime = death.time();
                    firstDeath = death.victim();
                }
            }
        }
        return firstDeath;
    }

}
