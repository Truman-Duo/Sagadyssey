package com.jgeted.sagadyssey.npc.network;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.faction.Faction;
import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.gui.NpcCommandScreen;
import com.jgeted.sagadyssey.npc.gui.NpcRecruitScreen;
import com.jgeted.sagadyssey.npc.trade.NpcTradeOffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：NPC 完整属性数据（含交易列表、坐骑信息）。
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
        String commandName,
        String farmModeName,
        String factionName,
        String originalFaction,
        List<byte[]> rawTrades,
        boolean hasMount,
        int mountType,
        float mountHp,
        float mountMaxHp,
        float mountSpeed,
        String mountName,
        boolean leadMountMode,
        boolean mountLeashed
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
                writeString(buf, packet.farmModeName());
                writeString(buf, packet.factionName());
                writeString(buf, packet.originalFaction());
                // 交易数据：每个交易 = 完整 ItemStack 字节序列
                buf.writeInt(packet.rawTrades.size());
                for (byte[] t : packet.rawTrades) {
                    buf.writeInt(t.length);
                    buf.writeBytes(t);
                }
                // 坐骑信息
                buf.writeBoolean(packet.hasMount);
                buf.writeInt(packet.mountType);
                buf.writeFloat(packet.mountHp);
                buf.writeFloat(packet.mountMaxHp);
                buf.writeFloat(packet.mountSpeed);
                writeString(buf, packet.mountName);
                buf.writeBoolean(packet.leadMountMode);
                buf.writeBoolean(packet.mountLeashed);
            },
            buf -> {
                int npcId = buf.readInt();
                String npcName = readString(buf);
                String profName = readString(buf);
                float curHp = buf.readFloat();
                float maxHp = buf.readFloat();
                float atk = buf.readFloat();
                float spd = buf.readFloat();
                float arm = buf.readFloat();
                int lvl = buf.readInt();
                int exp = buf.readInt();
                int kills = buf.readInt();
                int moral = buf.readInt();
                int cost = buf.readInt();
                boolean owned = buf.readBoolean();
                String cmd = NpcCommand.values()[buf.readByte()].name();
                String farmModeName = readString(buf);
                String factionName = readString(buf);
                String originalFaction = readString(buf);
                int tradeCount = buf.readInt();
                List<byte[]> trades = new ArrayList<>();
                for (int i = 0; i < tradeCount; i++) {
                    int len = buf.readInt();
                    byte[] data = new byte[len];
                    buf.readBytes(data);
                    trades.add(data);
                }
                boolean hasMount = buf.readBoolean();
                int mountType = buf.readInt();
                float mountHp = buf.readFloat();
                float mountMaxHp = buf.readFloat();
                float mountSpeed = buf.readFloat();
                String mountName = readString(buf);
                boolean leadMountMode = buf.readBoolean();
                boolean mountLeashed = buf.readBoolean();
                return new NpcStatsPayload(npcId, npcName, profName, curHp, maxHp, atk, spd, arm,
                        lvl, exp, kills, moral, cost, owned, cmd, farmModeName, factionName, originalFaction, trades,
                        hasMount, mountType, mountHp, mountMaxHp, mountSpeed, mountName,
                        leadMountMode, mountLeashed);
            }
    );

    /** 将 rawTrades 还原为 NpcTradeOffer 列表 */
    public List<NpcTradeOffer> buildTrades() {
        List<NpcTradeOffer> result = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return result;
        for (byte[] t : rawTrades) {
            io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.wrappedBuffer(t);
            net.minecraft.network.RegistryFriendlyByteBuf fbuf =
                    new net.minecraft.network.RegistryFriendlyByteBuf(buf, mc.level.registryAccess());
            ItemStack costItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(fbuf);
            int costMin = fbuf.readInt();
            int costMax = fbuf.readInt();
            ItemStack resultItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(fbuf);
            int resultMin = fbuf.readInt();
            int resultMax = fbuf.readInt();
            int minNpcLevel = fbuf.readInt();
            if (costItem.isEmpty() || resultItem.isEmpty()) continue;
            result.add(new NpcTradeOffer(costItem, costMin, costMax,
                    resultItem, resultMin, resultMax, minNpcLevel));
        }
        return result;
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes();
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

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

    public static void handle(final NpcStatsPayload data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (data.isOwned) {
                mc.setScreen(new NpcCommandScreen(data));
            } else {
                mc.setScreen(new NpcRecruitScreen(data));
            }
        });
    }

    public static NpcStatsPayload from(NpcBase npc) {
        int cost = npc.getRecruitmentCost();
        var faction = npc.getFaction();
        if (faction != null && faction.canBeHostile()) {
            cost *= 2;
        }
        // 序列化交易数据（完整 ItemStack，含附魔）
        List<byte[]> raw = new ArrayList<>();
        for (NpcTradeOffer t : npc.getActiveTrades()) {
            io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
            net.minecraft.network.RegistryFriendlyByteBuf fbuf =
                    new net.minecraft.network.RegistryFriendlyByteBuf(buf, npc.level().registryAccess());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(fbuf, t.costItem());
            fbuf.writeInt(t.costMin());
            fbuf.writeInt(t.costMax());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(fbuf, t.resultItem());
            fbuf.writeInt(t.resultMin());
            fbuf.writeInt(t.resultMax());
            fbuf.writeInt(t.minNpcLevel());
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();
            raw.add(bytes);
        }

        // 坐骑信息
        boolean hasMount = npc.hasMount();
        int mountType = 0;
        float mountHp = 0, mountMaxHp = 0, mountSpeed = 0;
        String mountName = "无";
        if (hasMount) {
            var mount = npc.getMount();
            if (mount instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h) {
                var type = h.getType();
                if (type == net.minecraft.world.entity.EntityType.DONKEY) {
                    mountType = 2;
                } else if (type == net.minecraft.world.entity.EntityType.MULE) {
                    mountType = 3;
                } else {
                    mountType = 1; // HORSE or other
                }
                mountHp = h.getHealth();
                mountMaxHp = h.getMaxHealth();
                mountSpeed = (float) h.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                mountName = h.getName().getString();
            }
        }

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
                cost,
                npc.isOwned(),
                npc.getCommand().name(),
                npc.getFarmMode().name(),
                npc.getFaction() != null ? npc.getFaction().id() : "sagadyssey:wilderness",
                npc.getOriginalFaction() != null ? npc.getOriginalFaction() : "",
                raw,
                hasMount, mountType, mountHp, mountMaxHp, mountSpeed, mountName,
                npc.isLeadMountMode(),
                npc.isMountLeashed()
        );
    }

    /** 同步 NPC 属性到所有附近玩家 */
    public static void sync(NpcBase npc) {
        if (!npc.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(npc, from(npc));
        }
    }
}
