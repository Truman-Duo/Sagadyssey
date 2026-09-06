package com.jgeted.sagadyssey.npc.faction.gui;

import com.jgeted.sagadyssey.npc.faction.*;
import com.jgeted.sagadyssey.npc.faction.network.ClientFactionCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 关系网络图组件。
 * <p>
 * 绘制阵营之间的关系网络，节点用阵营纹章（或颜色方块）表示，
 * 连线用 Bresenham 算法逐像素绘制。
 * <p>
 * 连线颜色：绿色(ALLY/FRIENDLY)、灰色(NEUTRAL)、橘色(DISTRUSTFUL)、红色(ENEMY)。
 * 第一阶段全部 1px 细线，暂不做粗细变化。
 */
public final class RelationNetworkWidget {

    private static final int NODE_RADIUS = 8;

    private static final int COLOR_ALLY = 0xFF_55FF55;
    private static final int COLOR_FRIENDLY = 0xFF_88CC88;
    private static final int COLOR_NEUTRAL = 0xFF_888888;
    private static final int COLOR_DISTRUSTFUL = 0xFF_FF8844;
    private static final int COLOR_ENEMY = 0xFF_FF4444;

    private RelationNetworkWidget() {}

    /**
     * 焦点模式渲染：以焦点阵营为中心，绘制与其他阵营的连线。
     */
    public static void renderFocus(GuiGraphics graphics, Font font,
                                    int x, int y, int w, int h, Faction focus) {
        FactionRelationMatrix matrix = FactionRelationMatrix.getInstance();
        List<Faction> allFactions = List.copyOf(FactionRegistry.getAllFactions());

        // 焦点节点位于中心偏左
        int focusX = x + 40;
        int focusY = y + h / 2;

        // 其他节点在右侧环形排列
        int otherCount = allFactions.size() - 1;
        int otherIndex = 0;

        for (Faction other : allFactions) {
            if (other.id().equals(focus.id())) continue;

            int otherX = x + w - 30;
            int otherY = y + (h / (otherCount + 1)) * (otherIndex + 1);
            otherIndex++;

            // 连线
            InterFactionRelation rel;
            if ("sagadyssey:player".equals(focus.id())) {
                // 玩家阵营：关系 = 玩家对此阵营的声望映射
                rel = standingToRelation(ClientFactionCache.getLevel(other.id()));
            } else if ("sagadyssey:player".equals(other.id())) {
                // 其他阵营 ← 玩家阵营（防御性处理）
                rel = standingToRelation(ClientFactionCache.getLevel(focus.id()));
            } else {
                rel = matrix.getRelation(focus, other);
            }
            int lineColor = getLineColor(rel);
            drawLine(graphics, focusX, focusY, otherX, otherY, lineColor);

            // 远端节点
            drawNode(graphics, otherX, otherY, other, rel);

            // 传递率标注
            String rateLabel = String.format("%+.0f%%", rel.getTransferRate() * 100);
            int labelX = (focusX + otherX) / 2;
            int labelY = (focusY + otherY) / 2 - 4;
            graphics.drawString(font, Component.literal(rateLabel).withStyle(s -> s.withColor(0xAA_AAAAAA)),
                    labelX, labelY, 0xAA_AAAAAA);
        }

        // 焦点节点
        drawNode(graphics, focusX, focusY, focus, InterFactionRelation.ALLY);

        // 底部信息：当前声望值 + 涟漪影响
        int standing = ClientFactionCache.getStanding(focus.id());
        StandingLevel level = StandingLevel.fromValue(standing);
        String baseInfo = "当前声望：" + getLevelText(level) + " (" + standing + "/100)";
        String info = "sagadyssey:player".equals(focus.id())
                ? baseInfo + "  |  玩家阵营：连线颜色 = 你对各阵营的声望"
                : baseInfo;
        graphics.drawString(font, Component.literal(info), x + 4, y + h - 12, 0xFF_D4A017);
    }

    /** 绘制阵营节点 */
    private static void drawNode(GuiGraphics graphics, int cx, int cy, Faction faction,
                                  InterFactionRelation relation) {
        // 节点外圈（关系颜色）
        int outerColor = getLineColor(relation);
        graphics.fill(cx - NODE_RADIUS - 1, cy - NODE_RADIUS - 1,
                cx + NODE_RADIUS + 1, cy + NODE_RADIUS + 1, outerColor);

        // 节点内部（阵营颜色）
        graphics.fill(cx - NODE_RADIUS, cy - NODE_RADIUS,
                cx + NODE_RADIUS, cy + NODE_RADIUS, faction.color() | 0xFF000000);
    }

    /** Bresenham 直线算法绘制 1px 斜线 */
    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private static int getLineColor(InterFactionRelation rel) {
        return switch (rel) {
            case ALLY -> COLOR_ALLY;
            case FRIENDLY -> COLOR_FRIENDLY;
            case NEUTRAL -> COLOR_NEUTRAL;
            case DISTRUSTFUL -> COLOR_DISTRUSTFUL;
            case ENEMY -> COLOR_ENEMY;
        };
    }

    private static String getLevelText(StandingLevel level) {
        return switch (level) {
            case REVERED -> "崇拜";
            case HONORED -> "尊敬";
            case NEUTRAL -> "中立";
            case COLD -> "冷淡";
            case HATED -> "仇恨";
        };
    }

    /** 声望等级 → 关系连线类型 */
    private static InterFactionRelation standingToRelation(StandingLevel level) {
        return switch (level) {
            case REVERED -> InterFactionRelation.ALLY;
            case HONORED -> InterFactionRelation.FRIENDLY;
            case NEUTRAL -> InterFactionRelation.NEUTRAL;
            case COLD -> InterFactionRelation.DISTRUSTFUL;
            case HATED -> InterFactionRelation.ENEMY;
        };
    }
}
