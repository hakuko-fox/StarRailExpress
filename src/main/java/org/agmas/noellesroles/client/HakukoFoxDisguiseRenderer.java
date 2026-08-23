package org.agmas.noellesroles.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.core.registries.Registries;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRolePlayerComponent;

/**
 * 白狐：把玩家渲染成一隻白色狐狸。
 * 每個偽裝玩家持有一隻不入世界的客戶端狐狸實體，逐幀複製玩家的位置與姿態後交給狐狸渲染器繪製。
 */
public class HakukoFoxDisguiseRenderer {
    private static final Map<UUID, Fox> FOXES = new HashMap<>();
    private static final Map<UUID, Cat> CATS = new HashMap<>();

    public static boolean shouldDisguise(AbstractClientPlayer player) {
        return org.agmas.noellesroles.game.roles.killer.hakukofox.HakukoFoxPlayerComponent.isDisguised(player)
                || VtuberRolePlayerComponent.KEY.maybeGet(player)
                        .map(VtuberRolePlayerComponent::isDisguised).orElse(false);
    }

    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta,
            com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        int disguise = VtuberRolePlayerComponent.KEY.maybeGet(player)
                .map(VtuberRolePlayerComponent::getDisguise).orElse(VtuberRolePlayerComponent.NONE);
        if (disguise == VtuberRolePlayerComponent.YOZORA_CAT) {
            return renderCat(player, yaw, tickDelta, poseStack, bufferSource, packedLight);
        }
        Fox fox = getFox(player, disguise == VtuberRolePlayerComponent.BLOOD_FOX);
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

    private static Fox getFox(AbstractClientPlayer player, boolean red) {
        Fox fox = FOXES.get(player.getUUID());
        if (fox == null || fox.level() != player.level()) {
            fox = EntityType.FOX.create(player.level());
            if (fox != null) {
                fox.setVariant(red ? Fox.Type.RED : Fox.Type.SNOW);
                FOXES.put(player.getUUID(), fox);
            }
        } else {
            fox.setVariant(red ? Fox.Type.RED : Fox.Type.SNOW);
        }
        return fox;
    }

    private static boolean renderCat(AbstractClientPlayer player, float yaw, float tickDelta,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        Cat cat = CATS.get(player.getUUID());
        if (cat == null || cat.level() != player.level()) {
            cat = EntityType.CAT.create(player.level());
            if (cat == null) {
                return false;
            }
            var registry = player.level().registryAccess().lookupOrThrow(Registries.CAT_VARIANT);
            cat.setVariant(registry.getOrThrow(CatVariant.WHITE));
            CATS.put(player.getUUID(), cat);
        }
        if (cat.tickCount != player.tickCount) {
            cat.walkAnimation.update(player.walkAnimation.speed(), 1.0f);
            cat.tickCount = player.tickCount;
        }
        cat.setPos(player.getX(), player.getY(), player.getZ());
        cat.xo = player.xo;
        cat.yo = player.yo;
        cat.zo = player.zo;
        cat.yBodyRot = player.yBodyRot;
        cat.yBodyRotO = player.yBodyRotO;
        cat.yHeadRot = player.yHeadRot;
        cat.yHeadRotO = player.yHeadRotO;
        cat.setXRot(player.getXRot());
        cat.xRotO = player.xRotO;
        cat.setInvisible(player.isInvisible());
        cat.hurtTime = player.hurtTime;
        cat.setCustomName(null);
        cat.setCustomNameVisible(false);
        EntityRenderer<? super Cat> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(cat);
        renderer.render(cat, yaw, tickDelta, poseStack, bufferSource, packedLight);
        return true;
    }
}
