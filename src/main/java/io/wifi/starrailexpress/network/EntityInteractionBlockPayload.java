package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体交互方块的网络通信
 */
public class EntityInteractionBlockPayload {

    // 打开UI的包
    public record OpenUI(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenUI> TYPE = new Type<>(SRE.id("entity_interaction_open_ui"));
        public static final StreamCodec<FriendlyByteBuf, OpenUI> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenUI::pos,
                ByteBufCodecs.COMPOUND_TAG, OpenUI::data,
                OpenUI::new
        );

        @Override
        public Type<OpenUI> type() {
            return TYPE;
        }
    }

    // 同步BlockEntity数据的包（服务端->客户端，不打开UI）
    public record SyncBlockEntity(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<SyncBlockEntity> TYPE = new Type<>(SRE.id("entity_interaction_sync"));
        public static final StreamCodec<FriendlyByteBuf, SyncBlockEntity> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SyncBlockEntity::pos,
                ByteBufCodecs.COMPOUND_TAG, SyncBlockEntity::data,
                SyncBlockEntity::new
        );

        @Override
        public Type<SyncBlockEntity> type() {
            return TYPE;
        }
    }

    // 保存配置的包（客户端->服务端）
    public record SaveConfig(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<SaveConfig> TYPE = new Type<>(SRE.id("entity_interaction_save"));
        public static final StreamCodec<FriendlyByteBuf, SaveConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SaveConfig::pos,
                ByteBufCodecs.COMPOUND_TAG, SaveConfig::data,
                SaveConfig::new
        );

        @Override
        public Type<SaveConfig> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenUI.TYPE, OpenUI.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveConfig.TYPE, SaveConfig.CODEC);
        // SyncBlockEntity 的注册在 registerClientReceiver() 中处理（需要客户端初始化上下文）

        ServerPlayNetworking.registerGlobalReceiver(SaveConfig.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                BlockEntity be = player.level().getBlockEntity(payload.pos());
                if (be instanceof EntityInteractionBlockEntity entity) {
                    // 解析数据
                    CompoundTag data = payload.data();
                    List<EntityInteractionBlockEntity.TriggerCondition> conditions = new ArrayList<>();
                    List<EntityInteractionBlockEntity.TriggerAction> actions = new ArrayList<>();

                    if (data.contains("Conditions", ListTag.TAG_LIST)) {
                        ListTag list = data.getList("Conditions", ListTag.TAG_COMPOUND);
                        for (int i = 0; i < list.size(); i++) {
                            conditions.add(EntityInteractionBlockEntity.TriggerCondition.fromNbt(list.getCompound(i)));
                        }
                    }

                    if (data.contains("Actions", ListTag.TAG_LIST)) {
                        ListTag list = data.getList("Actions", ListTag.TAG_COMPOUND);
                        for (int i = 0; i < list.size(); i++) {
                            actions.add(EntityInteractionBlockEntity.TriggerAction.fromNbt(list.getCompound(i)));
                        }
                    }

                    int cooldown = data.getInt("CooldownTicks");
                    boolean isTeleportPoint = data.getBoolean("IsTeleportPoint");
                    int teleportPointId = data.getInt("TeleportPointId");

                    // 任务路标相关
                    boolean isTaskMarker = data.getBoolean("IsTaskMarker");
                    int taskMarkerColor = data.contains("TaskMarkerColor") ? data.getInt("TaskMarkerColor") : 0xFFFFFF;
                    EntityInteractionBlockEntity.TaskHighlightCondition taskHighlightCondition = EntityInteractionBlockEntity.TaskHighlightCondition.NONE;
                    if (data.contains("TaskHighlightCondition")) {
                        taskHighlightCondition = EntityInteractionBlockEntity.TaskHighlightCondition.valueOf(data.getString("TaskHighlightCondition"));
                    }
                    String taskHighlightTaskType = data.getString("TaskHighlightTaskType");
                    if (taskHighlightTaskType == null || taskHighlightTaskType.isEmpty()) taskHighlightTaskType = "*";
                    String taskHighlightCustomTaskId = data.getString("TaskHighlightCustomTaskId");
                    if (taskHighlightCustomTaskId == null) taskHighlightCustomTaskId = "";
                    int taskInstinctId = data.contains("TaskInstinctId") ? data.getInt("TaskInstinctId") : 100;

                    entity.setTeleportPoint(isTeleportPoint);
                    entity.setTeleportPointId(teleportPointId);
                    entity.setTaskMarker(isTaskMarker);
                    entity.setTaskMarkerColor(taskMarkerColor);
                    entity.setTaskHighlightCondition(taskHighlightCondition);
                    entity.setTaskHighlightTaskType(taskHighlightTaskType);
                    entity.setTaskHighlightCustomTaskId(taskHighlightCustomTaskId);
                    entity.setTaskInstinctId(taskInstinctId);

                    entity.updateFromServer(conditions, actions, cooldown);

                    // 标记服务器端 BlockEntity 已变更
                    entity.setChanged();
                    // 发送区块更新通知客户端
                    Level level = player.level();
                    level.sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(), entity.getBlockState(), 3);

                    // 强制同步 BlockEntity 数据到所有客户端
                    // 使用自定义数据包确保客户端收到最新数据
                    if (level instanceof ServerLevel serverLevel) {
                        for (ServerPlayer allPlayer : serverLevel.players()) {
                            sendSyncBlockEntity(allPlayer, payload.pos(), entity);
                        }
                    }
                }
            });
        });
    }

    /**
     * 在客户端初始化时注册 SyncBlockEntity 数据包的接收器
     * 这个方法需要在客户端初始化代码（SREClient.java）中调用
     */
    public static void registerClientReceiver() {
        PayloadTypeRegistry.playS2C().register(SyncBlockEntity.TYPE, SyncBlockEntity.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(SyncBlockEntity.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Level clientLevel = Minecraft.getInstance().level;
                if (clientLevel != null) {
                    BlockEntity be = clientLevel.getBlockEntity(payload.pos());
                    if (be instanceof EntityInteractionBlockEntity entity) {
                        CompoundTag data = payload.data();
                        // 直接更新 BlockEntity 的数据，不打开 UI
                        entity.setTeleportPoint(data.getBoolean("IsTeleportPoint"));
                        entity.setTeleportPointId(data.getInt("TeleportPointId"));
                        entity.setTaskMarker(data.getBoolean("IsTaskMarker"));
                        entity.setTaskMarkerColor(data.contains("TaskMarkerColor") ? data.getInt("TaskMarkerColor") : 0xFFFFFF);
                        if (data.contains("TaskHighlightCondition")) {
                            entity.setTaskHighlightCondition(EntityInteractionBlockEntity.TaskHighlightCondition.valueOf(data.getString("TaskHighlightCondition")));
                        }
                        entity.setTaskHighlightTaskType(data.getString("TaskHighlightTaskType"));
                        entity.setTaskHighlightCustomTaskId(data.getString("TaskHighlightCustomTaskId"));
                        entity.setTaskInstinctId(data.contains("TaskInstinctId") ? data.getInt("TaskInstinctId") : 100);
                        
                        // 手动调用 setChanged 通知客户端 BlockEntity 已更新
                        entity.setChanged();
                    }
                }
            });
        });
    }

    public static void sendOpenUI(ServerPlayer player, BlockPos pos, EntityInteractionBlockEntity entity) {
        CompoundTag data = new CompoundTag();

        // 序列化条件
        ListTag conditionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerCondition condition : entity.getConditions()) {
            conditionsTag.add(condition.toNbt());
        }
        data.put("Conditions", conditionsTag);

        // 序列化触发内容
        ListTag actionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerAction action : entity.getActions()) {
            actionsTag.add(action.toNbt());
        }
        data.put("Actions", actionsTag);

        data.putInt("CooldownTicks", entity.getCooldownTicks());
        data.putBoolean("IsTeleportPoint", entity.isTeleportPoint());
        data.putInt("TeleportPointId", entity.getTeleportPointId());

        // 任务路标相关
        data.putBoolean("IsTaskMarker", entity.isTaskMarker());
        data.putInt("TaskMarkerColor", entity.getTaskMarkerColor());
        if (entity.getTaskHighlightCondition() != null) {
            data.putString("TaskHighlightCondition", entity.getTaskHighlightCondition().name());
        }
        data.putString("TaskHighlightTaskType", entity.getTaskHighlightTaskType() != null ? entity.getTaskHighlightTaskType() : "*");
        data.putString("TaskHighlightCustomTaskId", entity.getTaskHighlightCustomTaskId() != null ? entity.getTaskHighlightCustomTaskId() : "");
        data.putInt("TaskInstinctId", entity.getTaskInstinctId());

        ServerPlayNetworking.send(player, new OpenUI(pos, data));
    }

    /**
     * 发送 BlockEntity 同步数据包到指定玩家
     * 不打开 UI，只同步数据
     */
    public static void sendSyncBlockEntity(ServerPlayer player, BlockPos pos, EntityInteractionBlockEntity entity) {
        CompoundTag data = new CompoundTag();

        data.putInt("TeleportPointId", entity.getTeleportPointId());
        data.putBoolean("IsTeleportPoint", entity.isTeleportPoint());

        // 任务路标相关
        data.putBoolean("IsTaskMarker", entity.isTaskMarker());
        data.putInt("TaskMarkerColor", entity.getTaskMarkerColor());
        if (entity.getTaskHighlightCondition() != null) {
            data.putString("TaskHighlightCondition", entity.getTaskHighlightCondition().name());
        }
        data.putString("TaskHighlightTaskType", entity.getTaskHighlightTaskType() != null ? entity.getTaskHighlightTaskType() : "*");
        data.putString("TaskHighlightCustomTaskId", entity.getTaskHighlightCustomTaskId() != null ? entity.getTaskHighlightCustomTaskId() : "");
        data.putInt("TaskInstinctId", entity.getTaskInstinctId());

        ServerPlayNetworking.send(player, new SyncBlockEntity(pos, data));
    }

    public static void sendSaveConfig(BlockPos pos, List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                      List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown) {
        sendSaveConfig(pos, conditions, actions, cooldown, false, -1, false, 0xFFFFFF,
                EntityInteractionBlockEntity.TaskHighlightCondition.NONE, "*", "", 100);
    }

    public static void sendSaveConfig(BlockPos pos, List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                      List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown,
                                      boolean isTeleportPoint, int teleportPointId) {
        sendSaveConfig(pos, conditions, actions, cooldown, isTeleportPoint, teleportPointId, false, 0xFFFFFF,
                EntityInteractionBlockEntity.TaskHighlightCondition.NONE, "*", "", 100);
    }

    public static void sendSaveConfig(BlockPos pos, List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                      List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldown,
                                      boolean isTeleportPoint, int teleportPointId,
                                      boolean isTaskMarker, int taskMarkerColor,
                                      EntityInteractionBlockEntity.TaskHighlightCondition taskHighlightCondition,
                                      String taskHighlightTaskType, String taskHighlightCustomTaskId,
                                      int taskInstinctId) {
        CompoundTag data = new CompoundTag();

        ListTag conditionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerCondition condition : conditions) {
            conditionsTag.add(condition.toNbt());
        }
        data.put("Conditions", conditionsTag);

        ListTag actionsTag = new ListTag();
        for (EntityInteractionBlockEntity.TriggerAction action : actions) {
            actionsTag.add(action.toNbt());
        }
        data.put("Actions", actionsTag);

        data.putInt("CooldownTicks", cooldown);
        data.putBoolean("IsTeleportPoint", isTeleportPoint);
        data.putInt("TeleportPointId", teleportPointId);

        // 任务路标相关
        data.putBoolean("IsTaskMarker", isTaskMarker);
        data.putInt("TaskMarkerColor", taskMarkerColor);
        if (taskHighlightCondition != null) {
            data.putString("TaskHighlightCondition", taskHighlightCondition.name());
        }
        data.putString("TaskHighlightTaskType", taskHighlightTaskType != null ? taskHighlightTaskType : "*");
        data.putString("TaskHighlightCustomTaskId", taskHighlightCustomTaskId != null ? taskHighlightCustomTaskId : "");
        data.putInt("TaskInstinctId", taskInstinctId);

        ClientPlayNetworking.send(new SaveConfig(pos, data));
    }
}
