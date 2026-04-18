package org.agmas.noellesroles.game.roles.neutral.monokuma;

import io.wifi.starrailexpress.content.item.RevolverItem;
import net.minecraft.world.item.Item;

/**
 * 黑白专用特制左轮手枪
 *
 * - 外观与普通左轮一致
 * - 50%概率命中致死
 * - 击杀好人后触发"好人误杀好人"机制
 * - 死亡后掉落，仅真正的义警或好人可捡起（黑白熊形态无法捡起）
 */
public class MonokumaRevolverItem extends RevolverItem {

    public MonokumaRevolverItem(Item.Properties settings) {
        super(settings.durability(4));
    }

//    @Override
//    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
//        ItemStack stack = user.getItemInHand(hand);
//
//        if (world.isClientSide) {
//            final var gameComponent = SREClient.gameComponent;
//            if (gameComponent != null) {
//                final var role = gameComponent.getRole(user);
//                if (role != null) {
//                    if (!role.onUseGun(user)) {
//                        return InteractionResultHolder.fail(stack);
//                    }
//                }
//            }
//            HitResult collision = getGunTarget(user);
//            if (collision instanceof EntityHitResult entityHitResult) {
//                Entity target = entityHitResult.getEntity();
//                ClientPlayNetworking.send(new GunShootPayload(target.getId()));
//                CrosshairaddonsCompat.arrowHit();
//            } else {
//                ClientPlayNetworking.send(new GunShootPayload(-1));
//            }
//            user.setXRot(user.getXRot() - 4);
//            spawnHandParticle();
//        } else {
//            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
//            final var role = gameWorldComponent.getRole(user);
//            if (role != null) {
//                if (!role.onUseGun(user)) {
//                    return InteractionResultHolder.fail(stack);
//                }
//            }
//        }
//        return InteractionResultHolder.consume(stack);
//    }

//    public static void spawnHandParticle() {
//        HandParticle handParticle = new HandParticle()
//                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
//                .setPos(0.1f, 0.275f, -0.2f)
//                .setMaxAge(3)
//                .setSize(0.5f)
//                .setVelocity(0f, 0f, 0f)
//                .setLight(15, 15)
//                .setAlpha(1f, 0.1f)
//                .setRenderLayer(TMMRenderLayers::additive);
//        SREClient.handParticleManager.spawn(handParticle);
//    }

//    public static HitResult getGunTarget(Player user) {
//        return ProjectileUtil.getHitResultOnViewVector(user,
//                entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player),
//                20f);
//    }

    @Override
    public String getItemSkinType() {
        return "revolver"; // 与普通左轮共用皮肤
    }
}
