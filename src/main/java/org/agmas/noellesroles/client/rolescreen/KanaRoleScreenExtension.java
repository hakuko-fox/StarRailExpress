/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.client.rolescreen;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.widget.KanaPlayerWidget;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;

/** 十七夜佳奈背包界面扩展：以玩家头像选择一名有效目标。 */
public final class KanaRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> helper = new RoleScreenHelper<>(
                player,
                ModRoles.KANA,
                this::createKanaWidget,
                TEXT_PROVIDER,
                null,
                this::getEligiblePlayers);
        helper.setNameExtractor(info -> info.getProfile().getName());
        return helper;
    }

    private KanaPlayerWidget createKanaWidget(LimitedInventoryScreen screen, int x, int y,
            PlayerInfo targetInfo, int index) {
        KanaPlayerWidget widget = new KanaPlayerWidget(screen, x, y, targetInfo);
        screen.addRoleWidget(widget);
        return widget;
    }

    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getConnection() == null) {
            return List.of();
        }

        return client.getConnection().getListedOnlinePlayers().stream()
                .filter(info -> !info.getProfile().getId().equals(client.player.getUUID()))
                .filter(info -> {
                    Player target = client.level.getPlayerByUUID(info.getProfile().getId());
                    return target != null && target.isAlive() && !target.isSpectator();
                })
                .toList();
    }
}
