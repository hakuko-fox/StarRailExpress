package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public record ZeroOneFiveShootPayload(int target) implements CustomPacketPayload {
    public static final Type<ZeroOneFiveShootPayload> ID = new Type<>(SRE.id("zero_one_five_shoot"));
    public static final StreamCodec<FriendlyByteBuf, ZeroOneFiveShootPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            ZeroOneFiveShootPayload::target,
            ZeroOneFiveShootPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<ZeroOneFiveShootPayload> {
        @Override
        public void receive(@NotNull ZeroOneFiveShootPayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            ItemStack mainHandStack = player.getMainHandItem();

            if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                return;

            // 检查是否是零一五枪
            if (!mainHandStack.is(ModItems.ZERO_ONE_FIVE_GUN)) {
                return;
            }

            // 检查目标
            boolean hit = false;
            if (player.serverLevel().getEntity(payload.target()) instanceof ServerPlayer target
                    && target.distanceToSqr(player) < 30 * 30) {
                // 处理命中
                ZeroOneFiveGunItem.onHit(player, target);
                hit = true;
            }

            // 无论是否命中，第一枪后立即触发冷却（但不影响自动第二枪）
            player.getCooldowns().addCooldown(ModItems.ZERO_ONE_FIVE_GUN, ZeroOneFiveGunItem.getCooldown());

            // 播放音效
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            // 发送枪口闪光给所有追踪者
            for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
                PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));
            }
            PacketTracker.sendToClient(player, new ShootMuzzleS2CPayload(player.getId()));
        }
    }
}
