package org.agmas.noellesroles.modifier;

import net.minecraft.resources.ResourceLocation;

public class AnimeModifiers {
    public static final String NAMESPACE = "anime";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static void init() {
    }
}
