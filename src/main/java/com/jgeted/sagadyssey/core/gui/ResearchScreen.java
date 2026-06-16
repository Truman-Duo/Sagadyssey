package com.jgeted.sagadyssey.core.gui;

import com.jgeted.sagadyssey.core.network.ResearchUnlockPacket;
import com.jgeted.sagadyssey.core.research.ClientResearchCache;
import com.jgeted.sagadyssey.core.research.ResearchNode;
import com.jgeted.sagadyssey.core.research.ResearchRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * 技能树界面。按 K 键打开。
 * 每帧从 ClientResearchCache 读取，服务端同步包到达后自动刷新。
 */
public class ResearchScreen extends Screen {
    private static final int WIDTH = 280;
    private static final int HEIGHT = 200;
    private static final int LIST_X = 15;
    private static final int LIST_WIDTH = 130;
    private static final int DETAIL_X = 160;
    private static final int ROW_HEIGHT = 14;

    private int scrollOffset = 0;
    private ResearchNode selectedNode = null;

    public ResearchScreen() {
        super(Component.literal("技能树"));
    }

    private Set<String> unlockedNodes() { return ClientResearchCache.getUnlocked(); }
    private int availablePoints() { return ClientResearchCache.getPoints(); }
    private List<ResearchNode> visibleNodes() {
        return new ArrayList<>(ResearchRegistry.getVisibleNodes(unlockedNodes()));
    }

    @Override
    protected void init() {
        super.init();
    }

    /** 画关闭按钮 */
    private void renderCloseButton(GuiGraphics graphics, int baseX, int baseY, int mouseX, int mouseY) {
        int x = baseX + WIDTH - 18;
        int y = baseY + 4;
        boolean hovered = mouseX >= x && mouseX <= x + 12 && mouseY >= y && mouseY <= y + 12;
        graphics.fill(x, y, x + 12, y + 12, hovered ? 0xFFCC3333 : 0xFF666666);
        graphics.drawString(font, "✕", x + 2, y + 1, 0xFFFFFF);
    }

    /** 检测关闭按钮点击 */
    private boolean closeButtonClicked(double mouseX, double mouseY) {
        int baseX = (this.width - WIDTH) / 2;
        int baseY = (this.height - HEIGHT) / 2;
        int x = baseX + WIDTH - 18;
        int y = baseY + 4;
        return mouseX >= x && mouseX <= x + 12 && mouseY >= y && mouseY <= y + 12;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        var unlocked = unlockedNodes();
        int pts = availablePoints();
        var visible = visibleNodes();

        int baseX = (this.width - WIDTH) / 2;
        int baseY = (this.height - HEIGHT) / 2;

        // 背景
        graphics.fill(baseX, baseY, baseX + WIDTH, baseY + HEIGHT, 0xCC000000);

        // 标题
        graphics.drawString(font, "技能树（点数：" + pts + "）",
                baseX + 10, baseY + 6, 0xFFFFFF);

        // 关闭按钮
        renderCloseButton(graphics, baseX, baseY, mouseX, mouseY);

        // 分隔线
        graphics.fill(baseX + LIST_WIDTH + 20, baseY + 20,
                baseX + LIST_WIDTH + 21, baseY + HEIGHT - 10, 0xFF555555);

        // 左侧：节点列表
        renderNodeList(graphics, baseX, baseY, mouseX, mouseY, visible, unlocked, pts);

        // 右侧：详情
        if (selectedNode != null) {
            renderDetail(graphics, baseX, baseY, unlocked, pts);
        }
    }

    private void renderNodeList(GuiGraphics graphics, int baseX, int baseY, int mouseX, int mouseY,
                                 List<ResearchNode> visible, Set<String> unlocked, int pts) {
        int y = baseY + 24;
        int maxVisible = (HEIGHT - 40) / ROW_HEIGHT;

        for (int i = scrollOffset; i < Math.min(visible.size(), scrollOffset + maxVisible); i++) {
            ResearchNode node = visible.get(i);
            int rowY = y + (i - scrollOffset) * ROW_HEIGHT;

            boolean isUnlocked = unlocked.contains(node.getId());
            boolean canUnlock = !isUnlocked
                    && node.arePrerequisitesMet(unlocked)
                    && pts >= node.getCost();

            String icon = isUnlocked ? "§a✅" : (canUnlock ? "§e▶" : "§7🔒");
            int color = isUnlocked ? 0x00FF00 : (canUnlock ? 0xFFFF00 : 0x888888);

            String label = icon + " §r" + node.getName();
            graphics.drawString(font, label, baseX + LIST_X, rowY, color);

            if (selectedNode == node) {
                graphics.fill(baseX + LIST_X - 2, rowY - 1,
                        baseX + LIST_X + LIST_WIDTH, rowY + ROW_HEIGHT - 1,
                        0x44FFFFFF);
            }
        }
    }

