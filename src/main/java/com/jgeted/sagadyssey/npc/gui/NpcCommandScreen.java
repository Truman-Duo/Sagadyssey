package com.jgeted.sagadyssey.npc.gui;

import com.jgeted.sagadyssey.npc.network.NpcInteractionPacket;
import com.jgeted.sagadyssey.npc.network.NpcStatsPayload;
import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import com.jgeted.sagadyssey.npc.trade.NpcTradeOffer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

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
    private final String farmModeName;

    private final List<NpcTradeOffer> trades;

    // 坐骑数据
    private final boolean hasMount;
    private final int mountType;
    private final float mountHp;
    private final float mountMaxHp;
    private final float mountSpeed;
    private final String mountName;
    private final boolean leadMountMode;
    private final boolean mountLeashed;

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 248;

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
        this.farmModeName = data.farmModeName();
        this.trades = data.buildTrades();
        this.hasMount = data.hasMount();
        this.mountType = data.mountType();
        this.mountHp = data.mountHp();
        this.mountMaxHp = data.mountMaxHp();
        this.mountSpeed = data.mountSpeed();
        this.mountName = data.mountName();
        this.leadMountMode = data.leadMountMode();
        this.mountLeashed = data.mountLeashed();
    }

    @Override
    protected void init() {
        super.init();
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;

        int btnW = 76;
        int leftX = panelLeft + 10;
        int rightX = panelLeft + 90;
        int btnY1 = panelTop + 110;
        int btnY2 = panelTop + 138;

        // 第一行：跟随 / 工作 / 待命（三按钮）
        int workBtnW = 48;
        int btn2X = leftX + workBtnW + 6;
        int btn3X = btn2X + workBtnW + 6;

        addRenderableWidget(Button.builder(
                Component.literal("跟随我" + (commandName.equals("FOLLOW") ? " ✓" : "")),
                btn -> {
                    PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "follow"));
                    this.onClose();
                })
                .bounds(leftX, btnY1, workBtnW, 20)
                .build());

        // 工作按钮：农民→务农（打开务农模式面板），工人→干活，其余职业置灰
        NpcProfession prof = NpcProfession.fromDisplayName(professionName);
        boolean canWork = prof == NpcProfession.FARMER || prof == NpcProfession.WORKER;
        String workLabel = prof == NpcProfession.FARMER ? "务农"
                : prof == NpcProfession.WORKER ? "干活" : "工作";
        Button workBtn = Button.builder(
                Component.literal(workLabel + (commandName.equals("WORK") ? " ✓" : "")),
                btn -> {
                    if (prof == NpcProfession.FARMER) {
                        // 农民：弹出务农模式面板，选完模式再开始工作
                        this.minecraft.setScreen(new NpcFarmModeScreen(npcId, npcName, farmModeName));
                    } else {
                        PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "work"));
                        this.onClose();
                    }
                })
                .bounds(btn2X, btnY1, workBtnW, 20)
                .build();
        workBtn.active = canWork; // 非工作职业置灰
        addRenderableWidget(workBtn);

        addRenderableWidget(Button.builder(
                Component.literal("原地待命" + (commandName.equals("STAY") ? " ✓" : "")),
                btn -> {
                    PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "stay"));
                    this.onClose();
                })
                .bounds(btn3X, btnY1, workBtnW, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("装备"),
                btn -> {
                    PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "open_equip"));
                    this.onClose();
                })
                .bounds(leftX, btnY2, btnW, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("切换职业"),
                btn -> {
                    this.minecraft.setScreen(new NpcProfessionScreen(npcId, npcName, professionName));
                })
                .bounds(rightX, btnY2, btnW, 20)
                .build());

        // 第三行：交易 + 坐骑
        int btnY3 = panelTop + 166;
        addRenderableWidget(Button.builder(
                Component.literal("交易"),
                btn -> {
                    this.minecraft.setScreen(new NpcTradeScreen(npcId, npcName, professionName, npcLevel, experience, trades));
                })
                .bounds(leftX, btnY3, btnW, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal(hasMount ? "管理坐骑" : "分配坐骑"),
                btn -> {
                    if (hasMount) {
                        PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "unbind_mount"));
                    } else {
                        PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "request_mounts"));
                    }
                    this.onClose();
                })
                .bounds(rightX, btnY3, btnW, 20)
                .build());

        // 第四行：拴马/解除拴马 + 上马/牵马（仅在有坐骑时显示）
        if (hasMount) {
            int btnY4 = panelTop + 194;
            addRenderableWidget(Button.builder(
                    Component.literal(mountLeashed ? "解除拴马" : "拴在栅栏"),
                    btn -> {
                        PacketDistributor.sendToServer(new NpcInteractionPacket(npcId,
                                mountLeashed ? "untether_mount" : "tether_mount"));
                        this.onClose();
                    })
                    .bounds(leftX, btnY4, btnW, 20)
                    .build());

            addRenderableWidget(Button.builder(
                    Component.literal(leadMountMode ? "上马" : "牵马步行"),
                    btn -> {
                        PacketDistributor.sendToServer(new NpcInteractionPacket(npcId,
                                leadMountMode ? "mount_up" : "lead_mount"));
                        this.onClose();
                    })
                    .bounds(rightX, btnY4, btnW, 20)
                    .build());
        }

        // × 关闭按钮
        addRenderableWidget(Button.builder(
                Component.literal("✕").withStyle(s -> s.withColor(0xFF_FF5555)),
                btn -> this.onClose())
                .bounds(panelLeft + PANEL_WIDTH - 20, panelTop + 4, 16, 16)
                .build());

        // 解散按钮（恢复原阵营、清除主人）
        addRenderableWidget(Button.builder(
                Component.literal("解散"),
                btn -> {
                    PacketDistributor.sendToServer(new NpcInteractionPacket(npcId, "dismiss"));
                    this.onClose();
                })
                .bounds(panelLeft + 10, panelTop + PANEL_HEIGHT - 28, PANEL_WIDTH - 20, 20)
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

        // 坐骑信息区域
        int mountY = panelTop + 190;
        graphics.fill(panelLeft + 8, mountY, panelLeft + PANEL_WIDTH - 8, mountY + 24, 0x33_000000);
        if (hasMount) {
            String typeName = switch (mountType) {
                case 2 -> "驴";
                case 3 -> "骡";
                case 4 -> "骆驼";
                default -> "马";
            };
            graphics.drawString(font, "坐骑: " + typeName + " (" + mountName + ")",
                    panelLeft + 14, mountY + 2, 0xFF_FFAA00);
            float hpRatio = mountMaxHp > 0 ? mountHp / mountMaxHp : 0;
            int barW = 60;
            int barX = panelLeft + 14;
            int barY = mountY + 14;
            graphics.fill(barX, barY, barX + barW, barY + 5, 0x55_000000);
            graphics.fill(barX, barY, barX + (int)(barW * hpRatio), barY + 5, 0xFF_00AA00);
            graphics.drawString(font, String.format("%.0f/%.0f", mountHp, mountMaxHp),
                    barX + barW + 4, barY - 1, 0xFF_88FF88);
        } else {
            graphics.drawString(font, "坐骑: 无（牵马右键 NPC 分配）",
                    panelLeft + 14, mountY + 6, 0xFF_888888);
        }

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
