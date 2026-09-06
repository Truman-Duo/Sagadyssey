package com.jgeted.sagadyssey.core.command;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import com.jgeted.sagadyssey.npc.faction.Faction;
import com.jgeted.sagadyssey.npc.faction.FactionRegistry;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import com.jgeted.sagadyssey.npc.registry.NpcEntityTypes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * NPC 生成命令。
 * /saga npc spawn [profession] [faction] — 在玩家位置生成一个 NPC。
 * 阵营参数支持新 Faction ID（如 "kingdom"）和旧 NpcFaction 枚举值的自动映射。
 */
public class NpcSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("saga")
                        .then(Commands.literal("npc")
                                .then(Commands.literal("spawn")
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> spawnNpc(ctx.getSource(), null, null))
                                        .then(Commands.argument("profession", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (NpcProfession p : NpcProfession.values()) {
                                                        builder.suggest(p.name());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    String profName = StringArgumentType.getString(ctx, "profession");
                                                    return spawnNpc(ctx.getSource(), profName, null);
                                                })
                                                .then(Commands.argument("faction", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            // 建议新 Faction ID
                                                            for (Faction f : FactionRegistry.getAllFactions()) {
                                                                // 简化名称（去掉 "sagadyssey:" 前缀）
                                                                String shortId = f.id();
                                                                if (shortId.startsWith("sagadyssey:")) {
                                                                    shortId = shortId.substring("sagadyssey:".length());
                                                                }
                                                                builder.suggest(shortId);
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            String profName = StringArgumentType.getString(ctx, "profession");
                                                            String factionName = StringArgumentType.getString(ctx, "faction");
                                                            return spawnNpc(ctx.getSource(), profName, factionName);
                                                        })
                                                )
                                        )
                                )
                        )
        );
    }

    private static int spawnNpc(CommandSourceStack source, String professionName,
                                String factionName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        final NpcProfession profession;
        if (professionName != null) {
            try {
                profession = NpcProfession.valueOf(professionName.toUpperCase());
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal("§c未知职业：" + professionName
                        + "。可用：WARRIOR, ARCHER, WORKER 等"));
                return 0;
            }
        } else {
            profession = NpcProfession.NONE;
        }

        final Faction faction;
        if (factionName != null) {
            // 先尝试新 Faction ID（如 "kingdom" → "sagadyssey:kingdom"）
            String fullId = factionName.contains(":") ? factionName
                    : "sagadyssey:" + factionName;
            faction = FactionRegistry.get(fullId);
            if (faction == null) {
                source.sendFailure(Component.literal("§c未知阵营：" + factionName
                        + "。可用 Faction ID 见 /sagadyssey faction list"));
                return 0;
            }
        } else {
            faction = FactionRegistry.get("sagadyssey:wilderness");
        }

        NpcBase npc = new NpcBase(NpcEntityTypes.NPC_BASE.get(), player.level());
        npc.setPos(player.getX(), player.getY(), player.getZ());
        npc.setProfession(profession);
        npc.setFaction(faction);
        player.level().addFreshEntity(npc);

        String factionDisplay = faction != null ? faction.displayName() : "wilderness";
        source.sendSuccess(
                () -> Component.literal("§a已在当前位置生成 NPC（职业：" + profession.getDisplayName()
                        + "，阵营：" + factionDisplay + "）"),
                true
        );
        return 1;
    }
}
