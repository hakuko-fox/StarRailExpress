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

package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;

import java.util.List;

public class ForceRoleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("forceRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(SREConfig.instance().forceRoleRequiredPermission))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ForceRoleCommand::query)
                        .then(Commands.literal("clear").executes(ForceRoleCommand::clear))
                        .then(Commands.argument("role", RoleArgumentType.create())
                                .executes(ForceRoleCommand::execute))));

        dispatcher.register(Commands.literal("force-role")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(SREConfig.instance().forceRoleRequiredPermission))
                .then(Commands.literal("enable").executes(context -> setPersistentEnabled(context, true)))
                .then(Commands.literal("disable").executes(context -> setPersistentEnabled(context, false)))
                .then(Commands.literal("list").executes(ForceRoleCommand::listPersistent))
                .then(Commands.literal("reset").executes(ForceRoleCommand::resetPersistent))
                .then(Commands.argument("role", RoleArgumentType.create())
                        .executes(ForceRoleCommand::executePersistent)));
    }

    private static int query(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if(!Harpymodloader.officialVerify) {
            return 1;
        }
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        if (!Harpymodloader.FORCED_MODDED_ROLE.containsKey(targetPlayer.getUUID())) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.query.none", targetPlayer.getName()), false);
            return 0;
        }
        SRERole role = Harpymodloader.FORCED_MODDED_ROLE.get(targetPlayer.getUUID());
        Component roleText = Harpymodloader.getRoleName(role).withColor(role.color()).withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(role.identifier().toString())))
        );
        context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.query", targetPlayer.getName(), roleText), false);
        return 1;
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        SRERole role = RoleArgumentType.getRole(context, "role");
        Harpymodloader.addToForcedRoles(role, targetPlayer);
        final MutableComponent roleText = Harpymodloader.getRoleName(role).withColor(role.color()).withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(role.identifier().toString()))));
        context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.success", roleText, targetPlayer.getName()), true);
        return 1;
    }
    
    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        Harpymodloader.clearForcedRoles(targetPlayer);
        
        context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.success.clear", targetPlayer.getName()), true);
        return 1;
    }

    private static int executePersistent(CommandContext<CommandSourceStack> context) {
        SRERole role = RoleArgumentType.getRole(context, "role");
        Harpymodloader.addPersistentForcedRole(role);
        final MutableComponent roleText = Harpymodloader.getRoleName(role).withColor(role.color()).withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(role.identifier().toString()))));
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.forcerole.persistent.success", roleText,
                Harpymodloader.getOccupationRoleGroup(role).size() - 1), true);
        return 1;
    }

    private static int resetPersistent(CommandContext<CommandSourceStack> context) {
        Harpymodloader.resetPersistentForcedRoles();
        context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.persistent.reset"), true);
        return 1;
    }

    private static int setPersistentEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        Harpymodloader.setPersistentForcedRolesEnabled(enabled);
        context.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "commands.forcerole.persistent.enable" : "commands.forcerole.persistent.disable"), true);
        return 1;
    }

    private static int listPersistent(CommandContext<CommandSourceStack> context) {
        List<SRERole> roles = Harpymodloader.getPersistentForcedRoles();
        if (roles.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.persistent.list.empty"), false);
            return 0;
        }

        MutableComponent message = Component.translatable(
                "commands.forcerole.persistent.list.header",
                Component.translatable(Harpymodloader.PERSISTENT_FORCED_ROLES_ENABLED
                        ? "commands.forcerole.persistent.status.enabled"
                        : "commands.forcerole.persistent.status.disabled"));
        for (SRERole role : roles) {
            Component roleText = Harpymodloader.getRoleName(role).withColor(role.color()).withStyle(style ->
                    style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal(role.identifier().toString()))));
            message.append("\n- ").append(roleText);
        }
        context.getSource().sendSuccess(() -> message, false);
        return roles.size();
    }
}
