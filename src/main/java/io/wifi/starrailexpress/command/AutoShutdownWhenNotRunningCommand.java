package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AutoShutdownWhenNotRunningCommand {
    public static boolean autoShutdownWhenGameNotRunning = false;
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:autoShutdownWhenNotRunning")
                        .requires(source -> source.getServer().isDedicatedServer() && source.getEntity() == null)
                        .then(
                                Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> execute(context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))
                        )
        );
    }

    private static int execute(CommandSourceStack source, boolean enabled) {
        autoShutdownWhenGameNotRunning = enabled;


        source.sendSuccess(
                () -> Component.literal("自动关服(游戏未运行)已" + (enabled ? "开启" : "关闭")),
                true
        );
        return 1;
    }
}