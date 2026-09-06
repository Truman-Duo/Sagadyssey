package com.jgeted.sagadyssey.npc.faction.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jgeted.sagadyssey.Sagadyssey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * 阵营 JSON 数据加载器。
 * <p>
 * 负责从类路径 data/sagadyssey/ 下加载非 Registry 的 JSON 配置
 * ——目前主要是 faction_relations.json（关系矩阵）。
 * <p>
 * 阵营定义 JSON（data/sagadyssey/faction/*.json）由 NeoForge 的
 * 数据驱动 Registry 自动加载，不需要手动解析。
 * <p>
 * 关系矩阵的加载委托给 {@link com.jgeted.sagadyssey.npc.faction.FactionRelationMatrix#loadFromJson()}。
 */
public final class FactionDataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(Sagadyssey.MOD_ID);

    private FactionDataLoader() {}

    /**
     * 初始化：加载关系矩阵等非 Registry 的 JSON 配置。
     * 在模组构造器中调用。
     */
    public static void init() {
        // 加载阵营关系矩阵
        com.jgeted.sagadyssey.npc.faction.FactionRelationMatrix.loadFromJson();
    }
}
