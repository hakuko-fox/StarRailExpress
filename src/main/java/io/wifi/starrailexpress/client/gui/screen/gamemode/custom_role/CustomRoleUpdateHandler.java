package io.wifi.starrailexpress.client.gui.screen.gamemode.custom_role;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class CustomRoleUpdateHandler {
    public static void updateRoleSelection() {
        // 更新选择UI
        Minecraft client = Minecraft.getInstance();
        if(client.screen instanceof CustomRoleSelectScreen sc){
            sc.updateRoleSelection();
        }
    }
}
