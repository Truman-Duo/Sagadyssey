package com.jgeted.sagadyssey.core.command;

import com.jgeted.sagadyssey.core.network.ResearchUnlockPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 测试命令：客户端发送网络数据包到服务端。
 * 用法：/saga test "科技名称"
 */
public class TestNetworkCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("saga")
                        .then(Commands.literal("test")
                                .then(Commands.argument("researchId", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String researchId = StringArgumentType.getString(ctx, "researchId");
                                            // 客户端→服务端，发送测试数据包
                                            PacketDistributor.sendToServer(new ResearchUnlockPacket(researchId));
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("已发送测试数据包：" + researchId),
                                                    false
                                            );
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
