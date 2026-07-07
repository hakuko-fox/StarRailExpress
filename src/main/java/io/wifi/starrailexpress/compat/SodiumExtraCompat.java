package io.wifi.starrailexpress.compat;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.loader.api.FabricLoader;

public final class SodiumExtraCompat {

    private SodiumExtraCompat() {
    }

    /**
     * 强制 sodium-extra 的"光照更新"选项保持开启（关灯玩法依赖光照更新）。
     * sodium-extra 0.6 与 0.9 的 SodiumExtraGameOptions 类 FQN 不同
     * （client.gui vs client.config），因此用反射以同时兼容两个版本；
     * 相应的作弊 mixin 本身已由 SodiumExtraLightOptionMixinBlocker 屏蔽。
     */
    public static void forceLightUpdates() {
        if (!FabricLoader.getInstance().isModLoaded("sodium-extra")) {
            return;
        }
        try {
            Object options = Class.forName("me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod")
                    .getMethod("options")
                    .invoke(null);
            Object renderSettings = options.getClass().getField("renderSettings").get(options);
            renderSettings.getClass().getField("lightUpdates").set(renderSettings, true);
        } catch (ReflectiveOperationException e) {
            SRE.LOGGER.warn("Failed to force sodium-extra light updates option", e);
        }
    }
}
