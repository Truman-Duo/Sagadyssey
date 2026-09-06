package com.jgeted.sagadyssey.npc.gui;

import com.jgeted.sagadyssey.npc.faction.Faction;
import com.jgeted.sagadyssey.npc.faction.FactionRegistry;
import com.jgeted.sagadyssey.npc.network.NpcInteractionPacket;
import com.jgeted.sagadyssey.npc.network.NpcStatsPayload;
import com.jgeted.sagadyssey.npc.trade.NpcTradeOffer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * NPC 招募界面。
 * 显示 NPC 属性面板、背包预览和招募按钮。
 * 玩家点击 Hire 后发送 recruit 数据包到服务端。
 */
public class NpcRecruitScreen extends Screen {

    private final int npcId;
    private final String npcName;
    private final String professionName;

    // 属性数据
    private final float currentHp;
    private final float maxHp;
    private final float attackDamage;
    private final float speed;
    private final float armor;
    private final int npcLevel;
    private final int experience;
    private final int kills;
    private final int moral;
    private final int recruitmentCost;
    private final String factionName;

    private final List<NpcTradeOffer> trades;

    // 布局常量
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 170;

    // 动态计算的坐标
    private int panelLeft;
    private int panelTop;

    public NpcRecruitScreen(NpcStatsPayload data) {
        super(Component.literal(data.npcName()));
        this.npcId = data.npcId();
        this.npcName = data.npcName();
        this.professionName = data.professionName();
        this.currentHp = data.currentHp();
        this.maxHp = data.maxHp();
        this.attackDamage = data.attackDamage();
        this.speed = data.speed();
        this.armor = data.armor();
        this.npcLevel = data.npcLevel();
        this.experience = data.experience();
        this.kills = data.kills();
        this.moral = data.moral();
        this.recruitmentCost = data.recruitmentCost();
        this.factionName = data.factionName();
        this.trades = data.buildTrades();
    }

