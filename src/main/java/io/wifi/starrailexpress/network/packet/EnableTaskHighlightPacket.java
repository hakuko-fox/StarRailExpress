package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EnableTaskHighlightPacket(boolean enable, BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<EnableTaskHighlightPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "enable_task_highlight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnableTaskHighlightPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EnableTaskHighlightPacket::enable,
            BlockPos.STREAM_CODEC, EnableTaskHighlightPacket::blockPos,
            EnableTaskHighlightPacket::new);

    // 兼容旧版本：不带位置的构造函数
    public EnableTaskHighlightPacket(boolean enable) {
        this(enable, BlockPos.ZERO);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
