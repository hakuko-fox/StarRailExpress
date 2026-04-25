package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;

public class ClientVoteCache {
    private static boolean active;
    private static Component title = Component.empty();
    private static List<VoteOption> options = new ArrayList<>();
    private static int endTick;          // -1 = 暂停
    private static boolean showResults;
    private static Map<Integer, Integer> results = Map.of();
    private static int totalVotes;
    private static boolean allowReVote;

    public static void updateFromPacket(VoteSyncS2CPacket packet) {
        active = packet.active();
        if (packet.active()) {
            title = packet.title();
            if (packet.hasOptions()) {
                options = new ArrayList<>(packet.options());
            }
            endTick = packet.endTick();
            showResults = packet.showResults();
            results = packet.results();
            totalVotes = packet.totalVotes();
            allowReVote = packet.allowReVote();
        } else {
            active = false;
        }
    }

    public static boolean isActive() { return active; }
    public static boolean canReOpen() { return active && allowReVote; }
    public static Component getTitle() { return title; }
    public static List<VoteOption> getOptions() { return options; }
    public static boolean isShowResults() { return showResults; }
    public static Map<Integer, Integer> getResults() { return results; }
    public static int getTotalVotes() { return totalVotes; }
    public static boolean isAllowReVote() { return allowReVote; }

    /** 根据服务端结束刻计算剩余秒数，-1 表示暂停 */
    public static int getRemainingSeconds() {
        if (endTick == -1) return -1;
        if (Minecraft.getInstance().level == null) return 0;
        long currentTick = Minecraft.getInstance().level.getGameTime();
        int remainingTicks = endTick - (int) currentTick;
        return Math.max(0, remainingTicks / 20);
    }
}