    @Override
    protected void init() {
        super.init();
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;

        Faction faction = FactionRegistry.get(factionName);
        boolean isHostile = faction != null && faction.canBeHostile();

        // 友好/中立 NPC 可以交易
        if (!isHostile) {
            int tradeBtnW = 76;
            int tradeBtnX = panelLeft + (PANEL_WIDTH - tradeBtnW) / 2;
            int tradeBtnY = panelTop + 106;
            addRenderableWidget(Button.builder(
                    Component.literal("交易"),
                    btn -> this.minecraft.setScreen(new NpcTradeScreen(npcId, npcName, professionName, npcLevel, experience, trades)))
                    .bounds(tradeBtnX, tradeBtnY, tradeBtnW, 20)
                    .build());
        }

        // 招募按钮
        int recruitBtnW = 120;
        int recruitX = panelLeft + (PANEL_WIDTH - recruitBtnW) / 2;
        int recruitY = panelTop + 132;
        addRenderableWidget(Button.builder(
                Component.literal("招募（花费 " + recruitmentCost + " 绿宝石）"),
                btn -> {
                    PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "recruit"));
                    this.onClose();
                })
                .bounds(recruitX, recruitY, recruitBtnW, 20)
                .build());

        // × 关闭按钮
        addRenderableWidget(Button.builder(
                Component.literal("✕").withStyle(s -> s.withColor(0xFF_FF5555)),
                btn -> this.onClose())
                .bounds(panelLeft + PANEL_WIDTH - 20, panelTop + 4, 16, 16)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 自定义背景（比原版浅，不完全遮住游戏画面）
        graphics.fill(0, 0, this.width, this.height, 0x88_000000);

        // === 主面板背景 ===
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xCC_1A1A2E);
        // 金色边框
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop, 0xFF_AA5500);
        graphics.fill(panelLeft - 1, panelTop + PANEL_HEIGHT, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFF_AA5500);
        graphics.fill(panelLeft - 1, panelTop, panelLeft, panelTop + PANEL_HEIGHT, 0xFF_AA5500);
        graphics.fill(panelLeft + PANEL_WIDTH, panelTop, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT, 0xFF_AA5500);

        // === 标题 ===
        Component title = Component.literal(npcName).withStyle(style -> style.withBold(true));
        int titleX = panelLeft + (PANEL_WIDTH - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, panelTop + 8, 0xFF_D4A017);

        // === 职业 ===
        Component prof = Component.literal("职业：" + professionName);
        int profX = panelLeft + (PANEL_WIDTH - font.width(prof)) / 2;
        graphics.drawString(font, prof, profX, panelTop + 22, 0xCC_CCCCCC);

        // === 分割线 ===
        graphics.fill(panelLeft + 8, panelTop + 36, panelLeft + PANEL_WIDTH - 8, panelTop + 37, 0x55_AA5500);

        // === 阵营 ===
        Faction faction = FactionRegistry.get(factionName);
        String factionDisplayName = faction != null ? getFactionDisplayName(faction) : factionName;
        // player 阵营 NPC 显示 "(已招募)"
        if ("sagadyssey:player".equals(factionName)) {
            factionDisplayName += Component.translatable("gui.sagadyssey.recruited").getString();
        }
        int factionColor = faction != null ? faction.color() : 0xFF_FFFF55;
        Component factionComp = Component.literal("阵营：" + factionDisplayName);
        int factionX = panelLeft + (PANEL_WIDTH - font.width(factionComp)) / 2;
        graphics.drawString(font, factionComp, factionX, panelTop + 42, factionColor);

        // === 属性面板（两列布局） ===
        int col1X = panelLeft + 12;
        int col2X = panelLeft + 92;
        int rowY = panelTop + 52;
        int rowH = 14;

        drawStat(graphics, col1X, rowY, "生命", String.format("%.0f / %.0f", currentHp, maxHp), 0xFF_55FF55);
        drawStat(graphics, col2X, rowY, "等级", String.valueOf(npcLevel), 0xFF_FFFF55);
        rowY += rowH;

        drawStat(graphics, col1X, rowY, "攻击", String.format("%.1f", attackDamage), 0xFF_FF5555);
        drawStat(graphics, col2X, rowY, "经验", String.valueOf(experience), 0xFF_55FFFF);
        rowY += rowH;

        drawStat(graphics, col1X, rowY, "速度", String.format("%.1f", speed), 0xFF_FFFFFF);
        drawStat(graphics, col2X, rowY, "击杀", String.valueOf(kills), 0xFF_FFAA00);
        rowY += rowH;

        drawStat(graphics, col1X, rowY, "护甲", String.format("%.1f", armor), 0xFF_AAAAFF);
        drawStat(graphics, col2X, rowY, "士气", moral + " / 100", 0xFF_FF88FF);
        rowY += rowH;

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 绘制单个属性标签：名字（灰色）+ 值（指定颜色） */
    private void drawStat(GuiGraphics graphics, int x, int y, String label, String value, int valueColor) {
        Component labelComp = Component.literal(label + ": ");
        graphics.drawString(font, labelComp, x, y, 0xAA_AAAAAA);
        graphics.drawString(font, Component.literal(value), x + font.width(labelComp), y, valueColor);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 覆盖原版背景渲染为空操作 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制原版背景，面板自身提供深色背景
    }

    /** 获取阵营显示名（fallback） */
    private static String getFactionDisplayName(Faction faction) {
        return switch (faction.id()) {
            case "sagadyssey:kingdom" -> "王国卫队";
            case "sagadyssey:merchant_guild" -> "商会联盟";
            case "sagadyssey:church" -> "圣殿教团";
            case "sagadyssey:dwarven" -> "矮人部族";
            case "sagadyssey:mystic" -> "秘法学会";
            case "sagadyssey:wilderness" -> "荒野流民";
            case "sagadyssey:bandit" -> "劫掠者";
            default -> faction.displayName();
        };
    }
}
