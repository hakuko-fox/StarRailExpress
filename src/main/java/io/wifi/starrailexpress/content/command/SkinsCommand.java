/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.data.AdminSkinManagementService;
import io.wifi.starrailexpress.data.AdminSkinManagementService.Change;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.network.OpenSkinScreenPaylod;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SkinsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:skins")
                        .requires((t) -> Harpymodloader.officialVerify)
                        .executes(context -> execute(context.getSource(), null)) // 不指定玩家，默认自己
                        .then(Commands.literal("unlock")
                                .requires(source -> source.hasPermission(3))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        ItemSkinManager.getSkins().keySet(), builder))
                                                .then(Commands.literal("all")
                                                        .executes(context -> changeAll(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "type"),
                                                                Change.UNLOCK)))
                                                .then(Commands.argument("skin", StringArgumentType.word())
                                                        .suggests((context, builder) -> {
                                                            String type = canonicalKey(
                                                                    ItemSkinManager.getSkins(),
                                                                    StringArgumentType.getString(context, "type"));
                                                            return SharedSuggestionProvider.suggest(
                                                                    type == null
                                                                            ? java.util.List.of()
                                                                            : ItemSkinManager.getSkins(type).keySet(),
                                                                    builder);
                                                        })
                                                        .executes(context -> unlock(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "type"),
                                                                StringArgumentType.getString(context, "skin")))))))
                        .then(Commands.literal("lock")
                                .requires(source -> source.hasPermission(3))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        ItemSkinManager.getSkins().keySet(), builder))
                                                .then(Commands.literal("all")
                                                        .executes(context -> changeAll(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "type"),
                                                                Change.LOCK))))))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2)) // 需要权限等级2来查看其他玩家
                                .executes(context -> execute(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer player)
            throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();

        if (player == null) {
            // 未指定玩家，打开自己的皮肤管理界面
            openSkinScreen(sender);
            source.sendSuccess(() -> Component.translatable("commands.sre.showskin.self"), false);
        } else {
            // 指定玩家，打开指定玩家的皮肤管理界面

            if (player != null) {
                openSkinScreen(player);
                source.sendSuccess(() -> Component.translatable("commands.sre.showskin.other", player.getName()),
                        false);
            }

        }
        return 1;
    }

    private static int unlock(CommandSourceStack source, ServerPlayer player, String requestedType,
            String requestedSkin) {
        String itemType = canonicalKey(ItemSkinManager.getSkins(), requestedType);
        if (itemType == null) {
            source.sendFailure(Component.translatable("commands.sre.skins.invalid_type", requestedType));
            return 0;
        }
        if ("default".equalsIgnoreCase(requestedSkin)) {
            source.sendFailure(Component.translatable("commands.sre.skins.default"));
            return 0;
        }

        String skinName = canonicalKey(ItemSkinManager.getSkins(itemType), requestedSkin);
        if (skinName == null) {
            source.sendFailure(Component.translatable(
                    "commands.sre.skins.invalid_skin", requestedSkin, itemType));
            return 0;
        }

        return change(source, player, itemType, List.of(skinName), Change.UNLOCK, false);
    }

    private static int changeAll(CommandSourceStack source, ServerPlayer player, String requestedType,
            Change change) {
        String itemType = canonicalKey(ItemSkinManager.getSkins(), requestedType);
        if (itemType == null) {
            source.sendFailure(Component.translatable("commands.sre.skins.invalid_type", requestedType));
            return 0;
        }

        List<String> skinNames = ItemSkinManager.getSkins(itemType).keySet().stream()
                .filter(skinName -> !"default".equalsIgnoreCase(skinName))
                .toList();
        if (skinNames.isEmpty()) {
            source.sendFailure(Component.translatable("commands.sre.skins.all.no_skins", itemType));
            return 0;
        }
        return change(source, player, itemType, skinNames, change, true);
    }

    private static int change(CommandSourceStack source, ServerPlayer player, String itemType,
            Collection<String> skinNames, Change change, boolean all) {
        String displaySkin = all ? null : skinNames.iterator().next();
        if (!AdminSkinManagementService.isMysqlSyncConfigured()) {
            applyLocalChange(player, itemType, skinNames, change);
            source.sendSuccess(() -> changeMessage(change, all, "success_local", displaySkin, itemType, player),
                    true);
            return 1;
        }
        if (!AdminSkinManagementService.isMysqlSyncAvailable()) {
            source.sendFailure(Component.translatable("commands.sre.skins.mysql_unavailable"));
            return 0;
        }

        source.sendSuccess(() -> changeMessage(change, all, "saving", displaySkin, itemType, player), false);
        AdminSkinManagementService.persistChange(player, itemType, skinNames, change)
                .whenComplete((saved, throwable) -> source.getServer().execute(() -> {
                    if (throwable != null) {
                        SRE.LOGGER.warn("Failed to persist administrator skin change for player {}",
                                player.getUUID(), throwable);
                        source.sendFailure(Component.translatable("commands.sre.skins.mysql_failed"));
                        return;
                    }
                    if (!Boolean.TRUE.equals(saved)) {
                        source.sendFailure(Component.translatable("commands.sre.skins.mysql_conflict"));
                        return;
                    }

                    ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayer(player.getUUID());
                    if (onlinePlayer != null) {
                        applyLocalChange(onlinePlayer, itemType, skinNames, change);
                    }
                    source.sendSuccess(
                            () -> changeMessage(change, all, "success_mysql", displaySkin, itemType, player), true);
                }));
        return 1;
    }

    private static void applyLocalChange(ServerPlayer player, String itemType, Collection<String> skinNames,
            Change change) {
        if (change == Change.UNLOCK) {
            PlayerEconomyManager.unlockSkinsForItemType(player, itemType, skinNames);
        } else {
            PlayerEconomyManager.lockSkinsForItemType(player, itemType, skinNames);
        }
    }

    private static Component changeMessage(Change change, boolean all, String suffix, String skinName,
            String itemType, ServerPlayer player) {
        String action = change == Change.UNLOCK ? "unlock" : "lock";
        String key = "commands.sre.skins." + action + (all ? ".all." : ".") + suffix;
        if (all) {
            return Component.translatable(key, itemType, player.getDisplayName());
        }
        return Component.translatable(key, skinName, itemType, player.getDisplayName());
    }

    private static <T> String canonicalKey(Map<String, T> values, String requested) {
        for (String key : values.keySet()) {
            if (key.equalsIgnoreCase(requested)) {
                return key;
            }
        }
        return null;
    }

    private static void openSkinScreen(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenSkinScreenPaylod());
    }
}
