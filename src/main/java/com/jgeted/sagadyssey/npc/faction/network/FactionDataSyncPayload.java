package com.jgeted.sagadyssey.npc.faction.network;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.faction.FactionRelationMatrix;
import com.jgeted.sagadyssey.npc.faction.FactionRegistry;
import com.jgeted.sagadyssey.npc.faction.FactionStandings;
import com.jgeted.sagadyssey.npc.faction.InterFactionRelation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务端→客户端：全量声望数据同步（玩家登录时）。
 * <p>
 * 包含所有阵营的声望值和轻量关系摘要（供 GUI 渲染关系网络图）。
 */
public record FactionDataSyncPayload(
        Map<String, Integer> standings,
        Map<String, String> relationSummary,
        String playerFactionName
) implements CustomPacketPayload {

    public static final Type<FactionDataSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "faction_data_sync")
    );

    public static final StreamCodec<ByteBuf, FactionDataSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                // standings map
                buf.writeInt(payload.standings.size());
                for (var entry : payload.standings.entrySet()) {
                    writeString(buf, entry.getKey());
                    buf.writeInt(entry.getValue());
                }
                // relationSummary map
                buf.writeInt(payload.relationSummary.size());
                for (var entry : payload.relationSummary.entrySet()) {
                    writeString(buf, entry.getKey());
                    writeString(buf, entry.getValue());
                }
                // playerFactionName
                writeString(buf, payload.playerFactionName != null ? payload.playerFactionName : "");
            },
            buf -> {
                // standings map
                int size = buf.readInt();
                Map<String, Integer> standings = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    String key = readString(buf);
                    int value = buf.readInt();
                    standings.put(key, value);
                }
                // relationSummary map
                int relSize = buf.readInt();
                Map<String, String> relations = new HashMap<>();
                for (int i = 0; i < relSize; i++) {
                    String key = readString(buf);
                    String value = readString(buf);
                    relations.put(key, value);
                }
                // playerFactionName
                String name = readString(buf);
                return new FactionDataSyncPayload(standings, relations, name.isEmpty() ? null : name);
            }
    );

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public Type<FactionDataSyncPayload> type() {
        return TYPE;
    }

    /** 客户端处理：更新 ClientFactionCache */
    public static void handle(final FactionDataSyncPayload data,
                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientFactionCache.updateFromSync(data.standings);
            ClientFactionCache.updateRelationSummary(data.relationSummary);
            ClientFactionCache.setPlayerFactionName(data.playerFactionName);
        });
    }

    /** 从 FactionStandings 构建 Payload（服务端调用） */
    public static FactionDataSyncPayload from(FactionStandings standings) {
        return new FactionDataSyncPayload(
                new HashMap<>(standings.getStandingsMap()),
                buildRelationSummary(),
                standings.getPlayerFactionName()
        );
    }

    /** 构建轻量关系摘要（供客户端 GUI 渲染） */
    private static Map<String, String> buildRelationSummary() {
        Map<String, String> summary = new HashMap<>();
        var matrix = FactionRelationMatrix.getInstance();
        var factions = FactionRegistry.getAllFactions();
        for (var a : factions) {
            for (var b : factions) {
                if (a.id().equals(b.id())) continue;
                InterFactionRelation rel = matrix.getRelation(a, b);
                if (rel != InterFactionRelation.NEUTRAL) {
                    summary.put(a.id() + "→" + b.id(), rel.name());
                }
            }
        }
        return summary;
    }
}
