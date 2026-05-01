package io.wifi.starrailexpress.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;


@Mixin(BundleItem.class)
public class BundleItemMixin {

    private static final float CUSTOM_BUNDLE_MAX_WEIGHT_FLOAT = 16.0F;

    @ModifyConstant(method = "appendHoverText", constant = @Constant(intValue = 64,ordinal = 1))
    private int modifyTooltipMaxWeight(int original) {
        return 1024; // 与新上限一致
    }

        @ModifyReturnValue(
                method = "getFullnessDisplay",
                at = @At("RETURN")
        )
        private static float modifyFullnessDisplay(float original, ItemStack stack) {
            BundleContents contents = stack.getOrDefault(
                    DataComponents.BUNDLE_CONTENTS,
                    BundleContents.EMPTY
            );

            return contents.weight().floatValue() / CUSTOM_BUNDLE_MAX_WEIGHT_FLOAT;
        }

    @ModifyExpressionValue(
            method = "getBarWidth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;mulAndTruncate(Lorg/apache/commons/lang3/math/Fraction;I)I"
            )
    )
    private int modifyBarWidthValue(int original, ItemStack stack) {
        BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        float fullness = contents.weight().floatValue() / CUSTOM_BUNDLE_MAX_WEIGHT_FLOAT;

        return Math.min(1 + (int)(fullness * 12.0F), 13);
    }
}