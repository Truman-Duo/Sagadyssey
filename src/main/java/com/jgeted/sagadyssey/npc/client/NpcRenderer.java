package com.jgeted.sagadyssey.npc.client;

import com.jgeted.sagadyssey.core.SagadysseyClient;
import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class NpcRenderer extends HumanoidMobRenderer<NpcBase, NpcModel> {

    private static final ResourceLocation FALLBACK =
            ResourceLocation.fromNamespaceAndPath("sagadyssey", "textures/entity/npc_farmer.png");

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel(context.bakeLayer(SagadysseyClient.NPC_MODEL_LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcBase entity) {
        NpcProfession prof = entity.getProfession();
        if (prof == NpcProfession.MEDIC) {
            return ResourceLocation.fromNamespaceAndPath("sagadyssey", "textures/entity/npc_doctor.png");
        }
        if (prof == NpcProfession.FARMER) {
            return FALLBACK;
        }
        return FALLBACK;
    }
}
