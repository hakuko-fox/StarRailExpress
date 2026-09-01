/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.backpack.BackpackManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

public final class VtuberCoinCommand {
    private VtuberCoinCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:vtuber_coin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("get")
                        .executes(context -> get(context, List.of(context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> get(context, EntityArgument.getPlayers(context, "targets")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> apply(context, true)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(context -> apply(context, false))))));
    }

    private static int get(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            if (!ensureLoaded(context.getSource(), player)) {
                continue;
            }
            int balance = BackpackManager.getVtuberCoins(player);
            context.getSource().sendSuccess(() -> Component.translatable("commands.sre.vtuber_coin.get",
                    player.getName().getString(), balance), false);
            count++;
        }
        return count;
    }

    private static int apply(CommandContext<CommandSourceStack> context, boolean set)
            throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int affected = 0;
        for (ServerPlayer player : targets) {
            if (!ensureLoaded(context.getSource(), player)) {
                continue;
            }
            boolean success = set
                    ? BackpackManager.setVtuberCoins(player, amount)
                    : BackpackManager.addVtuberCoins(player, amount);
            if (!success) {
                context.getSource().sendFailure(Component.translatable("commands.sre.vtuber_coin.invalid",
                        player.getName().getString()));
                continue;
            }
            int balance = BackpackManager.getVtuberCoins(player);
            context.getSource().sendSuccess(() -> Component.translatable(
                    set ? "commands.sre.vtuber_coin.set" : "commands.sre.vtuber_coin.add",
                    player.getName().getString(), amount, balance), true);
            affected++;
        }
        return affected;
    }

    private static boolean ensureLoaded(CommandSourceStack source, ServerPlayer player) {
        if (BackpackManager.isLoaded(player.getUUID())) {
            return true;
        }
        source.sendFailure(Component.translatable("commands.sre.vtuber_coin.not_loaded",
                player.getName().getString()));
        return false;
    }
}
