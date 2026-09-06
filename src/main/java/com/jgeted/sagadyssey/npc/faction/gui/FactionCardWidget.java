package com.jgeted.sagadyssey.npc.faction.gui;

import com.jgeted.sagadyssey.npc.faction.Faction;
import com.jgeted.sagadyssey.npc.faction.StandingLevel;
import com.jgeted.sagadyssey.npc.faction.network.ClientFactionCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 阵营卡片组件。
 * <p>
 * 每个卡片显示：阵营纹章(32×32)、阵营名称、星标(1-5★)、等级文字标签、
 * 声望进度条(10px 高)。悬停时显示具体数值和变化原因。
 * <p>
 * 星标映射：1★=HATED(红), 2★=COLD(橙), 3★=NEUTRAL(灰), 4★=HONORED(绿), 5★=REVERED(金)
 */
public final class FactionCardWidget {

    /** 卡面颜色等级映射 */
    private static final int[] LEVEL_COLORS = {
            0xFF_FF4444, // HATED  红
            0xFF_FF8844, // COLD   橙
            0xFF_AAAAAA, // NEUTRAL 灰
            0xFF_55FF55, // HONORED 绿
            0xFF_FFD700, // REVERED 金
    };

    private FactionCardWidget() {}

    /**
     * 渲染单个阵营卡片。
     *
     * @param graphics   GuiGraphics
     * @param font       字体渲染器
     * @param x, y       卡片左上角
     * @param w, h       卡片宽高
     * @param faction    阵营数据
     * @param level      声望等级
     * @param standing   具体声望值 [-100, 100]
     */
    public static void render(GuiGraphics graphics, Font font,
                               int x, int y, int w, int h,
                               Faction faction, StandingLevel level, int standing) {
        // 卡片背景（半透明，颜色随等级）
        int bgColor = (LEVEL_COLORS[level.ordinal()] & 0x00FFFFFF) | 0x33000000;
        graphics.fill(x, y, x + w, y + h, bgColor);

        // 边框
        int borderColor = LEVEL_COLORS[level.ordinal()];
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        // 阵营纹章图标位置（预留 TODO: 纹理加载）
        // 暂用颜色方块代替
        int iconSize = 16;
        int iconX = x + 4;
        int iconY = y + 4;
        graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, faction.color() | 0xFF000000);

        // 阵营名称
        String displayName = getLocalizedName(faction);
        Component nameComp = Component.literal(displayName);
        int nameX = iconX + iconSize + 4;
        int nameY = y + 4;
        graphics.drawString(font, nameComp, nameX, nameY, 0xFFFFFFFF);

        // 星标
        String stars = switch (level) {
            case HATED -> "★☆☆☆☆";
            case COLD -> "★★☆☆☆";
            case NEUTRAL -> "★★★☆☆";
            case HONORED -> "★★★★☆";
            case REVERED -> "★★★★★";
        };
        Component starComp = Component.literal(stars).withStyle(s -> s.withColor(LEVEL_COLORS[level.ordinal()]));
        graphics.drawString(font, starComp, nameX, nameY + font.lineHeight + 1, LEVEL_COLORS[level.ordinal()]);

        // 等级文字
        String levelText = getLevelLocalizedName(level);
        int levelColor = LEVEL_COLORS[level.ordinal()];
        Component levelComp = Component.literal(levelText).withStyle(s -> s.withColor(levelColor));
        graphics.drawString(font, levelComp, x + 4, y + h - 22, levelColor);

        // 声望进度条（10px 高）
        int barX = x + 4;
        int barY = y + h - 10;
        int barW = w - 8;
        int barH = 6;
        drawProgressBar(graphics, barX, barY, barW, barH, standing, level, levelColor);

        // 具体数值（右下）
        String valueText = standing + "/100";
        int valueW = font.width(valueText);
        graphics.drawString(font, Component.literal(valueText).withStyle(s -> s.withColor(0xAA_AAAAAA)),
                x + w - valueW - 4, y + h - 22, 0xAA_AAAAAA);
    }

    /** 绘制进度条：当前值在当前等级范围内的位置 */
    private static void drawProgressBar(GuiGraphics graphics, int x, int y,
                                         int w, int h, int standing,
                                         StandingLevel level, int color) {
        // 背景
        graphics.fill(x, y, x + w, y + h, 0x44_333333);

        // 计算进度（在当前等级范围内的相对位置）
        int rangeMin = level.getMinValue();
        int rangeMax = getRangeMax(level);
        float progress = (float) (standing - rangeMin) / (rangeMax - rangeMin);
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        int filled = (int) (w * progress);

        // 填充色
        graphics.fill(x, y, x + filled, y + h, color | 0xFF000000);
    }

    private static int getRangeMax(StandingLevel level) {
        return switch (level) {
            case REVERED -> 100;
            case HONORED -> 74;
            case NEUTRAL -> 24;
            case COLD -> -25;
            case HATED -> -75;
        };
    }

    /** 获取阵营本地化名称（fallback: displayName key） */
    private static String getLocalizedName(Faction faction) {
        // 简体中文硬编码（后续步骤替换为 I18n 翻译）
        return switch (faction.id()) {
            case "sagadyssey:kingdom" -> "王国卫队";
            case "sagadyssey:merchant_guild" -> "商会联盟";
            case "sagadyssey:church" -> "圣殿教团";
            case "sagadyssey:dwarven" -> "矮人部族";
            case "sagadyssey:mystic" -> "秘法学会";
            case "sagadyssey:wilderness" -> "荒野流民";
            case "sagadyssey:bandit" -> "劫掠者";
            case "sagadyssey:player" -> ClientFactionCache.getPlayerFactionDisplayName();
            default -> faction.displayName();
        };
    }

    /** 获取声望等级的中文名称 */
    private static String getLevelLocalizedName(StandingLevel level) {
        return switch (level) {
            case REVERED -> "崇拜";
            case HONORED -> "尊敬";
            case NEUTRAL -> "中立";
            case COLD -> "冷淡";
            case HATED -> "仇恨";
        };
    }
}
