package org.agmas.noellesroles.client;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class NRMixinPlugin implements IMixinConfigPlugin {
    private static final boolean IS_LAMBDYNLIGHTS_LOADED = FabricLoader.getInstance().isModLoaded("lambdynlights");
    private static final boolean IS_SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");

    public void onLoad(String mixinPackage) {
        System.out.println("Noelle's Roles: Mixin Plugin Loaded");
        System.out.println("LambDynLights loaded: " + IS_LAMBDYNLIGHTS_LOADED);
        System.out.println("Sodium loaded: " + IS_SODIUM_LOADED);
    }

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("LamBugFix") && !IS_LAMBDYNLIGHTS_LOADED) {
            return false;
        }
        if (mixinClassName.contains("sodium.HakukoFoxPOVSodiumSectionMixin") && !IS_SODIUM_LOADED) {
            return false;
        }
        return true;
    }

    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    public List<String> getMixins() {
        return List.of();
    }

    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
