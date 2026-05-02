package io.wifi.starrailexpress.network;

import io.wifi.events.day_night_fight.clue.ClueSystem;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

public record SendClueBookPayload(String clueUuidsCsv) implements CustomPacketPayload {
    public static final Type<SendClueBookPayload> ID = new Type<>(SRE.id("send_clue_book"));
    public static final StreamCodec<FriendlyByteBuf, SendClueBookPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SendClueBookPayload::clueUuidsCsv,
                    SendClueBookPayload::new);

    @Override
    public Type<SendClueBookPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<SendClueBookPayload> {
        @Override
        public void receive(@NotNull SendClueBookPayload payload, ServerPlayNetworking.@NotNull Context context) {
            context.server().execute(() -> {
                try {
                    var ids = Arrays.stream(payload.clueUuidsCsv().split(","))
                            .map(String::trim)
                            .filter(value -> !value.isEmpty())
                            .map(UUID::fromString)
                            .toList();
                    boolean ok = ClueSystem.sendCluesAsBook(context.player(), ids);
                    context.player().displayClientMessage(Component.translatable(ok
                            ? "commands.sre.clue.sendbook.success"
                            : "commands.sre.clue.sendbook.fail"), true);
                } catch (Exception ex) {
                    context.player().displayClientMessage(Component.translatable(
                            "commands.sre.clue.sendbook.uuid_format_error"), true);
                }
            });
        }
    }
}
