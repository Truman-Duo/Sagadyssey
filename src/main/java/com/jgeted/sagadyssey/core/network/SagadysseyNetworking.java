package com.jgeted.sagadyssey.core.network;

import com.jgeted.sagadyssey.npc.faction.network.FactionDataSyncPayload;
import com.jgeted.sagadyssey.npc.faction.network.FactionStandingsUpdatePayload;
import com.jgeted.sagadyssey.npc.faction.network.RequestStandingsRefreshPacket;
import com.jgeted.sagadyssey.npc.network.NpcFarmModePacket;
import com.jgeted.sagadyssey.npc.network.NpcInteractionPacket;
import com.jgeted.sagadyssey.npc.network.NpcProfessionPacket;
import com.jgeted.sagadyssey.npc.network.NpcStatsPayload;
import com.jgeted.sagadyssey.npc.network.NpcTradePacket;
import com.jgeted.sagadyssey.npc.network.NpcTradeResponsePacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Sagadyssey 网络通信注册中心。
 * 所有数据包类型在这里统一注册。
 */
public class SagadysseyNetworking {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // 客户端→服务端：研究解锁请求
        registrar.playToServer(
                ResearchUnlockPacket.TYPE,
                ResearchUnlockPacket.STREAM_CODEC,
                ResearchUnlockPacket::handle
        );

        // 客户端→服务端：聊天消息（测试用）
        registrar.playToServer(
                ChatPayload.TYPE,
                ChatPayload.STREAM_CODEC,
                (payload, context) -> {
                    var player = context.player();
                    var message = net.minecraft.network.chat.Component.literal(
                            "§6[Sagadyssey]§r " + payload.message());
                    player.sendSystemMessage(message);
                }
        );

        // 服务端→客户端：研究数据同步
        registrar.playToClient(
                ResearchSyncPayload.TYPE,
                ResearchSyncPayload.STREAM_CODEC,
                ResearchSyncPayload::handle
        );

        // 客户端→服务端：NPC 交互
        registrar.playToServer(
                NpcInteractionPacket.TYPE,
                NpcInteractionPacket.STREAM_CODEC,
                NpcInteractionPacket::handle
        );

        // 服务端→客户端：NPC 属性同步
        registrar.playToClient(
                NpcStatsPayload.TYPE,
                NpcStatsPayload.STREAM_CODEC,
                NpcStatsPayload::handle
        );

        // 客户端→服务端：NPC 职业切换
        registrar.playToServer(
                NpcProfessionPacket.TYPE,
                NpcProfessionPacket.STREAM_CODEC,
                NpcProfessionPacket::handle
        );

        // 客户端→服务端：农民务农模式切换
        registrar.playToServer(
                NpcFarmModePacket.TYPE,
                NpcFarmModePacket.STREAM_CODEC,
                NpcFarmModePacket::handle
        );

        // 客户端→服务端：NPC 交易
        registrar.playToServer(
                NpcTradePacket.TYPE,
                NpcTradePacket.STREAM_CODEC,
                NpcTradePacket::handle
        );

        // 服务端→客户端：交易回应（经验刷新）
        registrar.playToClient(
                NpcTradeResponsePacket.TYPE,
                NpcTradeResponsePacket.STREAM_CODEC,
                NpcTradeResponsePacket::handle
        );

        // === 阵营声望网络包 ===

        // 服务端→客户端：全量声望同步
        registrar.playToClient(
                FactionDataSyncPayload.TYPE,
                FactionDataSyncPayload.STREAM_CODEC,
                FactionDataSyncPayload::handle
        );

        // 服务端→客户端：增量声望更新
        registrar.playToClient(
                FactionStandingsUpdatePayload.TYPE,
                FactionStandingsUpdatePayload.STREAM_CODEC,
                FactionStandingsUpdatePayload::handle
        );

        // 客户端→服务端：GUI 数据保鲜请求
        registrar.playToServer(
                RequestStandingsRefreshPacket.TYPE,
                RequestStandingsRefreshPacket.STREAM_CODEC,
                RequestStandingsRefreshPacket::handle
        );
    }
}
