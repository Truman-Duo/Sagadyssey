package com.jgeted.sagadyssey.core.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 每个玩家独立的研究点数数据。
 * 作为 Attachment 存储，自动同步到客户端。
 */
public class ResearchPointsData {
    public static final Codec<ResearchPointsData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("points").forGetter(d -> d.points)
            ).apply(instance, ResearchPointsData::new)
    );

    private int points;

    public ResearchPointsData() {
        this(0);
    }

    public ResearchPointsData(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }

    /** 增加点数 */
    public void addPoints(int amount) {
        this.points += amount;
    }

    /** 扣除点数，返回是否成功 */
    public boolean spendPoints(int amount) {
        if (points >= amount) {
            points -= amount;
            return true;
        }
        return false;
    }

    /** 是否有足够点数 */
    public boolean hasEnough(int amount) {
        return points >= amount;
    }
}
