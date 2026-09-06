package com.jgeted.sagadyssey.npc.faction.network;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.faction.FactionAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端→服务端：GUI 数据保鲜请求。
 * <p>
 * 声望图表界面打开时（init()）向服务端发送此包，
 * 服务端回传全量 FactionDataSyncPayload，确保 GUI 显示最新数据。
 */
public record RequestStandingsRefreshPacket() implements CustomPacketPayload {

    public static final Type<RequestStandingsRefreshPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "request_standings_refresh")
    );

    /** 空包：不需要任何字段 */
    public static final StreamCodec<ByteBuf, RequestStandingsRefreshPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestStandingsRefreshPacket());

    @Override
    public Type<RequestStandingsRefreshPacket> type() {
        return TYPE;
    }

    /** 服务端处理：回传全量声望数据 */
    public static void handle(final RequestStandingsRefreshPacket data,
                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                FactionAttachments.syncToClient(player);
            }
        });
    }
}
