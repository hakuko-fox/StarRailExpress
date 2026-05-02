package io.wifi.events.day_night_fight.gui;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class DNFMenus {
    public static final ExtendedScreenHandlerType<HotbarStorageMenu, BlockPos> HOTBAR_STORAGE = Registry.register(
            BuiltInRegistries.MENU,
            SRE.id("dnf_hotbar_storage"),
            new ExtendedScreenHandlerType<>(HotbarStorageMenu::new, BlockPos.STREAM_CODEC.cast()));

    private DNFMenus() {
    }

    public static void initialize() {
    }
}
