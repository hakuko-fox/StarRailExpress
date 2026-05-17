package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.gamemode.RoleRotationWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

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
    
    // 玩家轮选顺序
    private final HashMap<UUID, Integer> rotationOrder;
    // 已选职业
    private final HashMap<UUID, String> selectedRoles;
    // 候选职业
    private final List<String> currentCandidates;
    // 当前玩家自己的序号
    private final int myRotationIndex;

    public RoleRotationSyncS2CPacket(RegistryFriendlyByteBuf buf) {
        this.isSelecting = buf.readBoolean();
        this.currentIndex = buf.readInt();
        this.totalPlayers = buf.readInt();
        this.confirmCountdown = buf.readInt();
        this.finalPhaseThreshold = buf.readInt();
        this.remainingTime = buf.readInt();
        
        // 读取rotationOrder
        int orderSize = buf.readInt();
        this.rotationOrder = new HashMap<>();
        for (int i = 0; i < orderSize; i++) {
            UUID uuid = buf.readUUID();
            int index = buf.readInt();
            rotationOrder.put(uuid, index);
        }
        
        // 读取selectedRoles
        int selectedSize = buf.readInt();
        this.selectedRoles = new HashMap<>();
        for (int i = 0; i < selectedSize; i++) {
            UUID uuid = buf.readUUID();
            String rolePath = buf.readUtf();
            selectedRoles.put(uuid, rolePath);
        }
        
        // 读取currentCandidates
        int candidatesSize = buf.readInt();
        this.currentCandidates = new ArrayList<>();
        for (int i = 0; i < candidatesSize; i++) {
            String rolePath = buf.readUtf();
            currentCandidates.add(rolePath);
        }
        
        // 读取myRotationIndex
        this.myRotationIndex = buf.readInt();
    }

    private RoleRotationSyncS2CPacket(boolean isSelecting, int currentIndex, int totalPlayers,
            int confirmCountdown, int finalPhaseThreshold, int remainingTime,
            HashMap<UUID, Integer> rotationOrder, HashMap<UUID, String> selectedRoles,
            List<String> currentCandidates, int myRotationIndex) {
        this.isSelecting = isSelecting;
        this.currentIndex = currentIndex;
        this.totalPlayers = totalPlayers;
        this.confirmCountdown = confirmCountdown;
        this.finalPhaseThreshold = finalPhaseThreshold;
        this.remainingTime = remainingTime;
        this.rotationOrder = rotationOrder;
        this.selectedRoles = selectedRoles;
        this.currentCandidates = currentCandidates;
        this.myRotationIndex = myRotationIndex;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(isSelecting);
        buf.writeInt(currentIndex);
        buf.writeInt(totalPlayers);
        buf.writeInt(confirmCountdown);
        buf.writeInt(finalPhaseThreshold);
        buf.writeInt(remainingTime);
        
        // 写入rotationOrder
        buf.writeInt(rotationOrder.size());
        for (Map.Entry<UUID, Integer> entry : rotationOrder.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        
        // 写入selectedRoles
        buf.writeInt(selectedRoles.size());
        for (Map.Entry<UUID, String> entry : selectedRoles.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
        
        // 写入currentCandidates
        buf.writeInt(currentCandidates.size());
        for (String rolePath : currentCandidates) {
            buf.writeUtf(rolePath);
        }
        
        // 写入myRotationIndex
        buf.writeInt(myRotationIndex);
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
        } else if (rrwc.getConfirmCountdown() > 0) {
            // 如果不是选择阶段但有确认倒计时，使用确认倒计时
            remainingTime = rrwc.getConfirmCountdown();
        }
        
        // 获取当前玩家自己的序号
        int myIndex = rrwc.getPlayerRotationIndex(player.getUUID());

        // 构建玩家轮选顺序
        HashMap<UUID, Integer> orderMap = new HashMap<>(rrwc.getRotationOrderMap());
        
        // 构建已选职业map
        HashMap<UUID, String> selectedMap = new HashMap<>();
        for (Map.Entry<UUID, io.wifi.starrailexpress.api.SRERole> entry : rrwc.getSelectedRoles().entrySet()) {
            selectedMap.put(entry.getKey(), entry.getValue().identifier().toString());
        }
        
        // 构建候选职业列表
        List<String> candidatesList = new ArrayList<>();
        for (io.wifi.starrailexpress.api.SRERole role : rrwc.getCurrentCandidates()) {
            candidatesList.add(role.identifier().toString());
        }

        ServerPlayNetworking.send(player, new RoleRotationSyncS2CPacket(
                rrwc.isSelecting(),
                rrwc.getCurrentRotationIndex(),
                rrwc.getTotalPlayers(),
                rrwc.getConfirmCountdown(),
                rrwc.getFinalPhaseThreshold(),
                remainingTime,
                orderMap,
                selectedMap,
                candidatesList,
                myIndex
        ));
    }

    public static void registerClientReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            context.client().execute(() -> {
                // 检测是否有玩家刚选择完职业（通过检查selectedRoles变化）
                int previousSelectedCount = io.wifi.starrailexpress.content.vote.client.RoleRotationCache.getSelectedRoles().size();
                
                // 保存上次的轮到状态
                boolean previousWasMyTurn = io.wifi.starrailexpress.content.vote.client.RoleRotationCache.getWasMyTurn();

                // 更新客户端缓存
                io.wifi.starrailexpress.content.vote.client.RoleRotationCache.updateFromPacket(payload);

                // 检测是否有玩家刚选择完职业，播放音符盒音效
                int currentSelectedCount = io.wifi.starrailexpress.content.vote.client.RoleRotationCache.getSelectedRoles().size();
                if (currentSelectedCount > previousSelectedCount) {
                    // 有玩家选择了职业，播放音符盒音效
                    net.minecraft.client.Minecraft mc = context.client();
                    if (mc.player != null) {
                        mc.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                                0.5f,
                                1.2f
                            )
                        );
                    }
                }
                
                // 检测是否轮到自己（从不是轮到变为轮到）
                boolean currentIsMyTurn = io.wifi.starrailexpress.content.vote.client.RoleRotationCache.getWasMyTurn();
                if (!previousWasMyTurn && currentIsMyTurn) {
                    // 轮到当前玩家了，播放村民成功的声音
                    net.minecraft.client.Minecraft mc = context.client();
                    if (mc.player != null) {
                        mc.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.VILLAGER_YES, 
                                1.0f, 
                                1.0f
                            )
                        );
                    }
                }

                // 如果isSelecting变为false，关闭界面
                if (!payload.isSelecting() && payload.getConfirmCountdown() <= 0) {
                    net.minecraft.client.Minecraft mc = context.client();
                    if (mc.screen instanceof RoleRotationScreen) {
                        mc.setScreen(null);
                    }
                }

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
    public HashMap<UUID, Integer> getRotationOrder() { return rotationOrder; }
    public HashMap<UUID, String> getSelectedRoles() { return selectedRoles; }
    public List<String> getCurrentCandidates() { return currentCandidates; }
    public int getMyRotationIndex() { return myRotationIndex; }
}
