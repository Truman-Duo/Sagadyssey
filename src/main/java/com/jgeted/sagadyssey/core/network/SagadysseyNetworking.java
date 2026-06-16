package com.jgeted.sagadyssey.core.network;

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
    }
}
