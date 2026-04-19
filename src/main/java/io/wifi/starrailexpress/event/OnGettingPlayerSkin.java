package io.wifi.starrailexpress.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 获取玩家皮肤事件。
 */
@Environment(EnvType.CLIENT)
public interface OnGettingPlayerSkin {
    public static class PlayerSkinResult {
        public static PlayerSkinResult DEFAULT = new PlayerSkinResult(null, 0);
        public static PlayerSkinResult SKIP = new PlayerSkinResult(null, -1);

        public final ResourceLocation texture;
        public final int type;

        public static PlayerSkinResult alexSlim() {
            return new PlayerSkinResult(ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png"));
        }

        public static PlayerSkinResult steveWide() {
            return new PlayerSkinResult(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"));
        }

        public PlayerSkinResult(ResourceLocation texture) {
            this.texture = texture;
            this.type = 1;
        }

        public PlayerSkinResult original() {
            return DEFAULT;
        }

        public PlayerSkinResult skip() {
            return SKIP;
        }

        private PlayerSkinResult(ResourceLocation texture, int type) {
            this.texture = texture;
            this.type = type;
        }
    }

    /**
     * 获取玩家皮肤事件。
     */
    Event<OnGettingPlayerSkin> EVENT = createArrayBacked(OnGettingPlayerSkin.class,
            listeners -> (player) -> {
                for (OnGettingPlayerSkin listener : listeners) {
                    var a = listener.onGetSkin(player);
                    if (a != null && a != PlayerSkinResult.SKIP) {
                        return a;
                    }
                }
                return null;
            });

    PlayerSkinResult onGetSkin(AbstractClientPlayer abstractClientPlayerEntity);
}