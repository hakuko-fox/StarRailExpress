package io.wifi.mixins.client;

import com.bawnorton.mixinsquared.TargetHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@Mixin(value = PlayerRenderer.class, priority = 2000)
public abstract class RatatouilleArmorFixMixin {

    // ─── MethodHandle 缓存 ────────────────────────────────────────────────────
    // null  = 未初始化
    // NOOP  = 查找失败（目标方法不存在，静默跳过）
    // other = 就绪，可直接 invoke

    private static final MethodHandle NOOP;
    private static volatile MethodHandle cachedRenderArmorArm = null;

    static {
        // 占位符，用于标记"查找过但失败"
        MethodHandle noop;
        try {
            noop = MethodHandles.lookup()
                    .findStatic(RatatouilleArmorFixMixin.class, "noopRenderArmorArm",
                            MethodType.methodType(void.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            // 理论上不会发生
            noop = null;
        }
        NOOP = noop;
    }

    @SuppressWarnings("unused")
    private static void noopRenderArmorArm() {}

    /**
     * 延迟初始化：第一次调用时通过 MethodHandles 查找
     * ratatouille$renderArmorArm，此时 Ratatouille 的 Mixin 已注入完毕。
     */
    private MethodHandle getRenderArmorArmHandle() {
        MethodHandle h = cachedRenderArmorArm;
        if (h != null) return h;

        synchronized (RatatouilleArmorFixMixin.class) {
            h = cachedRenderArmorArm;
            if (h != null) return h;

            try {
                // unreflectSpecial / findVirtual 均可；因为目标是 private @Unique，
                // 需要先拿到 privateLookupIn
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        PlayerRenderer.class,
                        MethodHandles.lookup()
                );
                h = lookup.findVirtual(
                        PlayerRenderer.class,
                        "ratatouille$renderArmorArm",
                        MethodType.methodType(
                                void.class,
                                PoseStack.class,
                                MultiBufferSource.class,
                                int.class,
                                AbstractClientPlayer.class,
                                boolean.class
                        )
                );
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Ratatouille 未加载或方法名变动，静默降级
                h = NOOP;
            }

            cachedRenderArmorArm = h;
        }
        return h;
    }

    private void invokeRenderArmorArm(PoseStack matrices, MultiBufferSource vertexConsumers,
                                      int light, AbstractClientPlayer player, boolean isRightArm) {
        MethodHandle mh = getRenderArmorArmHandle();
        if (mh == NOOP) return;
        try {
            mh.invoke(this, matrices, vertexConsumers, light, player, isRightArm);
        } catch (Throwable t) {
            throw new RuntimeException("ratatouille$renderArmorArm invocation failed", t);
        }
    }

    // ─── Right Hand ───────────────────────────────────────────────────────────

    @TargetHandler(
            mixin = "dev.doctor4t.ratatouille.mixin.client.armor.PlayerEntityRendererMixin",
            name  = "hadopelagic$renderArmorRightArm"
    )
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false)
    private void fix$renderArmorRightArm(
            PoseStack matrices, MultiBufferSource vertexConsumers, int light,
            AbstractClientPlayer player,
            CallbackInfo ci, CallbackInfo ci2)
    {
        ci2.cancel();
        invokeRenderArmorArm(matrices, vertexConsumers, light, player, true);
    }

    // ─── Left Hand ────────────────────────────────────────────────────────────

    @TargetHandler(
            mixin = "dev.doctor4t.ratatouille.mixin.client.armor.PlayerEntityRendererMixin",
            name  = "hadopelagic$renderArmorLeftArm"
    )
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false)
    private void fix$renderArmorLeftArm(
            PoseStack matrices, MultiBufferSource vertexConsumers, int light,
            AbstractClientPlayer player,
            CallbackInfo ci, CallbackInfo ci2)
    {
        ci2.cancel();
        invokeRenderArmorArm(matrices, vertexConsumers, light, player, false);
    }
}