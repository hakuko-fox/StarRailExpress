package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.gamemode.RoleRotationWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 职业轮选同步数据包
 * 服务端向客户端同步职业轮选状态
 */
public class RoleRotationSyncS2CPacket implements CustomPacketPayload {

    public static final Type<RoleRotationSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "role_rotation_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoleRotationSyncS2CPacket> CODEC = StreamCodec.ofMember(
            RoleRotationSyncS2CPacket::write,
            RoleRotationSyncS2CPacket::new
    );

    private final boolean isSelecting;
    private final int currentIndex;
    private final int totalPlayers;
    private final int confirmCountdown;
    private final int finalPhaseThreshold;
    private final int remainingTime; // 剩余选择时间（tick）

    public RoleRotationSyncS2CPacket(RegistryFriendlyByteBuf buf) {
        this.isSelecting = buf.readBoolean();
        this.currentIndex = buf.readInt();
        this.totalPlayers = buf.readInt();
        this.confirmCountdown = buf.readInt();
        this.finalPhaseThreshold = buf.readInt();
        this.remainingTime = buf.readInt();
    }

    private RoleRotationSyncS2CPacket(boolean isSelecting, int currentIndex, int totalPlayers,
            int confirmCountdown, int finalPhaseThreshold, int remainingTime) {
        this.isSelecting = isSelecting;
        this.currentIndex = currentIndex;
        this.totalPlayers = totalPlayers;
        this.confirmCountdown = confirmCountdown;
        this.finalPhaseThreshold = finalPhaseThreshold;
        this.remainingTime = remainingTime;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(isSelecting);
        buf.writeInt(currentIndex);
        buf.writeInt(totalPlayers);
        buf.writeInt(confirmCountdown);
        buf.writeInt(finalPhaseThreshold);
        buf.writeInt(remainingTime);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== 客户端处理 ====================

    public static void sendToPlayer(ServerPlayer player) {
        Level level = player.level();
        if (level.getServer() == null) {
            return;
        }

        RoleRotationWorldComponent rrwc = RoleRotationWorldComponent.KEY.get(level);
        int remainingTime = 0;
        if (rrwc.isSelecting()) {
            // 计算当前玩家的剩余选择时间
            remainingTime = rrwc.getSelectionTimeLimit();
        }

        ServerPlayNetworking.send(player, new RoleRotationSyncS2CPacket(
                rrwc.isSelecting(),
                rrwc.getCurrentRotationIndex(),
                rrwc.getTotalPlayers(),
                rrwc.getConfirmCountdown(),
                rrwc.getFinalPhaseThreshold(),
                remainingTime
        ));
    }

    public static void registerClientReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            context.client().execute(() -> {
                // 更新客户端缓存
                io.wifi.starrailexpress.content.vote.client.RoleRotationCache.updateFromPacket(payload);

                // 如果当前在轮选界面，更新界面
                if (context.client().screen instanceof RoleRotationScreen screen) {
                    screen.updateData();
                }
            });
        });
    }

    // Getter
    public boolean isSelecting() { return isSelecting; }
    public int getCurrentIndex() { return currentIndex; }
    public int getTotalPlayers() { return totalPlayers; }
    public int getConfirmCountdown() { return confirmCountdown; }
    public int getFinalPhaseThreshold() { return finalPhaseThreshold; }
    public int getRemainingTime() { return remainingTime; }
}
