package io.wifi.starrailexpress.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class SREMixinPlugin implements IMixinConfigPlugin {

    private static boolean modVersionAtLeast(String modId, String minVersion) {
        var container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) {
            return false;
        }
        try {
            return container.get().getMetadata().getVersion()
                    .compareTo(Version.parse(minVersion)) >= 0;
        } catch (VersionParsingException e) {
            // 无法解析视为新版本
            return true;
        }
    }
    @Override
    public void onLoad(String s) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
        boolean sodiumExtraLoaded = FabricLoader.getInstance().isModLoaded("sodium-extra");
        // sodium 0.8+（mc1.21.1-0.8.x 向后移植）内部结构与 0.6 不同，按运行时版本二选一
        boolean sodiumModern = sodiumLoaded && modVersionAtLeast("sodium", "0.7.0");

        if (!sodiumExtraLoaded
                && mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.sodium_extra")) {
            return false;
        }
        // sodium 0.6 专用集（独立源集，针对 0.6.13 编译）
        if (mixinClassName.startsWith("io.wifi.starrailexpress.mixin.sodium6.")) {
            if (mixinClassName.contains("SodiumExtra")) {
                return sodiumExtraLoaded && !modVersionAtLeast("sodium-extra", "0.8.0");
            }
            return sodiumLoaded && !sodiumModern;
        }
        // 主源集中依赖 sodium 0.8 内部结构的 mixin
        if (mixinClassName.equals("net.exmo.mixin.client.side.DefaultChunkRendererMixin")
                || mixinClassName.equals("net.exmo.mixin.client.side.RenderSectionManagerMixin")) {
            return sodiumModern;
        }
        // iris 专属 mixin（SodiumShader / SodiumTransformer 目标类属于 iris）
        if ((mixinClassName.equals("net.exmo.mixin.client.side.SodiumShaderMixin")
                || mixinClassName.equals("net.exmo.mixin.client.side.SodiumTransformerMixin"))
                && !FabricLoader.getInstance().isModLoaded("iris")) {
            return false;
        }
        if (!sodiumLoaded
                && (mixinClassName.contains("net.exmo.mixin.client.side")
                        || mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.sodium."))) {
            return false;
        }

        if (!FabricLoader.getInstance().isModLoaded("carpet")
                && mixinClassName.contains("io.wifi.starrailexpress.mixin.compat.carpet")) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode classNode, String mixinClassName, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
