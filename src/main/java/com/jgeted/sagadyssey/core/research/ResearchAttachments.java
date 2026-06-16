package com.jgeted.sagadyssey.core.research;

import com.jgeted.sagadyssey.core.network.ResearchSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashSet;
import java.util.function.Supplier;

/**
 * Sagadyssey 玩家数据 Attachment 注册中心。
 */
public class ResearchAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "sagadyssey");

    /** 玩家研究点数，持久化存储，死亡后保留 */
    public static final Supplier<AttachmentType<ResearchPointsData>> RESEARCH_POINTS =
            ATTACHMENT_TYPES.register("research_points", () ->
                    AttachmentType.builder(() -> new ResearchPointsData())
                            .serialize(ResearchPointsData.CODEC)
                            .copyOnDeath()
                            .build()
            );

    /** 玩家已解锁科技，持久化存储，死亡后保留 */
    public static final Supplier<AttachmentType<PlayerResearchData>> UNLOCKED_RESEARCH =
            ATTACHMENT_TYPES.register("unlocked_research", () ->
                    AttachmentType.builder(() -> new PlayerResearchData())
                            .serialize(PlayerResearchData.CODEC)
                            .copyOnDeath()
                            .build()
            );

    /** 便捷方法：获取玩家的研究点数数据 */
    public static ResearchPointsData getPointsData(Entity entity) {
        return entity.getData(RESEARCH_POINTS.get());
    }

    /** 便捷方法：给玩家增加研究点数，自动同步到客户端 */
    public static void addPoints(Entity entity, int amount) {
        getPointsData(entity).addPoints(amount);
        if (entity instanceof ServerPlayer player) {
            syncToClient(player);
        }
    }

    /** 便捷方法：获取玩家的已解锁科技数据 */
    public static PlayerResearchData getUnlockedData(Entity entity) {
        return entity.getData(UNLOCKED_RESEARCH.get());
    }

    /** 尝试解锁科技。检查前置、点数，成功则扣除点数并解锁，自动同步到客户端。 */
    public static boolean tryUnlock(Entity entity, String nodeId) {
        ResearchNode node = ResearchRegistry.getNode(nodeId);
        if (node == null) return false;
        PlayerResearchData data = getUnlockedData(entity);
        if (data.isUnlocked(nodeId)) return false;
        if (!node.arePrerequisitesMet(data.getUnlocked())) return false;
        ResearchPointsData points = getPointsData(entity);
        if (!points.spendPoints(node.getCost())) return false;
        boolean result = data.unlock(nodeId);
        if (entity instanceof ServerPlayer player) {
            syncToClient(player);
        }
        return result;
    }

    /** 将研究数据同步到客户端 */
    public static void syncToClient(ServerPlayer player) {
        int pts = getPointsData(player).getPoints();
        var unlocked = new HashSet<>(getUnlockedData(player).getUnlocked());
        PacketDistributor.sendToPlayer(player, new ResearchSyncPayload(pts, unlocked));
    }
}
