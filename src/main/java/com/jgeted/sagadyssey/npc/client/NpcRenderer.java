package com.jgeted.sagadyssey.npc.client;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class NpcRenderer extends HumanoidMobRenderer<NpcBase, HumanoidModel<NpcBase>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("sagadyssey", "textures/entity/npc_farmer.png");

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcBase entity) {
        return TEXTURE;
    }
}
