package com.jgeted.sagadyssey.npc.network;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.gui.NpcCommandScreen;
import com.jgeted.sagadyssey.npc.gui.NpcRecruitScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端→客户端：NPC 完整属性数据。
 * 玩家右键 NPC 时，服务端收集所有属性发回客户端，客户端据此打开对应 GUI。
 */
public record NpcStatsPayload(
        int npcId,
        String npcName,
        String professionName,
        float currentHp,
        float maxHp,
        float attackDamage,
        float speed,
        float armor,
        int npcLevel,
        int experience,
        int kills,
        int moral,
        int recruitmentCost,
        boolean isOwned,
        String commandName
) implements CustomPacketPayload {

    public static final Type<NpcStatsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "npc_stats")
    );

    public static final StreamCodec<ByteBuf, NpcStatsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.npcId);
                writeString(buf, packet.npcName);
                writeString(buf, packet.professionName);
                buf.writeFloat(packet.currentHp);
                buf.writeFloat(packet.maxHp);
                buf.writeFloat(packet.attackDamage);
                buf.writeFloat(packet.speed);
                buf.writeFloat(packet.armor);
                buf.writeInt(packet.npcLevel);
                buf.writeInt(packet.experience);
                buf.writeInt(packet.kills);
                buf.writeInt(packet.moral);
                buf.writeInt(packet.recruitmentCost);
                buf.writeBoolean(packet.isOwned);
                buf.writeByte(NpcCommand.valueOf(packet.commandName()).ordinal());
            },
            buf -> new NpcStatsPayload(
                    buf.readInt(),
                    readString(buf),
                    readString(buf),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    NpcCommand.values()[buf.readByte()].name()
            )
    );

    /** 写入字符串：4 字节长度 + 内容 */
    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes();
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /** 读取字符串：4 字节长度 + 内容 */
    private static String readString(ByteBuf buf) {
        int len = buf.readInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：根据 isOwned 决定打开招募界面还是装备界面。
     * 目前装备界面未实现，已拥有 NPC 暂不打开任何界面。
     */
    public static void handle(final NpcStatsPayload data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (data.isOwned) {
                mc.setScreen(new NpcCommandScreen(data));
            } else {
                // 未被招募 → 打开招募界面
                mc.setScreen(new NpcRecruitScreen(data));
            }
        });
    }

    /**
     * 从 NpcBase 实体收集属性数据，构建数据包。
     */
    public static NpcStatsPayload from(NpcBase npc) {
        return new NpcStatsPayload(
                npc.getId(),
                npc.getNpcName(),
                npc.getProfession().getDisplayName(),
                npc.getCurrentHp(),
                npc.getMaxHp(),
                npc.getAttackDamage(),
                npc.getSpeed(),
                npc.getArmorValue(),
                npc.getNpcLevel(),
                npc.getExperience(),
                npc.getKills(),
                npc.getMoral(),
                npc.getRecruitmentCost(),
                npc.isOwned(),
                npc.getCommand().name()
        );
    }
}
