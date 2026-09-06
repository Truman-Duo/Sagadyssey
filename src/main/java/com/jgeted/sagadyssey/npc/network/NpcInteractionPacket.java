package com.jgeted.sagadyssey.npc.network;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.container.NpcEquipMenuProvider;
import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.entity.NpcCommand;
import com.jgeted.sagadyssey.npc.faction.NpcFaction;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端→服务端：NPC 交互请求。
 * action:
 *   "request_stats" — 请求 NPC 属性数据（右键 NPC 时发送）
 *   "recruit"        — 确认招募（在招募界面点击 Hire 按钮）
 *   "follow"         — 命令 NPC 跟随
 *   "stay"           — 命令 NPC 原地待命
 *   "work"           — 命令 NPC 开始工作（务农/伐木挖矿，按职业）
 */
public record NpcInteractionPacket(int npcId, String action) implements CustomPacketPayload {

    public static final Type<NpcInteractionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "npc_interact")
    );

    public static final StreamCodec<ByteBuf, NpcInteractionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.npcId);
                byte[] bytes = packet.action.getBytes();
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            },
            buf -> {
                int npcId = buf.readInt();
                int len = buf.readInt();
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                return new NpcInteractionPacket(npcId, new String(bytes));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理入口。
     */
    public static void handle(final NpcInteractionPacket data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Entity entity = player.serverLevel().getEntity(data.npcId);

            // NPC 不存在或已消失
            if (!(entity instanceof NpcBase npc)) {
                player.displayClientMessage(
                        Component.literal("§c这个 NPC 已经不存在了"), true);
                return;
            }

            switch (data.action) {
                case "request_stats" -> handleRequestStats(player, npc);
                case "recruit" -> handleRecruit(player, npc);
                case "follow" -> handleCommand(player, npc, "follow");
                case "stay" -> handleCommand(player, npc, "stay");
                case "work" -> handleCommand(player, npc, "work");
                case "open_equip" -> handleOpenEquip(player, npc);
                case "unbind_mount" -> handleUnbindMount(player, npc);
                case "request_mounts" -> handleRequestMounts(player, npc);
                case "tether_mount" -> handleTetherMount(player, npc);
                case "lead_mount" -> handleLeadMount(player, npc);
                case "mount_up" -> handleMountUp(player, npc);
                case "untether_mount" -> handleUntetherMount(player, npc);
                case "dismiss" -> handleDismiss(player, npc);
                default -> player.displayClientMessage(
                        Component.literal("§e未知操作：" + data.action), true);
            }
        });
    }

    /**
     * 处理 stats 请求：收集 NPC 属性，发回客户端。
     */
    private static void handleRequestStats(ServerPlayer player, NpcBase npc) {
        if (!npc.isAlive() || player.distanceToSqr(npc) > 64.0D) {
            player.displayClientMessage(Component.literal("§c你离 NPC 太远了"), true);
            return;
        }
        NpcStatsPayload.openFor(player, npc);
    }

    /**
     * 处理招募请求：检查费用、扣款、设主人。
     */
    private static void handleRecruit(ServerPlayer player, NpcBase npc) {
        // 距离检查：玩家离 NPC 太远不行
        if (player.distanceToSqr(npc) > 36.0D) { // 6 格以内
            player.displayClientMessage(
                    Component.literal("§c你离 NPC 太远了"), true);
            return;
        }

        // 已被别人招募
        if (npc.isOwned() && !npc.isOwnedBy(player.getUUID())) {
            player.displayClientMessage(
                    Component.literal("§c这个 NPC 已经被其他人招募了"), true);
            return;
        }

        // 已经是你的 NPC
        if (npc.isOwnedBy(player.getUUID())) {
            player.displayClientMessage(
                    Component.literal("§e这个 NPC 已经是你的了"), true);
            return;
        }

        int cost = npc.getRecruitmentCost();
        var faction = npc.getFaction();
        boolean isHostileFaction = faction != null && faction.canBeHostile();
        if (isHostileFaction) {
            cost *= 2;
        }

        // 检查玩家有没有足够绿宝石
        if (!hasEnoughEmeralds(player, cost)) {
            String factionTag = isHostileFaction ? "§c（敌对招募费用翻倍）" : "";
            player.displayClientMessage(
                    Component.literal("§c绿宝石不足！需要 " + cost + " 个绿宝石" + factionTag), true);
            return;
        }

        // 扣绿宝石
        if (!deductEmeralds(player, cost)) {
            player.displayClientMessage(
                    Component.literal("§c扣除绿宝石失败"), true);
            return;
        }

        // 设置主人（setOwner 内部已自动切阵营 + 保存原阵营）
        npc.setOwner(player.getUUID());

        // 通知玩家
        player.displayClientMessage(
                Component.literal("§a成功招募 " + npc.getNpcName() + "！花费 " + cost + " 绿宝石"), false);
    }

    /**
     * 检查玩家背包中是否有足够的绿宝石。
     */
    private static boolean hasEnoughEmeralds(ServerPlayer player, int cost) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
                if (count >= cost) return true;
            }
        }
        return false;
    }

    /**
     * 从玩家背包中扣除指定数量的绿宝石。
     * 优先从不满堆叠的格子扣。
     */
    private static boolean deductEmeralds(ServerPlayer player, int cost) {
        int remaining = cost;

        // 第一遍：从不满堆叠的格子扣
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.is(Items.EMERALD) && stack.getCount() < stack.getMaxStackSize()) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }

        // 第二遍：从满堆叠的格子扣
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.is(Items.EMERALD)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }

        return remaining == 0;
    }

    /**
     * 处理命令请求：follow / stay。
     * 验证实体存在、归属关系，然后执行（TODO: 后续接入 AI 行为）。
     */
    private static void handleCommand(ServerPlayer player, NpcBase npc, String action) {
        String npcName = npc.getNpcName();

        // 归属检查
        if (!npc.isOwnedBy(player.getUUID())) {
            player.displayClientMessage(
                    Component.literal("§c这个 NPC 不属于你"), true);
            return;
        }

        switch (action) {
            case "follow" -> {
                npc.setCommand(NpcCommand.FOLLOW);
                player.displayClientMessage(
                        Component.literal("§a已命令 " + npcName + " 跟随你"), true);
            }
            case "stay" -> {
                npc.setCommand(NpcCommand.STAY);
                player.displayClientMessage(
                        Component.literal("§a已命令 " + npcName + " 原地待命"), true);
            }
            case "work" -> {
                if (npc.getProfession() != NpcProfession.FARMER && npc.getProfession() != NpcProfession.WORKER) {
                    player.displayClientMessage(Component.literal("§e这个职业不会工作"), true);
                    return;
                }
                npc.setCommand(NpcCommand.WORK);
                player.displayClientMessage(Component.literal("§a已命令 " + npcName + " 开始工作"), true);
            }
        }
    }

    /**
     * 处理打开装备界面请求。
     */
    private static void handleOpenEquip(ServerPlayer player, NpcBase npc) {
        if (player.distanceToSqr(npc) > 64.0D) {
            player.displayClientMessage(Component.literal("§c你离 NPC 太远了"), true);
            return;
        }
        if (!npc.isOwnedBy(player.getUUID())) {
            player.displayClientMessage(Component.literal("§c这个 NPC 不属于你"), true);
            return;
        }
        player.openMenu(new NpcEquipMenuProvider(npc), buf -> buf.writeInt(npc.getId()));
    }

    /**
     * 处理解除坐骑绑定。
     */
    private static void handleUnbindMount(ServerPlayer player, NpcBase npc) {
        if (!npc.isOwnedBy(player.getUUID())) {
            player.displayClientMessage(Component.literal("§c这个 NPC 不属于你"), true);
            return;
        }
        npc.unbindMount();
        player.displayClientMessage(Component.literal("§a已解除坐骑绑定"), true);
    }

    /**
     * 处理请求周围可用坐骑列表。
     */
    private static void handleRequestMounts(ServerPlayer player, NpcBase npc) {
        if (!npc.isOwnedBy(player.getUUID())) {
            player.displayClientMessage(Component.literal("§c这个 NPC 不属于你"), true);
            return;
        }
        var mounts = new java.util.ArrayList<String>();
        for (var horse : player.level().getEntitiesOfClass(AbstractHorse.class,
                npc.getBoundingBox().inflate(10))) {
            if (horse.isTamed() && horse.getOwnerUUID() != null
                    && horse.getOwnerUUID().equals(player.getUUID())) {
                mounts.add(String.format("%d|%s|%.0f/%.0f",
                        horse.getId(), horse.getName().getString(),
                        horse.getHealth(), horse.getMaxHealth()));
            }
        }
        if (mounts.isEmpty()) {
            player.displayClientMessage(Component.literal("§e附近没有可分配的坐骑"), false);
        } else {
            player.displayClientMessage(Component.literal("§a可用坐骑 (共" + mounts.size() + "匹):"), false);
            for (String m : mounts) {
                String[] parts = m.split("\\|");
                player.displayClientMessage(Component.literal(
                        String.format("  §f- %s (%s HP)  §7/saga mount bind %d %s",
                                parts[1], parts[2], npc.getId(), parts[0])), false);
            }
        }
    }

    private static void handleTetherMount(ServerPlayer player, NpcBase npc) {
        if (!npc.isOwnedBy(player.getUUID())) return;
        if (!npc.hasMount()) {
            player.displayClientMessage(Component.literal("§e没有绑定坐骑"), true);
            return;
        }
        if (npc.tetherHorse()) {
            if (npc.isPassenger()) npc.dismountToBind();
            npc.setLeadMountMode(true);
            player.displayClientMessage(Component.literal("§a已拴在附近栅栏上"), true);
        } else {
            player.displayClientMessage(Component.literal("§c附近没有栅栏或缺少栓绳"), true);
        }
    }

    private static void handleLeadMount(ServerPlayer player, NpcBase npc) {
        if (!npc.isOwnedBy(player.getUUID())) return;
        if (!npc.hasMount()) {
            player.displayClientMessage(Component.literal("§e没有绑定坐骑"), true);
            return;
        }
        npc.untetherHorse();
        if (npc.isPassenger()) npc.dismountToBind();
        // 把马拴到 NPC 身上，体现"牵马"视觉效果
        Entity mount = npc.getMount();
        if (mount instanceof Leashable leashable && !leashable.isLeashed()) {
            leashable.setLeashedTo(npc, true);
        }
        npc.setLeadMountMode(true);
        player.displayClientMessage(Component.literal("§a已下马，切换为牵马步行"), true);
    }

    /**
     * 处理上马：从牵马/拴马状态恢复骑行。
     * 不直接 startRiding，而是设置 pendingMount 标记，
     * 让 MountGoal 驱动 NPC 走向坐骑后再上马。
     */
    private static void handleMountUp(ServerPlayer player, NpcBase npc) {
        if (!npc.isOwnedBy(player.getUUID())) return;
        if (!npc.hasMount()) {
            player.displayClientMessage(Component.literal("§e没有绑定坐骑"), true);
            return;
        }
        Entity mount = npc.getMount();
        if (mount == null || !mount.isAlive()) {
            player.displayClientMessage(Component.literal("§c坐骑已死亡或丢失"), true);
            return;
        }
        // 解拴，如果是栅栏栓绳则回收（拴栅栏时消耗了一根）
        if (mount instanceof Leashable leashable && leashable.isLeashed()) {
            if (leashable.getLeashHolder() instanceof LeashFenceKnotEntity) {
                npc.addLeadToInventory();
            }
            leashable.dropLeash(true, false);
        }
        // 如果 NPC 已经是乘客，先下马（防止重复）
        if (npc.isPassenger()) {
            npc.stopRiding();
        }
        npc.setLeadMountMode(false);
        // 设置标记，让 MountGoal 接管导航和上马
        npc.setPendingMount();
        player.displayClientMessage(Component.literal("§aNPC 正走向坐骑…"), true);
    }

    /**
     * 处理解除拴马：把马从栅栏上解下来，回收栓绳。
     */
    private static void handleUntetherMount(ServerPlayer player, NpcBase npc) {
        if (!npc.isOwnedBy(player.getUUID())) return;
        if (!npc.hasMount()) {
            player.displayClientMessage(Component.literal("§e没有绑定坐骑"), true);
            return;
        }
        npc.untetherHorse();
        npc.addLeadToInventory(); // 拴栅栏时消耗了一根，这里回收
        npc.setLeadMountMode(false);
        player.displayClientMessage(Component.literal("§a已解除拴马，栓绳已放回背包"), true);
    }

    /**
     * 处理解散：将已招募 NPC 恢复原阵营、清除主人。
     */
    private static void handleDismiss(ServerPlayer player, NpcBase npc) {
        if (!npc.isOwnedBy(player.getUUID())) return;
        String oldOriginal = npc.getOriginalFaction();
        npc.dismiss();
        String backTo = oldOriginal != null ? oldOriginal : "sagadyssey:wilderness";
        player.displayClientMessage(
                Component.literal("§a已解散 NPC，它回到了 " + backTo + " 阵营"), true);
    }
}
