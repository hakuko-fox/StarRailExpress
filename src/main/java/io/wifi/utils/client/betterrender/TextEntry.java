package io.wifi.utils.client.betterrender;

import net.minecraft.network.chat.Component;

/**
 * Represents a single text draw call pending in the batch.
 */
public record TextEntry(
        Component text,
        float x,
        float y,
        int color,
        boolean shadow,
        long throttleKey,   // unique id for throttle cache; -1 = no throttle
        long intervalMs     // throttle interval; 0 = no throttle
) {
    /** Convenience constructor — no throttle, raw string */
    public static TextEntry of(String text, float x, float y, int color, boolean shadow) {
        return new TextEntry(Component.literal(text), x, y, color, shadow, -1, 0);
    }

    /** Convenience constructor — no throttle, Component */
    public static TextEntry of(Component text, float x, float y, int color, boolean shadow) {
        return new TextEntry(text, x, y, color, shadow, -1, 0);
    }
}