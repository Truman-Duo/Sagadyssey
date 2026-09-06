package com.jgeted.sagadyssey.npc.network;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.faction.Faction;
import com.jgeted.sagadyssey.npc.faction.StandingModifier;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import com.jgeted.sagadyssey.npc.trade.NpcTradeOffer;
import com.jgeted.sagadyssey.npc.trade.NpcTradeRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 客户端→服务端：请求执行交易。
 */
public record NpcTradePacket(int npcId, int tradeIndex) implements CustomPacketPayload {

    public static final Type<NpcTradePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "npc_trade")
    );

    public static final StreamCodec<ByteBuf, NpcTradePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeInt(packet.npcId); buf.writeInt(packet.tradeIndex); },
            buf -> new NpcTradePacket(buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final NpcTradePacket data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Entity entity = player.serverLevel().getEntity(data.npcId);

            if (!(entity instanceof NpcBase npc)) {
                player.displayClientMessage(Component.literal("§cNPC 不存在"), true);
                return;
            }

            if (player.distanceToSqr(npc) > 36.0D) {
                player.displayClientMessage(Component.literal("§c太远了"), true);
                return;
            }

            // 货币兑换（负索引）—— 仅商人
            NpcTradeOffer offer;
            if (data.tradeIndex < 0) {
                if (npc.getProfession() != NpcProfession.TRADER) {
                    player.displayClientMessage(Component.literal("§c只有商人提供货币兑换"), true);
                    return;
                }
                try {
                    offer = NpcTradeRegistry.getCurrencyExchange(-data.tradeIndex - 1);
                } catch (IllegalArgumentException e) {
                    player.displayClientMessage(Component.literal("§c无效的兑换"), true);
                    return;
                }
            } else {
                List<NpcTradeOffer> trades = npc.getActiveTrades();
                if (data.tradeIndex < 0 || data.tradeIndex >= trades.size()) {
                    player.displayClientMessage(Component.literal("§c无效的交易"), true);
                    return;
                }
                offer = trades.get(data.tradeIndex);
            }
            ItemStack cost = offer.getCostStack();

            // 检查玩家是否有足够支付物品
            if (!hasItems(player, cost)) {
                player.displayClientMessage(Component.literal("§c物品不足"), true);
                return;
            }

            // 扣除
            deductItems(player, cost);

            // 给予结果
            ItemStack result = offer.getResultStack();
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }

            player.displayClientMessage(
                    Component.literal("§a交易成功"), true);

            // 交易经验：Lv1=16, Lv2=32, Lv3=64, Lv4=128
            int xpGain = switch (npc.getNpcLevel()) {
                case 1 -> 16;
                case 2 -> 32;
                case 3 -> 64;
                case 4 -> 128;
                default -> 16;
            };
            if (npc.getProfession() == NpcProfession.TRADER || npc.getProfession() == NpcProfession.BLACKSMITH) {
                xpGain = (int) (xpGain * 1.25);
            }
            npc.addExperience(xpGain);

            // 交易增加阵营声望（+1）
            Faction npcFaction = npc.getFaction();
            if (npcFaction != null) {
                StandingModifier.applyModification(player, npcFaction, 1, "standing.reason.trade");
            }

            // 回传新经验给客户端，驱动 GUI 刷新
            PacketDistributor.sendToPlayer(player,
                    new NpcTradeResponsePacket(npc.getId(), npc.getExperience(), npc.getNpcLevel()));
        });
    }

    private static boolean hasItems(Player player, ItemStack needed) {
        int remaining = needed.getCount();
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, needed)) {
                remaining -= stack.getCount();
                if (remaining <= 0) return true;
            }
        }
        return false;
    }

    private static void deductItems(Player player, ItemStack needed) {
        int remaining = needed.getCount();
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (ItemStack.isSameItemSameComponents(stack, needed)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }
    }
}
