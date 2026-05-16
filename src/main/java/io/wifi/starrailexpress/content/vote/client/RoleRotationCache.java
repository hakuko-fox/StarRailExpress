package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.network.RoleRotationSyncS2CPacket;

import java.util.*;

/**
 * 职业轮选模式客户端缓存
 */
public class RoleRotationCache {

    private static boolean isSelecting = false;
    private static int currentIndex = 0;
    private static int totalPlayers = 0;
    private static int confirmCountdown = -1;
    private static int finalPhaseThreshold = 3;
    private static int remainingTime = 0;

    // 玩家序号映射 (玩家UUID -> 序号)
    private static final HashMap<UUID, Integer> rotationOrder = new HashMap<>();

    // 已选择职业的玩家 (玩家UUID -> 职业名)
    private static final HashMap<UUID, String> selectedRoles = new HashMap<>();

    // 当前候选职业
    private static final List<String> currentCandidates = new ArrayList<>();

    // 当前玩家自己的序号
    private static int myRotationIndex = -1;

    public static void updateFromPacket(RoleRotationSyncS2CPacket packet) {
        isSelecting = packet.isSelecting();
        currentIndex = packet.getCurrentIndex();
        totalPlayers = packet.getTotalPlayers();
        confirmCountdown = packet.getConfirmCountdown();
        finalPhaseThreshold = packet.getFinalPhaseThreshold();
        remainingTime = packet.getRemainingTime();
    }

    public static boolean isSelecting() {
        return isSelecting;
    }

    public static int getCurrentIndex() {
        return currentIndex;
    }

    public static int getTotalPlayers() {
        return totalPlayers;
    }

    public static int getConfirmCountdown() {
        return confirmCountdown;
    }

    public static int getFinalPhaseThreshold() {
        return finalPhaseThreshold;
    }

    public static int getRemainingTime() {
        return remainingTime;
    }

    public static int getRemainingSeconds() {
        return remainingTime / 20;
    }

    public static HashMap<UUID, Integer> getRotationOrder() {
        return rotationOrder;
    }

    public static HashMap<UUID, String> getSelectedRoles() {
        return selectedRoles;
    }

    public static List<String> getCurrentCandidates() {
        return currentCandidates;
    }

    public static int getMyRotationIndex() {
        return myRotationIndex;
    }

    public static void setMyRotationIndex(int index) {
        myRotationIndex = index;
    }

    public static boolean isMyTurn(UUID playerUuid) {
        Integer index = rotationOrder.get(playerUuid);
        return index != null && index == currentIndex;
    }

    public static void updateRotationOrder(HashMap<UUID, Integer> order) {
        rotationOrder.clear();
        rotationOrder.putAll(order);
    }

    public static void updateSelectedRoles(HashMap<UUID, String> selected) {
        selectedRoles.clear();
        selectedRoles.putAll(selected);
    }

    public static void updateCurrentCandidates(List<String> candidates) {
        currentCandidates.clear();
        currentCandidates.addAll(candidates);
    }

    public static void clear() {
        isSelecting = false;
        currentIndex = 0;
        totalPlayers = 0;
        confirmCountdown = -1;
        remainingTime = 0;
        rotationOrder.clear();
        selectedRoles.clear();
        currentCandidates.clear();
        myRotationIndex = -1;
    }

    public static boolean canReOpen() {
        return isSelecting || confirmCountdown > 0;
    }
}
