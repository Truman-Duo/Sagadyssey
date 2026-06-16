package com.jgeted.sagadyssey.core;

import com.jgeted.sagadyssey.core.gui.ResearchScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端专用初始化。注册按键绑定。
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class SagadysseyClient {
    public static final KeyMapping RESEARCH_KEY = new KeyMapping(
            "key.sagadyssey.research",
            GLFW.GLFW_KEY_K,
            "key.categories.sagadyssey"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(RESEARCH_KEY);
    }

    @EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class KeyHandler {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            while (RESEARCH_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new ResearchScreen());
            }
        }
    }
}
