package com.jgeted.sagadyssey.core.network;

import com.jgeted.sagadyssey.core.research.ClientResearchCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/**
 * 服务端→客户端同步包。推送玩家的研究点数和已解锁科技列表。
 */
public record ResearchSyncPayload(int points, Set<String> unlocked) implements CustomPacketPayload {

    public static final Type<ResearchSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SagadysseyNetworking.MOD_ID, "research_sync")
    );

    public static final StreamCodec<ByteBuf, ResearchSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.points);
                buf.writeInt(packet.unlocked.size());
                for (String id : packet.unlocked) {
                    byte[] bytes = id.getBytes();
                    buf.writeInt(bytes.length);
                    buf.writeBytes(bytes);
                }
            },
            buf -> {
                int points = buf.readInt();
                int count = buf.readInt();
                Set<String> unlocked = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    int len = buf.readInt();
                    byte[] bytes = new byte[len];
                    buf.readBytes(bytes);
                    unlocked.add(new String(bytes));
                }
                return new ResearchSyncPayload(points, unlocked);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ResearchSyncPayload data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientResearchCache.set(data.points, data.unlocked);
        });
    }
}
