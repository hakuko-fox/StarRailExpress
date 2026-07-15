package io.wifi;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import io.wifi.starrailexpress.SRE;

import java.util.List;

public class SodiumExtraLightOptionMixinBlocker implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        List<String> bannedMixins = List.of(
                "me.flashyreese.mods.sodiumextra.mixin.light_updates.MixinLevelLightEngine" // 光照
        );
        // ItemFrameRenderer
        if (bannedMixins.contains(mixinClassName)
                || mixinClassName.contains("me.flashyreese.mods.sodiumextra.mixin.fog")
                || mixinClassName.contains("me.flashyreese.mods.sodiumextra.mixin.render.entity")) {
            SRE.LOGGER.info("Blocked sodium-extra mixin: [" + mixinClassName + "]");
            return true;
        }
        return false;
    }
}
