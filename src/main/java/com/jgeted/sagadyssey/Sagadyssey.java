package com.jgeted.sagadyssey;

import com.jgeted.sagadyssey.client.TestScreen;
import com.jgeted.sagadyssey.core.command.NpcSpawnCommand;
import com.jgeted.sagadyssey.core.command.ResearchCommand;
import com.jgeted.sagadyssey.core.command.StructureDiagnosticCommand;
import com.jgeted.sagadyssey.core.command.TestNetworkCommand;
import com.jgeted.sagadyssey.core.config.SagadysseyConfig;
import com.jgeted.sagadyssey.core.network.SagadysseyNetworking;
import com.jgeted.sagadyssey.core.research.ResearchAttachments;
import com.jgeted.sagadyssey.core.research.ResearchRegistry;
import com.jgeted.sagadyssey.core.registry.ModMenuTypes;
import com.jgeted.sagadyssey.core.structure.SagadysseyStructures;
import com.jgeted.sagadyssey.npc.event.NpcExperienceEvents;
import com.jgeted.sagadyssey.npc.event.NpcFactionEvents;
import com.jgeted.sagadyssey.npc.faction.FactionAttachments;
import com.jgeted.sagadyssey.npc.faction.FactionRegistry;
import com.jgeted.sagadyssey.npc.faction.StandingDecayHandler;
import com.jgeted.sagadyssey.npc.faction.command.FactionCommand;
import com.jgeted.sagadyssey.npc.faction.util.FactionDataLoader;
import com.jgeted.sagadyssey.npc.gui.NpcEquipScreen;
import com.jgeted.sagadyssey.npc.registry.NpcEntityTypes;
import com.jgeted.sagadyssey.registry.ModBlocks;
import com.jgeted.sagadyssey.registry.ModCreativeTabs;
import com.jgeted.sagadyssey.registry.ModItems;
import com.jgeted.sagadyssey.registry.ModMenus;
import com.jgeted.sagadyssey.vehicle.VehicleEventHandler;
import com.jgeted.sagadyssey.vehicle.command.MountCommand;
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
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Sagadyssey 模组主类。
 * 中世纪主题 Minecraft 扩展——NPC 聚落、蓝图建造、坐骑增强。
 */
@Mod(Sagadyssey.MOD_ID)
public class Sagadyssey {
    public static final String MOD_ID = "sagadyssey";
    public static final Logger LOGGER = LogUtils.getLogger();

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
        ModMenuTypes.REGISTRY.register(modEventBus);
        ModMenus.REGISTRY.register(modEventBus);
        ModCreativeTabs.REGISTRY.register(modEventBus);

        // 注册 Attachment（研究点数数据）
        ResearchAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // 注册 Attachment（阵营声望数据）
        FactionAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // 注册 NPC 实体 + 生成蛋
        NpcEntityTypes.REGISTRY.register(modEventBus);
        NpcEntityTypes.SPAWN_EGGS.register(modEventBus);
        modEventBus.addListener(NpcEntityTypes::registerAttributes);
        LOGGER.info("NPC 实体已注册");

        // 初始化技能树
        ResearchRegistry.init();
        LOGGER.info("技能树已加载（{} 个节点）", ResearchRegistry.getAllNodes().size());

        // 注册网络数据包
        modEventBus.register(SagadysseyNetworking.class);

        // 注册阵营 Registry（数据驱动 DataPackRegistry，mod bus）
        modEventBus.register(FactionRegistry.class);

        LOGGER.info("网络通信已加载");

        // 注册命令
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> {
            TestNetworkCommand.register(event.getDispatcher());
            ResearchCommand.register(event.getDispatcher());
            NpcSpawnCommand.register(event.getDispatcher());
            StructureDiagnosticCommand.register(event.getDispatcher());
            FactionCommand.register(event.getDispatcher());
            MountCommand.register(event.getDispatcher());
            LOGGER.info("命令已注册: /saga test, /research, /saga npc spawn, /saga structures dump, /sagadyssey faction, /saga mount");
        });

        // 注册 NPC 经验事件
        NeoForge.EVENT_BUS.register(NpcExperienceEvents.class);

        // 注册阵营声望事件处理器（击杀、睡眠、涟漪重置等）
        NeoForge.EVENT_BUS.register(new NpcFactionEvents());

        // 注册阵营声望衰减处理器
        NeoForge.EVENT_BUS.register(new StandingDecayHandler());

        // 注册 Vehicle 事件处理器
        NeoForge.EVENT_BUS.register(new VehicleEventHandler());

        // 注册结构生成系统（建筑 NBT + JSON 配置）
        SagadysseyStructures.STRUCTURE_TYPE_REGISTRY.register(modEventBus);
        SagadysseyStructures.PIECE_TYPE_REGISTRY.register(modEventBus);
        SagadysseyStructures.init();
        LOGGER.info("结构生成系统已加载（{} 个结构）", SagadysseyStructures.getAllIds().size());

        // 注册 GUI 事件
        modEventBus.addListener(this::onRegisterMenuScreens);

        // 初始化阵营数据（加载关系矩阵等非 Registry JSON）
        FactionDataLoader.init();
        LOGGER.info("阵营系统已初始化");

    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TEST_MENU.get(), TestScreen::new);
        event.register(ModMenuTypes.NPC_EQUIP.get(), NpcEquipScreen::new);
    }
}
