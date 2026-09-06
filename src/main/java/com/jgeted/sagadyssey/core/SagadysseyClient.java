package com.jgeted.sagadyssey.core;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.core.gui.ResearchScreen;

import com.jgeted.sagadyssey.npc.client.NpcModel;
import com.jgeted.sagadyssey.npc.client.NpcRenderer;
import com.jgeted.sagadyssey.npc.faction.gui.ReputationChartScreen;
import com.jgeted.sagadyssey.npc.registry.NpcEntityTypes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端专用初始化。注册按键绑定。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = Sagadyssey.MOD_ID)
public class SagadysseyClient {
    public static final ModelLayerLocation NPC_MODEL_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("sagadyssey", "npc"), "main");

    public static final KeyMapping RESEARCH_KEY = new KeyMapping(
            "key.sagadyssey.research",
            GLFW.GLFW_KEY_K,
            "key.categories.sagadyssey"
    );

    public static final KeyMapping REPUTATION_KEY = new KeyMapping(
            "key.sagadyssey.reputation",
            GLFW.GLFW_KEY_R,
            "key.categories.sagadyssey"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(RESEARCH_KEY);
        event.register(REPUTATION_KEY);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NPC_MODEL_LAYER, NpcModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcEntityTypes.NPC_BASE.get(), NpcRenderer::new);
    }


    @EventBusSubscriber(value = Dist.CLIENT)
    public static class KeyHandler {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            while (RESEARCH_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new ResearchScreen());
            }
            while (REPUTATION_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new ReputationChartScreen());
            }
        }
    }
}
