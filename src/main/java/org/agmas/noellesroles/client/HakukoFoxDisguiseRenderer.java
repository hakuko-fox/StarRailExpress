package org.agmas.noellesroles.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Fox;

/**
 * 白狐：把玩家渲染成一隻白色狐狸。
 * 每個偽裝玩家持有一隻不入世界的客戶端狐狸實體，逐幀複製玩家的位置與姿態後交給狐狸渲染器繪製。
 */
public class HakukoFoxDisguiseRenderer {
    private static final Map<UUID, Fox> FOXES = new HashMap<>();

    public static boolean shouldDisguise(AbstractClientPlayer player) {
        return org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent.isDisguised(player);
    }

    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta,
            com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        Fox fox = getFox(player);
        if (fox == null) {
            return false;
        }
        if (fox.tickCount != player.tickCount) {
            fox.walkAnimation.update(player.walkAnimation.speed(), 1.0f);
            fox.tickCount = player.tickCount;
        }
        fox.setPos(player.getX(), player.getY(), player.getZ());
        fox.xo = player.xo;
        fox.yo = player.yo;
        fox.zo = player.zo;
        fox.yBodyRot = player.yBodyRot;
        fox.yBodyRotO = player.yBodyRotO;
        fox.yHeadRot = player.yHeadRot;
        fox.yHeadRotO = player.yHeadRotO;
        fox.setXRot(player.getXRot());
        fox.xRotO = player.xRotO;
        fox.setInvisible(player.isInvisible());
        fox.hurtTime = player.hurtTime;
        fox.setCustomName(null);
        fox.setCustomNameVisible(false);

        EntityRenderer<? super Fox> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(fox);
        renderer.render(fox, yaw, tickDelta, poseStack, bufferSource, packedLight);
        return true;
    }

    private static Fox getFox(AbstractClientPlayer player) {
        Fox fox = FOXES.get(player.getUUID());
        if (fox == null || fox.level() != player.level()) {
            fox = EntityType.FOX.create(player.level());
            if (fox != null) {
                fox.setVariant(Fox.Type.SNOW);
                FOXES.put(player.getUUID(), fox);
            }
        }
        return fox;
    }
}
