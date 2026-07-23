package org.agmas.noellesroles.commands;

import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.game.data.MapConfig.MapEntry;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * /tmm:syncmaps — 将 train_maps/ 目录下未注册到 train_vote_maps.json 的地图自动补进注册表。
 * <p>
 * 仅新增缺失条目，绝不覆盖或删除既有条目（含 random）。新增条目预设：
 * displayName=地图文件名(字面)、canSelect=true、gameModes=[](全模式)、min/maxcount=-1。
 * <p>
 * 与 /tmm:switchmap scan_all 互补：scan_all 负责扫描重置点 / 任务点并缓存，
 * 本指令负责把 train_maps/ 中的新地图登记到投票清单。
 * 仅使用 ServerMapConfig / MapManager 的公开 API，不改动 io.wifi.starrailexpress。
 */
public class SyncMapsCommand {

    /** 新增条目的默认颜色 */
    private static final String DEFAULT_COLOR = "0xFF4CC9F0";

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("tmm:syncmaps")
                        .requires(source -> source.hasPermission(3))
                        .executes(SyncMapsCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        MinecraftServer server = level.getServer();

        // 扫描 train_maps/ 根目录（与 scan_all 一致使用 childFolder=false）
        List<String> files = MapManager.getAvailableMaps(level, false);
        if (files.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.sre.syncmaps.no_files")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        // reload 保证 instance 来自磁盘（Gson 反序列化为可变 ArrayList），
        // 规避 createDefaultConfig 使用 Arrays.asList 导致的不可变边界情况。
        ServerMapConfig.reload(server);
        ServerMapConfig cfg = ServerMapConfig.getInstance(server);

        List<MapEntry> entries = cfg.getMaps();
        // 已注册 id 集合，用于 O(1) 查重
        Set<String> registeredIds = new HashSet<>();
        if (entries != null) {
            for (MapEntry e : entries) {
                if (e != null && e.id != null) {
                    registeredIds.add(e.id);
                }
            }
        }

        List<String> added = new ArrayList<>();
        for (String name : files) {
            if (name == null || name.isEmpty()) continue;
            if (registeredIds.contains(name)) continue;
            // 仅新增缺失条目：不动既有设定
            cfg.getMaps().add(new MapEntry(name, name, "", DEFAULT_COLOR, -1));
            registeredIds.add(name);
            added.add(name);
        }

        if (added.isEmpty()) {
            final int total = registeredIds.size();
            source.sendSuccess(() -> Component.translatable("commands.sre.syncmaps.nothing_added", total)
                    .withStyle(ChatFormatting.GREEN), true);
            return 0;
        }

        // 持久化到 train_vote_maps.json
        cfg.saveConfig(server);

        final int addedCount = added.size();
        final int totalNow = cfg.getMaps().size();
        source.sendSuccess(() -> Component.translatable("commands.sre.syncmaps.added", addedCount, totalNow)
                .withStyle(ChatFormatting.GREEN), true);
        for (String n : added) {
            source.sendSuccess(() -> Component.literal(" + " + n)
                    .withStyle(ChatFormatting.AQUA), false);
        }

        // 提示孤立条目（注册表有、train_maps/ 无对应文件），仅告知不删除
        Set<String> fileSet = new HashSet<>(files);
        List<String> orphans = new ArrayList<>();
        if (cfg.getMaps() != null) {
            for (MapEntry e : cfg.getMaps()) {
                if (e != null && e.id != null && !"random".equals(e.id) && !fileSet.contains(e.id)) {
                    orphans.add(e.id);
                }
            }
        }
        if (!orphans.isEmpty()) {
            final int orphanCount = orphans.size();
            source.sendSuccess(() -> Component.translatable("commands.sre.syncmaps.orphans", orphanCount)
                    .withStyle(ChatFormatting.GOLD), false);
            for (String o : orphans) {
                source.sendSuccess(() -> Component.literal(" ? " + o)
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        return addedCount;
    }
}
