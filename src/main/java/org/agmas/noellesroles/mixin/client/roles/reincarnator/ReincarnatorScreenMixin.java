package org.agmas.noellesroles.mixin.client.roles.reincarnator;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.agmas.noellesroles.client.widget.ReincarnatorProgressWidget;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在背包 {@link LimitedInventoryScreen} 中为轮回者注入进度面板（参照 SwapperScreenMixin 思路）。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class ReincarnatorScreenMixin extends LimitedHandledScreen<InventoryMenu> {

    public ReincarnatorScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void noellesroles$reincarnatorInit(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) return;
        if (!SREClient.gameComponent.isRole(player, ModRoles.REINCARNATOR)) return;

        int w = 92;
        int h = 150;
        int x = this.width / 2 + 88;
        int y = this.height / 2 - 83;
        this.addRenderableWidget(new ReincarnatorProgressWidget(x, y, w, h));
    }
}
