package com.jgeted.sagadyssey;

import com.jgeted.sagadyssey.client.TestScreen;
import com.jgeted.sagadyssey.core.command.ResearchCommand;
import com.jgeted.sagadyssey.core.command.TestNetworkCommand;
import com.jgeted.sagadyssey.core.config.SagadysseyConfig;
import com.jgeted.sagadyssey.core.network.SagadysseyNetworking;
import com.jgeted.sagadyssey.core.research.ResearchAttachments;
import com.jgeted.sagadyssey.core.research.ResearchRegistry;
import com.jgeted.sagadyssey.registry.ModBlocks;
import com.jgeted.sagadyssey.registry.ModCreativeTabs;
import com.jgeted.sagadyssey.registry.ModItems;
import com.jgeted.sagadyssey.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Sagadyssey 模组主类。
 * 中世纪主题 Minecraft 扩展——NPC 聚落、蓝图建造、坐骑增强。
 */
@Mod(Sagadyssey.MOD_ID)
public class Sagadyssey {
    public static final String MOD_ID = "sagadyssey";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Sagadyssey(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Sagadyssey 初始化开始");

        // 注册配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, SagadysseyConfig.SPEC);

        // 客户端：注册配置界面扩展点（Mods 菜单里的 Config 按钮）
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }

        // 注册所有内容
        ModBlocks.REGISTRY.register(modEventBus);
        ModItems.REGISTRY.register(modEventBus);
        ModMenus.REGISTRY.register(modEventBus);
        ModCreativeTabs.REGISTRY.register(modEventBus);

        // 注册 Attachment（研究点数数据）
        ResearchAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // 初始化技能树
        ResearchRegistry.init();
        LOGGER.info("技能树已加载（{} 个节点）", ResearchRegistry.getAllNodes().size());

        // 注册网络数据包
        modEventBus.register(SagadysseyNetworking.class);
        LOGGER.info("网络通信已加载");

        // 注册命令
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> {
            TestNetworkCommand.register(event.getDispatcher());
            ResearchCommand.register(event.getDispatcher());
            LOGGER.info("命令已注册: /saga test, /research");
        });

        // 注册 GUI 事件
        modEventBus.addListener(this::onRegisterMenuScreens);
    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TEST_MENU.get(), TestScreen::new);
    }
}
