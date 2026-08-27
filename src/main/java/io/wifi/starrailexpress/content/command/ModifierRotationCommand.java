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
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.game.data.ModifierRotationSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Administrative commands for the cross-round modifier rotation. */
public final class ModifierRotationCommand {
    private ModifierRotationCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:modifier_rotation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ModifierRotationCommand::status))
                .then(Commands.literal("reset").executes(ModifierRotationCommand::reset))
                .then(Commands.literal("enable").executes(context -> setEnabled(context, true)))
                .then(Commands.literal("disable").executes(context -> setEnabled(context, false))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        ModifierRotationSavedData state = ModifierRotationSavedData.get(context.getSource().getServer());
        List<SREModifier> modifiers = new ArrayList<>(HMLModifiers.MODIFIERS);
        modifiers.sort(Comparator.comparing(modifier -> modifier.identifier().toString()));

        int appeared = 0;
        int minimumPlayed = Integer.MAX_VALUE;
        for (SREModifier modifier : modifiers) {
            ModifierRotationSavedData.ModifierTrack track = state.getTrackOrNull(modifier.identifier().toString());
            int playedCount = track == null ? 0 : track.playedCount;
            if (playedCount > 0)
                appeared++;
            minimumPlayed = Math.min(minimumPlayed, playedCount);
        }
        if (minimumPlayed == Integer.MAX_VALUE)
            minimumPlayed = 0;

        List<SREModifier> priority = new ArrayList<>();
        for (SREModifier modifier : modifiers) {
            ModifierRotationSavedData.ModifierTrack track = state.getTrackOrNull(modifier.identifier().toString());
            int playedCount = track == null ? 0 : track.playedCount;
            if (playedCount == minimumPlayed)
                priority.add(modifier);
        }

        Component status = Component.translatable(state.isEnabled()
                ? "commands.sre.modifier_rotation.status.enabled"
                : "commands.sre.modifier_rotation.status.disabled");
        MutableComponent header = Component.translatable(
                "commands.sre.modifier_rotation.status.header", status, state.getCurrentRound())
                .withStyle(ChatFormatting.GOLD);
        MutableComponent summary = Component.translatable(
                "commands.sre.modifier_rotation.status.summary", appeared, modifiers.size())
                .withStyle(ChatFormatting.YELLOW);
        MutableComponent priorityText = Component.translatable(
                "commands.sre.modifier_rotation.status.priority", minimumPlayed)
                .withStyle(ChatFormatting.AQUA);
        for (int i = 0; i < priority.size(); i++) {
            SREModifier modifier = priority.get(i);
            if (i > 0)
                priorityText.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            priorityText.append(Component.literal(modifier.identifier().toString()).withColor(modifier.color()));
        }

        context.getSource().sendSuccess(() -> header, false);
        context.getSource().sendSuccess(() -> summary, false);
        context.getSource().sendSuccess(() -> priorityText, false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        ModifierRotationSavedData state = ModifierRotationSavedData.get(context.getSource().getServer());
        state.resetAll();
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.sre.modifier_rotation.reset")
                        .withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        ModifierRotationSavedData state = ModifierRotationSavedData.get(context.getSource().getServer());
        state.setEnabled(enabled);
        context.getSource().sendSuccess(
                () -> Component.translatable(enabled
                        ? "commands.sre.modifier_rotation.enable"
                        : "commands.sre.modifier_rotation.disable")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
                true);
        return 1;
    }
}
