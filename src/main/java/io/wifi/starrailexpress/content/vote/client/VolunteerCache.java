package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.game.modes.funny.volunteer.VolunteerDraftState.Phase;
import io.wifi.starrailexpress.network.packet.VolunteerDraftSyncS2CPacket;
import net.minecraft.client.Minecraft;

import java.util.*;

public class VolunteerCache {
    private static Phase phase = Phase.WAITING;
    private static int serverRemainingTime = 0;
    private static long syncWorldTime = 0;
    private static List<String> myCandidates = List.of();
    private static int volunteerCount = 3;
    private static Map<UUID, String> finalRoles = Map.of();
    private static String myFinalRole = "";

    public static void updateFromPacket(VolunteerDraftSyncS2CPacket packet) {
        phase = packet.phase();
        serverRemainingTime = packet.remainingTime();
        myCandidates = packet.myCandidates();
        volunteerCount = packet.volunteerCount();
        finalRoles = packet.finalRoles();
        myFinalRole = packet.myFinalRole();
        Minecraft mc = Minecraft.getInstance();
        syncWorldTime = mc.level != null ? mc.level.getGameTime() : 0;
    }

    public static int getSmoothRemainingTime() {
        if (phase == Phase.WAITING || phase == Phase.RESULT)
            return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return serverRemainingTime;
        long elapsed = mc.level.getGameTime() - syncWorldTime;
        return (int) Math.max(0, serverRemainingTime - elapsed);
    }

    public static Phase getPhase() {
        return phase;
    }

    public static List<String> getMyCandidates() {
        return myCandidates;
    }

    public static int getVolunteerCount() {
        return volunteerCount;
    }

    public static Map<UUID, String> getFinalRoles() {
        return finalRoles;
    }

    public static String getMyFinalRole() {
        return myFinalRole;
    }
}