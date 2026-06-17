package com.jgeted.sagadyssey.npc.gui;

import com.jgeted.sagadyssey.npc.network.NpcInteractionPacket;
import com.jgeted.sagadyssey.npc.network.NpcStatsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NPC 命令界面。
 * 已招募的 NPC — 右键打开，显示属性、发送命令。
 */
public class NpcCommandScreen extends Screen {

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
    private final String commandName;

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 170;

    private int panelLeft;
    private int panelTop;

    public NpcCommandScreen(NpcStatsPayload data) {
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
        this.commandName = data.commandName();
    }

    @Override
    protected void init() {
        super.init();
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;

        int btnW = 70;
        int btnH = 20;
        int leftX = panelLeft + 18;
        int rightX = panelLeft + PANEL_WIDTH - 18 - btnW;

        addRenderableWidget(Button.builder(
                Component.literal("跟随我" + (commandName.equals("FOLLOW") ? " ✓" : "")),
                btn -> {
                    PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "follow"));
                    this.onClose();
                })
                .bounds(leftX, panelTop + 110, btnW, btnH)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("原地待命" + (commandName.equals("STAY") ? " ✓" : "")),
                btn -> {
                    PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "stay"));
                    this.onClose();
                })
                .bounds(rightX, panelTop + 110, btnW, btnH)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("装备"),
                btn -> {
                    PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "open_equip"));
                    this.onClose();
                })
                .bounds(panelLeft + 48, panelTop + 138, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88_000000);

        // 面板背景
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xCC_1A1A2E);
        // 金色边框
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop, 0xFF_AA5500);
        graphics.fill(panelLeft - 1, panelTop + PANEL_HEIGHT, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFF_AA5500);
        graphics.fill(panelLeft - 1, panelTop, panelLeft, panelTop + PANEL_HEIGHT, 0xFF_AA5500);
        graphics.fill(panelLeft + PANEL_WIDTH, panelTop, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT, 0xFF_AA5500);

        // 标题
        Component title = Component.literal(npcName).withStyle(style -> style.withBold(true));
        int titleX = panelLeft + (PANEL_WIDTH - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, panelTop + 8, 0xFF_D4A017);

        // 职业
        Component prof = Component.literal("职业：" + professionName);
        int profX = panelLeft + (PANEL_WIDTH - font.width(prof)) / 2;
        graphics.drawString(font, prof, profX, panelTop + 22, 0xCC_CCCCCC);

        // 分割线
        graphics.fill(panelLeft + 8, panelTop + 36, panelLeft + PANEL_WIDTH - 8, panelTop + 37, 0x55_AA5500);

        // 属性面板（双列布局，同 NpcRecruitScreen）
        int colX1 = panelLeft + 12;
        int colX2 = panelLeft + 92;
        int rowY = panelTop + 42;
        int rowH = 14;

        drawStat(graphics, colX1, rowY, "生命", String.format("%.0f / %.0f", currentHp, maxHp), 0xFF_55FF55);
        drawStat(graphics, colX2, rowY, "等级", String.valueOf(npcLevel), 0xFF_FFFF55);
        rowY += rowH;

        drawStat(graphics, colX1, rowY, "攻击", String.format("%.1f", attackDamage), 0xFF_FF5555);
        drawStat(graphics, colX2, rowY, "经验", String.valueOf(experience), 0xFF_55FFFF);
        rowY += rowH;

        drawStat(graphics, colX1, rowY, "速度", String.format("%.1f", speed), 0xFF_FFFFFF);
        drawStat(graphics, colX2, rowY, "击杀", String.valueOf(kills), 0xFF_FFAA00);
        rowY += rowH;

        drawStat(graphics, colX1, rowY, "护甲", String.format("%.1f", armor), 0xFF_AAAAFF);
        drawStat(graphics, colX2, rowY, "士气", moral + " / 100", 0xFF_FF88FF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 绘制单个属性标签：名字（灰色）+ 值（指定颜色） */
    private void drawStat(GuiGraphics graphics, int x, int y, String label, String value, int valueColor) {
        Component labelComp = Component.literal(label + ": ");
        graphics.drawString(font, labelComp, x, y, 0xAA_AAAAAA);
        graphics.drawString(font, Component.literal(value), x + font.width(labelComp), y, valueColor);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制原版背景
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
