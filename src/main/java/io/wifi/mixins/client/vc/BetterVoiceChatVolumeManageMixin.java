package io.wifi.mixins.client.vc;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.maxhenkel.voicechat.gui.volume.PlayerVolumeEntry;

/**
 * Marks the text renderer dirty once per game tick so HUD
 * computation only runs at 20 Hz instead of every render frame.
 */
@Mixin(PlayerVolumeEntry.class)
public class BetterVoiceChatVolumeManageMixin {

// @Inject(method = "tick", at = @At("HEAD"))
}