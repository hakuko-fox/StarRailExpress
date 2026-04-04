package io.wifi.starrailexpress.mixin.gui;

import com.kreezcraft.localizedchat.CommonClass;
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Player.class)
public class CommonTalkChatMixin {
    @Unique
    private static MutableComponent somePrefix(Player mainPlayer) {
        if (mainPlayer instanceof ServerPlayer ){
                return NameTagInventoryComponent.KEY.get(mainPlayer).generate();
        }
        return Component.literal("");
    }


    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    public void getDisplayName(CallbackInfoReturnable<Component> cir) {
        Player mainPlayer = (Player) (Object) this;
        if (mainPlayer instanceof ServerPlayer ){
            cir.setReturnValue(somePrefix(mainPlayer).append(mainPlayer.getDisplayName()));
        }
    }
}
