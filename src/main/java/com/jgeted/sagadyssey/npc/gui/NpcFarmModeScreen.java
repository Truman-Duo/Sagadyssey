package com.jgeted.sagadyssey.npc.gui;

import com.jgeted.sagadyssey.npc.entity.FarmMode;
import com.jgeted.sagadyssey.npc.network.NpcFarmModePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 农民务农模式选择界面。
 * 五个模式：种植农作物 / 种瓜类 / 种植甘蔗 / 种植可可豆 / 综合。点击后设置模式并开始工作。
 */
public class NpcFarmModeScreen extends Screen {

    private final int npcId;
    private final String npcName;
    private final String currentModeName;

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 184;

    private int panelLeft;
    private int panelTop;

    public NpcFarmModeScreen(int npcId, String npcName, String currentModeName) {
        super(Component.literal("务农模式"));
        this.npcId = npcId;
        this.npcName = npcName;
        this.currentModeName = currentModeName;
    }

    @Override
    protected void init() {
        super.init();
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;

        FarmMode[] modes = FarmMode.values();
        int btnW = PANEL_WIDTH - 20;
        int btnH = 20;
        int startX = panelLeft + 10;
        int startY = panelTop + 50;

        for (int i = 0; i < modes.length; i++) {
            FarmMode mode = modes[i];
            int y = startY + i * (btnH + 6);
            String label = mode.getDisplayName();
            if (mode.name().equals(currentModeName)) {
                label += " ✓";
            }
            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    btn -> {
                        PacketDistributor.sendToServer(new NpcFarmModePacket(npcId, mode.name()));
                        this.onClose();
                    })
                    .bounds(startX, y, btnW, btnH)
                    .build());
        }

        // × 关闭按钮
        addRenderableWidget(Button.builder(
                Component.literal("✕").withStyle(s -> s.withColor(0xFF_FF5555)),
                btn -> this.onClose())
                .bounds(panelLeft + PANEL_WIDTH - 20, panelTop + 4, 16, 16)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88_000000);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xCC_1A1A2E);
        // 金色边框
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop, 0xFF_AA5500);
        graphics.fill(panelLeft - 1, panelTop + PANEL_HEIGHT, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFF_AA5500);
        graphics.fill(panelLeft - 1, panelTop, panelLeft, panelTop + PANEL_HEIGHT, 0xFF_AA5500);
        graphics.fill(panelLeft + PANEL_WIDTH, panelTop, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT, 0xFF_AA5500);

        Component title = Component.literal("务农模式 - " + npcName).withStyle(s -> s.withBold(true));
        int titleX = panelLeft + (PANEL_WIDTH - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, panelTop + 8, 0xFF_D4A017);

        graphics.fill(panelLeft + 8, panelTop + 26, panelLeft + PANEL_WIDTH - 8, panelTop + 27, 0x55_AA5500);

        Component hint = Component.literal("选择后 NPC 会开始工作");
        int hintX = panelLeft + (PANEL_WIDTH - font.width(hint)) / 2;
        graphics.drawString(font, hint, hintX, panelTop + 32, 0xFF_55FF55);

        super.render(graphics, mouseX, mouseY, partialTick);
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
