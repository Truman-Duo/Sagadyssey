package com.jgeted.sagadyssey.core.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.jgeted.sagadyssey.Sagadyssey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 加载 data/sagadyssey/structure/*.json 配置。
 * 通过 KNOWN_IDS 列表 + ClassLoader.getResourceAsStream 从类路径加载，
 * 不依赖工作目录，开发环境和生产环境均可用。
 */
public final class StructureConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Sagadyssey.MOD_ID);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String CONFIG_DIR = "data/sagadyssey/structure";

    private StructureConfigLoader() {}

    /**
     * 已知结构 ID 列表（新增建筑后需在此追加）。
     */
    private static final List<String> KNOWN_IDS = List.of(
        "camp_bandit_small",
        "house_cottage_small",
        "house_cottage_medium",
        "house_cottage_large",
        "watchtower_stone_large",
        "chapel_misty_moon",
        "forge",
        "stable",
        "vilg_1",
        "vilg_2",
        "wallgate"
    );

    /**
     * 加载全部结构配置。按 KNOWN_IDS 列表逐个从类路径加载 JSON 文件。
     */
    public static List<StructureConfig> loadAll() {
        List<StructureConfig> configs = new ArrayList<>();
        ClassLoader cl = StructureConfigLoader.class.getClassLoader();

        for (String id : KNOWN_IDS) {
            String resourcePath = CONFIG_DIR + "/" + id + ".json";
            try (InputStream in = cl.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    LOGGER.warn("找不到结构配置资源: {}", resourcePath);
                    continue;
                }
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    StructureConfig config = GSON.fromJson(reader, StructureConfig.class);
                    if (config != null && config.getId() != null) {
                        configs.add(config);
                    }
                }
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("跳过格式错误的配置文件 {}: {}", id, e.getMessage());
            }
        }

        LOGGER.info("从类路径加载了 {} 个结构配置", configs.size());
        return configs;
    }
}
