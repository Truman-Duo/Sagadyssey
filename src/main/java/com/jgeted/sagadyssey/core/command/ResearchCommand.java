package com.jgeted.sagadyssey.core.command;

import com.jgeted.sagadyssey.core.research.ResearchAttachments;
import com.jgeted.sagadyssey.core.research.ResearchNode;
import com.jgeted.sagadyssey.core.research.ResearchRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * 研究点数管理命令。
 * /research show             — 查看自己的点数
 * /research tree             — 查看技能树
 * /research unlock [科技ID]  — 解锁科技
 * /research give @p 50       — 给玩家点数
 * /research spend 30         — 消费点数
 */
public class ResearchCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("research")
                        // /research show — 查看点数
                        .then(Commands.literal("show")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int points = ResearchAttachments.getPointsData(player).getPoints();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("当前研究点数：" + points),
                                            false
                                    );
                                    return points;
                                })
                        )
                        // /research tree — 查看技能树
                        .then(Commands.literal("tree")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Set<String> unlocked = ResearchAttachments.getUnlockedData(player).getUnlocked();
                                    int points = ResearchAttachments.getPointsData(player).getPoints();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("===== 技能树（点数：" + points + "）====="), false);
                                    for (ResearchNode node : ResearchRegistry.getAllNodes()) {
                                        String icon = unlocked.contains(node.getId()) ? "✅" :
                                                node.arePrerequisitesMet(unlocked) && points >= node.getCost() ? "▶" : "🔒";
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal(icon + " " + node.getName()
                                                        + "（" + node.getCost() + " 点）— " + node.getDescription()), false);
                                    }
                                    return 1;
                                })
                        )
                        // /research unlock [科技ID]
                        .then(Commands.literal("unlock")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("nodeId", StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String nodeId = StringArgumentType.getString(ctx, "nodeId");
                                            boolean ok = ResearchAttachments.tryUnlock(player, nodeId);
                                            if (ok) {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("已解锁：" + nodeId), true);
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("失败——检查前置或点数"));
                                            }
                                            return ok ? 1 : 0;
                                        })
                                )
                        )
                        // /research give [玩家] [数量]
                        .then(Commands.literal("give")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    ResearchAttachments.addPoints(target, amount);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("已给 " + target.getName().getString()
                                                                    + " 添加 " + amount + " 研究点数"),
                                                            true
                                                    );
                                                    return amount;
                                                })
                                        )
                                )
                        )
                        // /research spend [数量]
                        .then(Commands.literal("spend")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            boolean success = ResearchAttachments.getPointsData(player).spendPoints(amount);
                                            if (success) {
                                                ResearchAttachments.syncToClient(player);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("已消耗 " + amount + " 研究点数，剩余："
                                                                + ResearchAttachments.getPointsData(player).getPoints()),
                                                        false
                                                );
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("点数不足！当前："
                                                                + ResearchAttachments.getPointsData(player).getPoints()
                                                                + "，需要：" + amount)
                                                );
                                            }
                                            return success ? 1 : 0;
                                        })
                                )
                        )
        );
    }
}
