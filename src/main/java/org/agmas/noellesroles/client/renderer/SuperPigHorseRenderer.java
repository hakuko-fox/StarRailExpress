package org.agmas.noellesroles.client.renderer;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.SuperPigHorseEntity;

/**
 * 超级猪马渲染器：复用原版马模型（放大），纹理换成超级猪马皮肤。
 */
public class SuperPigHorseRenderer
        extends AbstractHorseRenderer<SuperPigHorseEntity, HorseModel<SuperPigHorseEntity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "textures/entity/super_pig_horse.png");

    public SuperPigHorseRenderer(EntityRendererProvider.Context context) {
        super(context, new HorseModel<>(context.bakeLayer(ModelLayers.HORSE)), 1.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(SuperPigHorseEntity entity) {
        return TEXTURE;
    }
}
