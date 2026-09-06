package com.jgeted.sagadyssey.npc.faction.network;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.faction.StandingLevel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：增量声望更新（声望变化时）。
 * <p>
 * 包含主要变化的阵营及受涟漪影响的其他阵营变化。
 */
public record FactionStandingsUpdatePayload(
        String factionId,
        int newValue,
        StandingLevel newLevel,
        String reasonKey,
        List<AffectedFaction> rippleEffects
) implements CustomPacketPayload {

    public static final Type<FactionStandingsUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "faction_standings_update")
    );

    public static final StreamCodec<ByteBuf, FactionStandingsUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                writeString(buf, payload.factionId);
                buf.writeInt(payload.newValue);
                buf.writeByte(payload.newLevel.ordinal());
                writeString(buf, payload.reasonKey);
                buf.writeInt(payload.rippleEffects.size());
                for (var a : payload.rippleEffects) {
                    AffectedFaction.write(buf, a);
                }
            },
            buf -> {
                String factionId = readString(buf);
                int newValue = buf.readInt();
                StandingLevel level = StandingLevel.values()[buf.readByte()];
                String reasonKey = readString(buf);
                int count = buf.readInt();
                List<AffectedFaction> ripples = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    ripples.add(AffectedFaction.read(buf));
                }
                return new FactionStandingsUpdatePayload(factionId, newValue, level, reasonKey, ripples);
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
    public Type<FactionStandingsUpdatePayload> type() {
        return TYPE;
    }

    /** 客户端处理：更新 ClientFactionCache */
    public static void handle(final FactionStandingsUpdatePayload data,
                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientFactionCache.updateFromUpdate(data.factionId, data.newValue);
            for (var a : data.rippleEffects) {
                ClientFactionCache.updateFromUpdate(a.factionId(), a.newValue());
            }
        });
    }

    /** 涟漪影响的阵营 */
    public record AffectedFaction(
            String factionId,
            int delta,
            int newValue,
            StandingLevel newLevel
    ) {
        static void write(ByteBuf buf, AffectedFaction a) {
            FactionStandingsUpdatePayload.writeString(buf, a.factionId);
            buf.writeInt(a.delta);
            buf.writeInt(a.newValue);
            buf.writeByte(a.newLevel.ordinal());
        }

        static AffectedFaction read(ByteBuf buf) {
            return new AffectedFaction(
                    FactionStandingsUpdatePayload.readString(buf),
                    buf.readInt(),
                    buf.readInt(),
                    StandingLevel.values()[buf.readByte()]
            );
        }
    }
}
