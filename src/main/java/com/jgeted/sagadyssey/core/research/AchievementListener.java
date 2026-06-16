package com.jgeted.sagadyssey.core.research;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.core.config.SagadysseyConfig;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 监听原版进度完成事件，给予研究点数。
 * 绿框（TASK + GOAL）= 2 点，紫框（CHALLENGE）= 5 点。
 */
@EventBusSubscriber(modid = Sagadyssey.MOD_ID)
public class AchievementListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        var advancement = event.getAdvancement();
        var id = advancement.id();

        // 跳过配方解锁
        if (id.getPath().startsWith("recipes/")) return;

        var player = event.getEntity();
        AdvancementType type = advancement.value().display()
                .map(display -> display.getType()).orElse(AdvancementType.TASK);

        int points = (type == AdvancementType.CHALLENGE)
                ? SagadysseyConfig.CHALLENGE_ADVANCEMENT_POINTS.get()
                : SagadysseyConfig.NORMAL_ADVANCEMENT_POINTS.get();

        ResearchAttachments.addPoints(player, points);
        player.displayClientMessage(
                Component.literal("§6[Sagadyssey]§r 完成进度获得 " + points + " 研究点数！"),
                false
        );
        LOGGER.info("玩家 {} 完成进度 {}，获得 {} 研究点数", player.getName().getString(), id, points);
    }

    /** 玩家登录时同步研究数据到客户端 */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResearchAttachments.syncToClient(player);
        }
    }
}