    private void renderDetail(GuiGraphics graphics, int baseX, int baseY,
                               Set<String> unlocked, int pts) {
        ResearchNode node = selectedNode;
        boolean isUnlocked = unlocked.contains(node.getId());
        boolean canUnlock = !isUnlocked
                && node.arePrerequisitesMet(unlocked)
                && pts >= node.getCost();

        int x = baseX + DETAIL_X;
        int y = baseY + 24;

        // 名称
        graphics.drawString(font, "§l" + node.getName(), x, y, 0xFFFFFF);
        y += 16;

        // 描述
        java.util.List<String> descLines = wrapText(node.getDescription(), WIDTH - DETAIL_X - 20);
        for (String line : descLines) {
            graphics.drawString(font, "§7" + line, x, y, 0xAAAAAA);
            y += 11;
        }
        y += 6;

        // 消耗
        graphics.drawString(font, "消耗：" + node.getCost() + " 点", x, y, 0xFFFFFF);
        y += 14;

        // 前置
        if (!node.getPrerequisites().isEmpty()) {
            graphics.drawString(font, "§7前置：", x, y, 0x888888);
            y += 11;
            for (String prereq : node.getPrerequisites()) {
                ResearchNode prereqNode = ResearchRegistry.getNode(prereq);
                String prereqName = prereqNode != null ? prereqNode.getName() : prereq;
                boolean hasPrereq = unlocked.contains(prereq);
                graphics.drawString(font, "  " + (hasPrereq ? "§a✓ " : "§c✗ ") + prereqName,
                        x, y, hasPrereq ? 0x00FF00 : 0xFF4444);
                y += 11;
            }
        }
        y += 6;

        // 状态
        if (isUnlocked) {
            graphics.drawString(font, "§a✅ 已解锁", x, y, 0x00FF00);
        } else if (canUnlock) {
            graphics.drawString(font, "§e点击解锁", x, y, 0xFFFF00);
        } else if (!node.arePrerequisitesMet(unlocked)) {
            graphics.drawString(font, "§c前置未满足", x, y, 0xFF4444);
        } else {
            graphics.drawString(font, "§c点数不足", x, y, 0xFF4444);
        }
    }

    /** 简单中文换行 */
    private java.util.List<String> wrapText(String text, int maxWidth) {
        java.util.List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(current.toString() + c) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            current.append(c);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 关闭按钮
        if (closeButtonClicked(mouseX, mouseY)) {
            onClose();
            return true;
        }

        var unlocked = unlockedNodes();
        int pts = availablePoints();
        var visible = visibleNodes();

        int baseX = (this.width - WIDTH) / 2;
        int baseY = (this.height - HEIGHT) / 2;

        int listStartY = baseY + 24;
        int maxVisible = (HEIGHT - 40) / ROW_HEIGHT;
        for (int i = scrollOffset; i < Math.min(visible.size(), scrollOffset + maxVisible); i++) {
            int rowY = listStartY + (i - scrollOffset) * ROW_HEIGHT;
            if (mouseX >= baseX + LIST_X && mouseX <= baseX + LIST_X + LIST_WIDTH
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                selectedNode = visible.get(i);

                // 如果可解锁，发送解锁包并乐观更新缓存
                ResearchNode node = visible.get(i);
                if (!unlocked.contains(node.getId())
                        && node.arePrerequisitesMet(unlocked)
                        && pts >= node.getCost()) {
                    PacketDistributor.sendToServer(new ResearchUnlockPacket(node.getId()));
                    // 乐观更新：立即刷新缓存（扣除点数、标记解锁）
                    unlocked.add(node.getId());
                    ClientResearchCache.set(pts - node.getCost(), unlocked);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, visibleNodes().size() - (HEIGHT - 40) / ROW_HEIGHT);
        scrollOffset = (int) Math.clamp(scrollOffset - scrollY, 0, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
