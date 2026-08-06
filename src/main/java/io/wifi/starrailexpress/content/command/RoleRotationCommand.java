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
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.data.AllRoleRotationSavedData;
import net.exmo.sre.repair.role.RepairRole;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 全職業輪跑模式管理指令：
 *   sre:role_rotation status — 查看當前回合與輪跑進度（已玩 / 未玩清單）
 *   sre:role_rotation reset   — 清空全部進度，從頭輪跑
 */
public class RoleRotationCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:role_rotation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(RoleRotationCommand::status))
                .then(Commands.literal("reset")
                        .executes(RoleRotationCommand::reset)));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        AllRoleRotationSavedData state = AllRoleRotationSavedData.get(ctx.getSource().getServer());
        long round = state.getCurrentRound();

        List<SRERole> eligible = new ArrayList<>();
        List<SRERole> unplayed = new ArrayList<>();
        int playedDistinct = 0;
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (!isGloballyEligible(role))
                continue;
            eligible.add(role);
            AllRoleRotationSavedData.RoleTrack t = state.getTrackOrNull(role.getIdentifier().toString());
            if (t == null || t.playedCount == 0)
                unplayed.add(role);
            else
                playedDistinct++;
        }

        MutableComponent header = Component.translatable("commands.sre.role_rotation.status.header", round)
                .withStyle(ChatFormatting.GOLD);
        MutableComponent summary = Component.translatable("commands.sre.role_rotation.status.summary",
                playedDistinct, eligible.size()).withStyle(ChatFormatting.YELLOW);

        MutableComponent unplayedHeader = Component.translatable("commands.sre.role_rotation.status.unplayed")
                .withStyle(ChatFormatting.AQUA);
        for (SRERole role : unplayed) {
            unplayedHeader.append(Component.literal(role.getIdentifier().toString())
                    .withColor(role.getColor()));
            unplayedHeader.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
        }

        ctx.getSource().sendSuccess(() -> header, false);
        ctx.getSource().sendSuccess(() -> summary, false);
        ctx.getSource().sendSuccess(() -> unplayedHeader, false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        AllRoleRotationSavedData state = AllRoleRotationSavedData.get(ctx.getSource().getServer());
        state.resetAll();
        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.sre.role_rotation.reset").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    /** 全域合格判斷（不考慮當前地圖禁用，僅用於進度統計）。 */
    private static boolean isGloballyEligible(SRERole role) {
        return !Harpymodloader.VANNILA_ROLES.contains(role)
                && !role.isOtherModeRole()
                && !(role instanceof RepairRole)
                && role != TMMRoles.DISCOVERY_CIVILIAN
                && role != TMMRoles.LOOSE_END
                && role != TMMRoles.CIVILIAN;
    }
}
