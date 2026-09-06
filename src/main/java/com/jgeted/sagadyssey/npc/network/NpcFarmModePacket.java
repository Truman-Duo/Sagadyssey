package com.jgeted.sagadyssey.npc.network;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.entity.FarmMode;
import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端→服务端：设置农民 NPC 的务农模式。
 * 设置成功后同时命令 NPC 开始工作（WORK）。
 */
public record NpcFarmModePacket(int npcId, String modeName) implements CustomPacketPayload {

    public static final Type<NpcFarmModePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "npc_farm_mode")
    );

    public static final StreamCodec<ByteBuf, NpcFarmModePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.npcId);
                byte[] bytes = packet.modeName.getBytes();
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            },
            buf -> {
                int npcId = buf.readInt();
                int len = buf.readInt();
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                return new NpcFarmModePacket(npcId, new String(bytes));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final NpcFarmModePacket data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Entity entity = player.serverLevel().getEntity(data.npcId);

            if (!(entity instanceof NpcBase npc)) {
                player.displayClientMessage(Component.literal("§c这个 NPC 已经不存在了"), true);
                return;
            }
            if (!npc.isOwnedBy(player.getUUID())) {
                player.displayClientMessage(Component.literal("§c这个 NPC 不属于你"), true);
                return;
            }
            if (player.distanceToSqr(npc) > 64.0D) {
                player.displayClientMessage(Component.literal("§c你离 NPC 太远了"), true);
                return;
            }
            if (npc.getProfession() != NpcProfession.FARMER) {
                player.displayClientMessage(Component.literal("§e这个 NPC 不是农民"), true);
                return;
            }

            FarmMode mode;
            try {
                mode = FarmMode.valueOf(data.modeName);
            } catch (IllegalArgumentException e) {
                player.displayClientMessage(Component.literal("§c无效的务农模式"), true);
                return;
            }

            npc.setFarmMode(mode);
            npc.setCommand(NpcCommand.WORK);
            player.displayClientMessage(Component.literal(
                    "§a" + npc.getNpcName() + " 已切换到「" + mode.getDisplayName() + "」模式并开始工作"), true);
            if (mode == FarmMode.CANE) {
                player.displayClientMessage(Component.literal(
                        "§e提示：甘蔗模式需要给 NPC 甘蔗和两桶水"), false);
            }
        });
    }
}
