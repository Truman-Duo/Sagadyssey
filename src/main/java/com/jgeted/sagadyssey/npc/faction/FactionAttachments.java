package com.jgeted.sagadyssey.npc.faction;

import com.jgeted.sagadyssey.npc.faction.network.FactionDataSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 阵营声望 Attachment 注册中心。
 * <p>
 * 参考 {@code ResearchAttachments.java} 的模式——
 * 使用 DeferredRegister 注册 AttachmentType，提供静态便捷方法。
 */
public class FactionAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "sagadyssey");

    /** 玩家阵营声望数据，持久化存储，死亡后保留 */
    public static final Supplier<AttachmentType<FactionStandings>> FACTION_STANDINGS =
            ATTACHMENT_TYPES.register("faction_standings", () ->
                    AttachmentType.builder(FactionStandings::new)
                            .serialize(FactionStandings.CODEC)
                            .copyOnDeath()
                            .build()
            );

    /** 便捷方法：获取玩家的声望数据 */
    public static FactionStandings getStandings(Entity entity) {
        return entity.getData(FACTION_STANDINGS.get());
    }

    /** 便捷方法：同步全量声望数据到客户端 */
    public static void syncToClient(ServerPlayer player) {
        FactionStandings standings = getStandings(player);
        PacketDistributor.sendToPlayer(player,
                FactionDataSyncPayload.from(standings));
    }
}
