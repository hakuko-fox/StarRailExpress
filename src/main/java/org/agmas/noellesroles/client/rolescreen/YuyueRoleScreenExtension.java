/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.client.rolescreen;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.NoteScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleInventoryScreenExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 羽月背包界面擴充：保留商店並提供便利貼文字編輯入口。 */
public final class YuyueRoleScreenExtension implements RoleInventoryScreenExtension {

    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;

    @Override
    public void onInventoryScreenInitTail(LimitedInventoryScreen screen) {
        int x = screen.width / 2 - BUTTON_WIDTH / 2;
        int y = Math.min(screen.height - BUTTON_HEIGHT - 4, screen.height / 2 + 92);
        screen.addRoleWidget(Button.builder(
                Component.translatable("gui.noellesroles.yuyue.edit_note"),
                button -> Minecraft.getInstance().setScreen(new NoteScreen()))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }
}
