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

package net.exmo.sre.nametag;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.stats.PlayerStatsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

public class NameTagCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        // /nametag add <nameTag> [target] - 添加名片
        dispatcher.register(Commands.literal("nametag:add").requires(source -> source.hasPermission(2))
                .then(Commands.argument("nameTag", StringArgumentType.string())
                        .executes(context -> addNametag(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .executes(context -> addNametagOfflineAware(context,
                                        StringArgumentType.getString(context, "target"))))));

        // /nametag remove <nameTag> [target] - 移除名片
        dispatcher.register(Commands.literal("nametag:remove").requires(source -> source.hasPermission(2))
                .then(Commands.argument("nameTag", StringArgumentType.string())
                        .executes(context -> removeNametag(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> removeNametag(context,
                                        EntityArgument.getPlayer(context, "target"))))));

        // /nametag set <nameTag> [target] - 设置当前名片
        dispatcher.register(Commands.literal("nametag:set").requires(source -> source.hasPermission(2))
                .then(Commands.argument("nameTag", StringArgumentType.string())
                        .executes(context -> setNametag(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(
                                        context -> setNametag(context, EntityArgument.getPlayer(context, "target"))))));

        // /nametag get [target] - 获取当前名片
        dispatcher.register(Commands.literal("nametag:get").requires(source -> source.hasPermission(2))
                .executes(context -> getNametag(context, context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> getNametag(context, EntityArgument.getPlayer(context, "target")))));

        // /nametag list [target] - 列出所有名片
        dispatcher.register(Commands.literal("nametag:list").requires(source -> source.hasPermission(2))
                .executes(context -> listNametags(context, context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> listNametags(context, EntityArgument.getPlayer(context, "target")))));

        // /nametag clear [target] - 清空所有名片
        dispatcher.register(Commands.literal("nametag:clear").requires(source -> source.hasPermission(2))
                .executes(context -> clearNametags(context, context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> clearNametags(context, EntityArgument.getPlayer(context, "target")))));

        // /nametag sync <target> - 从服务器同步名片数据
        dispatcher.register(Commands.literal("nametag:sync").requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> syncFromServer(context, EntityArgument.getPlayer(context, "target")))));

        // /nametag:backfill [targets] - 补发可由现有数据验证的称号
        dispatcher.register(Commands.literal("nametag:backfill").requires(source -> source.hasPermission(2))
                .executes(context -> backfillNametags(context,
                        List.of(context.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> backfillNametags(context,
                                EntityArgument.getPlayers(context, "targets")))));
    }

    /**
     * 添加名片
     */
    private static int addNametag(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        try {

            String nameTag = StringArgumentType.getString(context, "nameTag");
            NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

            component.addNameTag(NameTagTitleCatalog.resolveCommandInput(nameTag));

            context.getSource().sendSuccess(() -> Component.literal("已为玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 添加名片: "))
                    .append(NameTagTitleCatalog.displayText(NameTagTitleCatalog.resolveCommandInput(nameTag))), true);

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("添加名片失败"));
            SRE.LOGGER.error("添加名片失败", e);
        }
        return 1;
    }

    private static int addNametagOfflineAware(CommandContext<CommandSourceStack> context, String targetName) {
        ServerPlayer onlinePlayer = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        if (onlinePlayer != null) {
            return addNametag(context, onlinePlayer);
        }

        var profileCache = context.getSource().getServer().getProfileCache();
        var profile = profileCache == null ? java.util.Optional.<com.mojang.authlib.GameProfile>empty()
                : profileCache.get(targetName);
        NameTagDataStore.StoredPlayer storedPlayer;
        if (profile.isPresent() && profile.get().getId() != null) {
            storedPlayer = new NameTagDataStore.StoredPlayer(profile.get().getId(), profile.get().getName());
        } else {
            storedPlayer = NameTagDataStore.findStoredPlayer(targetName).orElse(null);
        }
        if (storedPlayer == null) {
            context.getSource().sendFailure(Component.literal("找不到玩家紀錄: " + targetName));
            return 0;
        }

        String inputNameTag = StringArgumentType.getString(context, "nameTag");
        String resolvedNameTag = NameTagTitleCatalog.resolveCommandInput(inputNameTag);
        boolean added = NameTagDataStore.addOfflineNameTag(
                storedPlayer.uuid(), storedPlayer.playerName(), resolvedNameTag);
        if (!added) {
            context.getSource().sendFailure(Component.literal("玩家 " + storedPlayer.playerName()
                    + " 已擁有稱號: " + inputNameTag));
            return 0;
        }

        NameTagInventoryComponent.broadcastUnlock(context.getSource().getServer(),
                Component.literal(storedPlayer.playerName()), resolvedNameTag);
        context.getSource().sendSuccess(() -> Component.literal("已為離線玩家 " + storedPlayer.playerName()
                + " 添加稱號: ").append(NameTagTitleCatalog.displayText(resolvedNameTag)), true);
        return 1;
    }

    /**
     * 移除名片
     */
    private static int removeNametag(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        String nameTag = StringArgumentType.getString(context, "nameTag");
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

        String resolvedNameTag = NameTagTitleCatalog.resolveCommandInput(nameTag);
        if (component.nameTags.contains(resolvedNameTag)) {
            component.removeNameTag(resolvedNameTag);
            context.getSource().sendSuccess(() -> Component.literal("已从玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 移除名片: "))
                    .append(Component.literal(nameTag)), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 没有该名片: "))
                    .append(Component.literal(nameTag)));
            return 0;
        }
    }

    /**
     * 设置当前名片
     */
    private static int setNametag(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        String nameTag = StringArgumentType.getString(context, "nameTag");
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

        String resolvedNameTag = NameTagTitleCatalog.resolveCommandInput(nameTag);
        if (component.nameTags.contains(resolvedNameTag)) {
            component.setCurrentNameTag(resolvedNameTag);
            context.getSource().sendSuccess(() -> Component.literal("已将玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 的当前名片设置为: "))
                    .append(NameTagTitleCatalog.displayText(resolvedNameTag)), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 没有该名片，无法设置: "))
                    .append(Component.literal(nameTag)));
            return 0;
        }
    }

    /**
     * 获取当前名片
     */
    private static int getNametag(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);
        String currentNametag = component.getCurrentNameTag();

        if (currentNametag.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 当前未装备名片")), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 的当前名片为: "))
                    .append(NameTagTitleCatalog.displayText(currentNametag)), false);
        }

        return 1;
    }

    /**
     * 列出所有名片
     */
    private static int listNametags(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

        if (component.nameTags.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 没有任何名片")), false);
            return 1;
        }

        var message = Component.literal("玩家 ")
                .append(target.getName())
                .append(Component.literal(" 的名片列表 ("))
                .append(Component.literal(String.valueOf(component.nameTags.size())))
                .append(Component.literal("):"));

        for (String nameTag : component.nameTags) {
            message.append(Component.literal("\n - ")).append(NameTagTitleCatalog.displayText(nameTag));
            if (nameTag.equals(component.CurrentNameTag)) {
                message.append(Component.literal(" [当前]").withStyle(ChatFormatting.YELLOW));
            }
        }

        context.getSource().sendSuccess(() -> message, false);

        return 1;
    }

    /**
     * 清空所有名片
     */
    private static int clearNametags(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);
        int count = component.nameTags.size();

        if (count > 0) {
            component.clear();
            context.getSource().sendSuccess(() -> Component.literal("已清空玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 的所有名片 ("))
                    .append(Component.literal(String.valueOf(count)))
                    .append(Component.literal(" 个)")), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 本来就没有名片")), false);
        }

        return 1;
    }

    /**
     * 从服务器同步名片数据
     */
    private static int syncFromServer(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

        if (component.isNetworkSyncEnabled()) {
            component.syncFromLinkedServer();
            context.getSource().sendSuccess(() -> Component.literal("正在从服务器同步玩家 ")
                    .append(target.getName())
                    .append(Component.literal(" 的名片数据...")), true);
        } else {
            context.getSource().sendFailure(Component.literal("网络同步未启用，无法同步数据"));
            return 0;
        }

        return 1;
    }

    private static int backfillNametags(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets) {
        int processed = 0;
        for (ServerPlayer target : targets) {
            if (!PlayerStatsManager.isReadyForTitleBackfill(target)) {
                context.getSource().sendFailure(Component.translatable(
                        "command.sre.nametag.backfill.not_ready", target.getDisplayName()));
                continue;
            }

            List<String> added = TitleUnlockManager.backfillStoredTitles(target);
            if (added.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "command.sre.nametag.backfill.none", target.getDisplayName()), false);
            } else {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "command.sre.nametag.backfill.success", added.size(), target.getDisplayName()), false);
            }
            processed++;
        }
        return processed;
    }
}
