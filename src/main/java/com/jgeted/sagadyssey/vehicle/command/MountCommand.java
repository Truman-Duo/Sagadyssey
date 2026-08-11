package com.jgeted.sagadyssey.vehicle.command;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * 坐骑管理命令。
 * <pre>
 * /saga mount bind &lt;npcId&gt; &lt;horseId&gt;   — 绑定坐骑到 NPC
 * /saga mount unbind &lt;npcId&gt;            — 解除 NPC 坐骑绑定
 * /saga mount list &lt;npcId&gt;              — 列出 NPC 周围的可骑乘马
 * </pre>
 */
public class MountCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("saga")
                        .then(Commands.literal("mount")
                                .requires(src -> src.hasPermission(2))
                                // bind <npcId> <horseId>
                                .then(Commands.literal("bind")
                                        .then(Commands.argument("npcId", IntegerArgumentType.integer())
                                                .then(Commands.argument("horseId", IntegerArgumentType.integer())
                                                        .executes(ctx -> bindMount(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "npcId"),
                                                                IntegerArgumentType.getInteger(ctx, "horseId"))
                                                        )
                                                )
                                        )
                                )
                                // unbind <npcId>
                                .then(Commands.literal("unbind")
                                        .then(Commands.argument("npcId", IntegerArgumentType.integer())
                                                .executes(ctx -> unbindMount(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "npcId"))
                                                )
                                        )
                                )
                                // list <npcId>
                                .then(Commands.literal("list")
                                        .then(Commands.argument("npcId", IntegerArgumentType.integer())
                                                .executes(ctx -> listMounts(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "npcId"))
                                                )
                                        )
                                )
                        )
        );
    }

    private static int bindMount(CommandSourceStack source, int npcId, int horseId) {
        ServerLevel level = source.getLevel();
        Entity npcEntity = level.getEntity(npcId);
        Entity horseEntity = level.getEntity(horseId);

        if (!(npcEntity instanceof NpcBase npc)) {
            source.sendFailure(Component.literal("§cNPC 未找到（ID: " + npcId + "）"));
            return 0;
        }
        if (!(horseEntity instanceof AbstractHorse horse)) {
            source.sendFailure(Component.literal("§c坐骑未找到或不是马（ID: " + horseId + "）"));
            return 0;
        }
        if (!horse.isSaddled()) {
            source.sendFailure(Component.literal("§c该马未装备马鞍，无法骑乘"));
            return 0;
        }

        npc.bindMount(horse);
        source.sendSuccess(() -> Component.literal(
                "§a" + npc.getNpcName() + " 已绑定坐骑（" + horse.getName().getString() + "）"), true);
        return 1;
    }

    private static int unbindMount(CommandSourceStack source, int npcId) {
        ServerLevel level = source.getLevel();
        Entity npcEntity = level.getEntity(npcId);

        if (!(npcEntity instanceof NpcBase npc)) {
            source.sendFailure(Component.literal("§cNPC 未找到（ID: " + npcId + "）"));
            return 0;
        }
        if (!npc.hasMount()) {
            source.sendFailure(Component.literal("§c该 NPC 没有绑定坐骑"));
            return 0;
        }

        npc.unbindMount();
        source.sendSuccess(() -> Component.literal(
                "§a已解除 " + npc.getNpcName() + " 的坐骑绑定"), true);
        return 1;
    }

    private static int listMounts(CommandSourceStack source, int npcId) {
        ServerLevel level = source.getLevel();
        Entity npcEntity = level.getEntity(npcId);

        if (!(npcEntity instanceof NpcBase npc)) {
            source.sendFailure(Component.literal("§cNPC 未找到（ID: " + npcId + "）"));
            return 0;
        }

        if (npc.hasMount()) {
            Entity mount = npc.getMount();
            String status = mount != null && mount.isAlive() ? "§a已绑定" : "§c已死亡/未加载";
            source.sendSuccess(() -> Component.literal(
                    "§6" + npc.getNpcName() + " 的坐骑：" + status), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "§6" + npc.getNpcName() + " 当前没有绑定坐骑"), false);
        }

        // 列出周围可骑乘的已驯服马
        source.sendSuccess(() -> Component.literal("§7===== 周围可骑乘的马 ====="), false);
        int count = 0;
        for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class,
                npc.getBoundingBox().inflate(20))) {
            if (horse.isTamed() && horse.isSaddled() && horse.isAlive()) {
                String ownerName = horse.getOwnerUUID() != null
                        ? level.getPlayerByUUID(horse.getOwnerUUID()) != null
                                ? level.getPlayerByUUID(horse.getOwnerUUID()).getName().getString()
                                : "离线玩家"
                        : "无主人";
                source.sendSuccess(() -> Component.literal(
                        "  §f#" + horse.getId() + " §7" + horse.getName().getString()
                                + "  主人：" + ownerName
                                + "  血量：" + (int) horse.getHealth() + "/" + (int) horse.getMaxHealth()), false);
                count++;
            }
        }
        if (count == 0) {
            source.sendSuccess(() -> Component.literal("  §7（无）"), false);
        }
        return 1;
    }
}
