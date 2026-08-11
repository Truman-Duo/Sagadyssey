package com.jgeted.sagadyssey.npc.faction.command;

import com.jgeted.sagadyssey.npc.faction.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * 阵营管理命令。
 * <pre>
 * /sagadyssey faction list                      — 列出所有阵营及关系
 * /sagadyssey faction get &lt;player&gt; &lt;faction&gt;   — 显示声望值和等级
 * /sagadyssey faction set &lt;player&gt; &lt;faction&gt; &lt;value&gt; — 设置声望值
 * /sagadyssey faction reset &lt;player&gt;            — 重置所有声望到 defaultStanding
 * /sagadyssey faction rename &lt;name&gt;             — 自定义玩家阵营名称（1-16 字符）
 * </pre>
 */
public class FactionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sagadyssey")
                        .then(Commands.literal("faction")
                                .requires(src -> src.hasPermission(2))
                                // list
                                .then(Commands.literal("list")
                                        .executes(ctx -> listFactions(ctx.getSource()))
                                )
                                // get <player> <faction>
                                .then(Commands.literal("get")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("faction", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            for (var f : FactionRegistry.getAllFactions()) {
                                                                builder.suggest(f.id());
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> getStanding(
                                                                ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "faction"))
                                                        )
                                                )
                                        )
                                )
                                // set <player> <faction> <value>
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("faction", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            for (var f : FactionRegistry.getAllFactions()) {
                                                                builder.suggest(f.id());
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .then(Commands.argument("value",
                                                                IntegerArgumentType.integer(-100, 100))
                                                                .executes(ctx -> setStanding(
                                                                        ctx.getSource(),
                                                                        EntityArgument.getPlayer(ctx, "player"),
                                                                        StringArgumentType.getString(ctx, "faction"),
                                                                        IntegerArgumentType.getInteger(ctx, "value"))
                                                                )
                                                        )
                                                )
                                        )
                                )
                                // reset <player>
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> resetStandings(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"))
                                                )
                                        )
                                )
                                // rename <name>
                                .then(Commands.literal("rename")
                                        .executes(ctx -> renameFaction(
                                                ctx.getSource(), null))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> renameFaction(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"))
                                                )
                                        )
                                )
                        )
        );
    }

    private static int listFactions(CommandSourceStack source) {
        FactionRegistry.ensureCache(source.registryAccess());
        Collection<Faction> factions = FactionRegistry.getAllFactions();
        if (factions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§e暂无已注册阵营"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6===== 阵营列表（" + factions.size() + " 个）====="), false);
        var matrix = FactionRelationMatrix.getInstance();

        for (Faction f : factions) {
            StringBuilder sb = new StringBuilder();
            sb.append("§f  ").append(f.id())
                    .append("  §7颜色:§f #").append(String.format("%06X", f.color() & 0xFFFFFF))
                    .append("  §7初始声望:§f ").append(f.defaultStanding())
                    .append("  §7可敌对:§f ").append(f.canBeHostile() ? "是" : "否")
                    .append("  §7可招募:§f ").append(f.canRecruit() ? "是" : "否");

            source.sendSuccess(() -> Component.literal(sb.toString()), false);

            // 显示该阵营的关系
            for (Faction other : factions) {
                if (f.id().equals(other.id())) continue;
                var rel = matrix.getRelation(f, other);
                if (rel != InterFactionRelation.NEUTRAL) {
                    source.sendSuccess(() -> Component.literal(
                            "§7    → " + other.id() + ": " + rel.name()), false);
                }
            }
        }
        return 1;
    }

    private static int getStanding(CommandSourceStack source, ServerPlayer target, String factionId) {
        FactionRegistry.ensureCache(source.registryAccess());
        Faction faction = FactionRegistry.get(factionId);
        if (faction == null) {
            source.sendFailure(Component.literal("§c未知阵营：" + factionId));
            return 0;
        }

        var standings = FactionAttachments.getStandings(target);
        int value = standings.getValue(faction);
        StandingLevel level = standings.getLevel(faction);

        source.sendSuccess(() -> Component.literal(
                "§a" + target.getName().getString() + " §f对 §e" + faction.id()
                        + " §f的声望：§b" + value + " §f（" + level.name() + "）"), false);
        return 1;
    }

    private static int setStanding(CommandSourceStack source, ServerPlayer target,
                                    String factionId, int value) {
        FactionRegistry.ensureCache(source.registryAccess());
        Faction faction = FactionRegistry.get(factionId);
        if (faction == null) {
            source.sendFailure(Component.literal("§c未知阵营：" + factionId));
            return 0;
        }

        StandingModifier.applyModification(target, faction,
                value - FactionAttachments.getStandings(target).getValue(faction),
                "standing.reason.command");

        source.sendSuccess(() -> Component.literal(
                "§a已将 " + target.getName().getString() + " 对 " + faction.id()
                        + " 的声望设置为 " + value), true);
        return 1;
    }

    private static int resetStandings(CommandSourceStack source, ServerPlayer target) {
        FactionRegistry.ensureCache(source.registryAccess());
        var standings = FactionAttachments.getStandings(target);
        for (Faction f : FactionRegistry.getAllFactions()) {
            standings.setValue(f, f.defaultStanding());
        }
        FactionAttachments.syncToClient(target);

        source.sendSuccess(() -> Component.literal(
                "§a已重置 " + target.getName().getString() + " 的所有阵营声望"), true);
        return 1;
    }

    private static int renameFaction(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c此命令只能由玩家执行"));
            return 0;
        }

        if (name == null || name.isBlank()) {
            var standings = FactionAttachments.getStandings(player);
            standings.setPlayerFactionName(null);
            FactionAttachments.syncToClient(player);
            source.sendSuccess(() -> Component.literal("§a玩家阵营名称已重置为默认"), true);
            return 1;
        }

        // 验证名称
        String cleaned = name.replaceAll("§[0-9a-fk-or]", "").trim();
        if (cleaned.isEmpty()) {
            source.sendFailure(Component.literal("§c阵营名称不能为空"));
            return 0;
        }
        if (cleaned.length() > 16) {
            source.sendFailure(Component.literal("§c阵营名称不能超过 16 个字符（当前 " + cleaned.length() + " 字符）"));
            return 0;
        }

        var standings = FactionAttachments.getStandings(player);
        standings.setPlayerFactionName(cleaned);
        FactionAttachments.syncToClient(player);

        source.sendSuccess(() -> Component.literal(
                "§a玩家阵营名称已设为：§e" + cleaned), true);
        return 1;
    }
}
