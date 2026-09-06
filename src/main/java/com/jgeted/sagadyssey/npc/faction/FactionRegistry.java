package com.jgeted.sagadyssey.npc.faction;

import com.jgeted.sagadyssey.Sagadyssey;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.Collection;
import java.util.Optional;

/**
 * 阵营注册中心。
 * <p>
 * 使用 NeoForge 1.21 的 DataPackRegistry 方案——
 * 所有阵营（内置 7 个 + 自定义）统一从数据包 JSON 加载。
 * <p>
 * 阵营始终通过 {@link RegistryAccess} 动态获取：
 * <ul>
 *   <li>服务端命令：{@code level.registryAccess()}</li>
 *   <li>客户端 GUI：{@code Minecraft.getInstance().level.registryAccess()}</li>
 *   <li>内部便利：{@link FactionRegistry#get(String)} 使用全局缓存</li>
 * </ul>
 * <p>
 * 全局缓存在 {@link Level} 存在时填充，避免反复查 registryAccess。
 */
public final class FactionRegistry {

    /** Registry 的 ResourceKey */
    public static final ResourceKey<Registry<Faction>> KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "faction")
            );

    /** player 阵营的硬编码兜底（Registry 未加载或 JSON 丢失时使用） */
    private static final Faction PLAYER_FALLBACK = new Faction(
            "sagadyssey:player",
            "faction.sagadyssey.player",
            65280,
            100,
            false,   // canBeHostile
            false,   // canRecruit
            ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "textures/gui/faction/player.png"),
            Optional.empty(),
            Optional.of(new int[]{200, 200}),
            1.0f,
            false,   // decayEnabled
            100,     // decayTarget
            0        // decayRatePerDay
    );

    /** player 阵营 JSON 丢失时只警告一次 */
    private static boolean playerFactionMissingWarned = false;

    /** 全局缓存（任何 Level 的 registryAccess 均可填充，首次命中后生效） */
    private static volatile Registry<Faction> cachedRegistry;

    private FactionRegistry() {}

    /** 注册 DataPackRegistry（mod bus） */
    @SubscribeEvent
    public static void registerDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(KEY, Faction.CODEC, Faction.CODEC);
        Sagadyssey.LOGGER.info("阵营 DataPackRegistry 已注册: sagadyssey:faction");
    }

    /**
     * 从 RegistryAccess 获取阵营 Registry。
     * 同时更新全局缓存（如有数据）。
     */
    public static Registry<Faction> fromRegistryAccess(RegistryAccess access) {
        Registry<Faction> reg = access.registryOrThrow(KEY);
        if (reg.size() > 0) {
            cachedRegistry = reg;
        }
        return reg;
    }

    /** 从 Level 获取阵营 Registry */
    public static Registry<Faction> fromLevel(Level level) {
        return fromRegistryAccess(level.registryAccess());
    }

    /**
     * 确保缓存已填充——命令等入口调用此方法，
     * 传入 {@code source.registryAccess()} 或类似源。
     * 仅在缓存未命中时有开销。
     */
    public static void ensureCache(RegistryAccess access) {
        if (cachedRegistry == null || cachedRegistry.size() == 0) {
            fromRegistryAccess(access);
        }
    }

    /**
     * 从任意有效的 RegistryAccess 源获取。
     * 优先用缓存，否则从 mc.level 拿。
     */
    private static Registry<Faction> getActiveRegistry() {
        if (cachedRegistry != null && cachedRegistry.size() > 0) {
            return cachedRegistry;
        }
        // 客户端：从当前 level 获取
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                Registry<Faction> reg = fromRegistryAccess(mc.level.registryAccess());
                if (reg.size() > 0) return reg;
            }
        } catch (Exception ignored) {}
        return cachedRegistry;
    }

    // === 便捷查询 ===

    public static Optional<Faction> getOptional(ResourceLocation id) {
        Registry<Faction> reg = getActiveRegistry();
        if (reg == null) return Optional.empty();
        return reg.getOptional(id);
    }

    public static Faction get(ResourceLocation id) {
        Registry<Faction> reg = getActiveRegistry();
        if (reg == null) return null;
        return reg.get(id);
    }

    /**
     * 按字符串 ID 获取阵营。
     * 不带命名空间的 ID 自动补全为 {@code sagadyssey:} 命名空间。
     */
    public static Faction get(String id) {
        Registry<Faction> reg = getActiveRegistry();
        if (reg == null) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        // 无命名空间时默认补全为 sagadyssey:
        if (rl.getNamespace().equals("minecraft") && !id.contains(":")) {
            rl = ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, rl.getPath());
        }
        return reg.get(rl);
    }

    public static Collection<Faction> getAllFactions() {
        Registry<Faction> reg = getActiveRegistry();
        if (reg == null) return java.util.List.of();
        return reg.stream().toList();
    }

    public static Registry<Faction> getRegistry() {
        return getActiveRegistry();
    }

    /** 获取 Registry 大小（用于日志） */
    public static int size() {
        Registry<Faction> reg = getActiveRegistry();
        return reg == null ? 0 : reg.size();
    }

    /**
     * 获取 player 阵营（带硬编码兜底）。
     * <p>
     * 正常情况下从 DataPackRegistry 读取 player.json；
     * 如果 Registry 未加载或 JSON 丢失，返回硬编码的 PLAYER_FALLBACK，
     * 确保已招募 NPC 不会因整合包误删 player.json 而丢失阵营归属。
     */
    public static Faction getPlayerFaction() {
        // Registry 还没加载（早期初始化阶段），直接用兜底
        if (cachedRegistry == null) {
            return PLAYER_FALLBACK;
        }
        Faction fromRegistry = cachedRegistry.get(
                ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "player"));
        if (fromRegistry != null) {
            return fromRegistry;
        }
        // Registry 有数据但查不到 player 阵营 → JSON 可能被误删，告警一次
        if (!playerFactionMissingWarned) {
            playerFactionMissingWarned = true;
            Sagadyssey.LOGGER.warn("数据包中缺少 player.json 阵营定义，使用硬编码兜底");
        }
        return PLAYER_FALLBACK;
    }
}
