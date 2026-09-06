package com.jgeted.sagadyssey.npc.faction.gui;

import com.jgeted.sagadyssey.Sagadyssey;
import com.jgeted.sagadyssey.npc.faction.Faction;
import com.jgeted.sagadyssey.npc.faction.FactionRegistry;
import com.jgeted.sagadyssey.npc.faction.StandingLevel;
import com.jgeted.sagadyssey.npc.faction.network.ClientFactionCache;
import com.jgeted.sagadyssey.npc.faction.network.RequestStandingsRefreshPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * 声望图表主界面。
 * <p>
 * 布局：上方关系网络图区域，下方阵营卡片网格（可滚动）。
 * 面板整体居中，高度随阵营数量动态调整。
 * 打开快捷键默认 R，或通过阵营地图物品右键打开。
 */
public class ReputationChartScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int HEADER_HEIGHT = 40;
    private static final int NETWORK_AREA_HEIGHT = 90;
    private static final int DIVIDER_HEIGHT = 6;
    private static final int CARD_WIDTH = 90;
    private static final int CARD_HEIGHT = 70;
    private static final int CARDS_PER_ROW = 3;
    private static final int CARD_GAP = 8;
    /** 卡片区域最大可见行数，超出则滚动 */
    private static final int MAX_VISIBLE_ROWS = 2;
    /** 滚动灵敏度（每格 16px） */
    private static final int SCROLL_STEP = 16;

    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    /** 卡片内容区域的总高度（含所有行） */
    private int cardContentHeight;
    /** 卡片内容区域的可见高度 */
    private int cardAreaHeight;
    /** 当前滚动偏移（px），≥ 0 */
    private int scrollOffset;

    /** 焦点阵营（点击卡片后选中，用于关系网络图焦点模式） */
    private Faction focusedFaction;

    public ReputationChartScreen() {
        super(Component.literal("阵营声望"));
    }

    @Override
    protected void init() {
        super.init();

        // 动态计算面板高度
        Collection<Faction> factions = FactionRegistry.getAllFactions();
        int rowCount;
        if (factions.isEmpty()) {
            rowCount = 1;
        } else {
            rowCount = (factions.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
        }
        int visibleRows = Math.min(rowCount, MAX_VISIBLE_ROWS);
        cardAreaHeight = visibleRows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
        cardContentHeight = rowCount * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
        panelHeight = HEADER_HEIGHT + NETWORK_AREA_HEIGHT + DIVIDER_HEIGHT + cardAreaHeight;

        // 面板整体居中
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - panelHeight) / 2;

        // 重置滚动（避免窗口 resize 后偏移错乱）
        if (scrollOffset > Math.max(0, cardContentHeight - cardAreaHeight)) {
            scrollOffset = Math.max(0, cardContentHeight - cardAreaHeight);
        }

        // 向服务端请求最新数据
        PacketDistributor.sendToServer(new RequestStandingsRefreshPacket());

        // 关闭按钮
        addRenderableWidget(Button.builder(
                Component.literal("✕").withStyle(s -> s.withColor(0xFF_FF5555)),
                btn -> this.onClose())
                .bounds(panelLeft + PANEL_WIDTH - 20, panelTop + 4, 16, 16)
                .build());

        // 返回按钮（焦点模式下显示）
        if (focusedFaction != null) {
            addRenderableWidget(Button.builder(
                    Component.literal("← 返回"),
                    btn -> { focusedFaction = null; this.rebuildWidgets(); })
                    .bounds(panelLeft + 8, panelTop + 4, 50, 16)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明背景遮罩
        graphics.fill(0, 0, this.width, this.height, 0x88_000000);

        // 主面板背景
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + panelHeight,
                0xCC_1A1A2E);
        // 金色边框
        drawBorder(graphics, panelLeft, panelTop, PANEL_WIDTH, panelHeight);

        // 标题
        Component title = Component.literal("阵营声望").withStyle(s -> s.withBold(true));
        int titleX = panelLeft + (PANEL_WIDTH - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, panelTop + 8, 0xFF_D4A017);

        // 关系网络图区域（或焦点模式）
        int networkY = panelTop + HEADER_HEIGHT;
        if (focusedFaction != null) {
            RelationNetworkWidget.renderFocus(graphics, font, panelLeft, networkY, PANEL_WIDTH, NETWORK_AREA_HEIGHT,
                    focusedFaction);
        }

        // 分割线
        int dividerY = networkY + NETWORK_AREA_HEIGHT;
        graphics.fill(panelLeft + 8, dividerY, panelLeft + PANEL_WIDTH - 8, dividerY + 1, 0x55_AA5500);

        // 阵营卡片网格（带滚动）
        int cardStartY = dividerY + DIVIDER_HEIGHT;
        renderCards(graphics, mouseX, mouseY, cardStartY);

        // 滚动指示器（内容超出时显示）
        if (cardContentHeight > cardAreaHeight) {
            drawScrollIndicator(graphics, cardStartY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 渲染卡片网格，处理滚动偏移和裁剪 */
    private void renderCards(GuiGraphics graphics, int mouseX, int mouseY, int cardStartY) {
        // 裁剪：确保不渲染到卡片区域外
        graphics.enableScissor(
                panelLeft + 4, cardStartY,
                panelLeft + PANEL_WIDTH - 4, cardStartY + cardAreaHeight
        );

        Collection<Faction> factions = FactionRegistry.getAllFactions();
        if (factions.isEmpty()) {
            Component empty = Component.literal("暂无阵营数据");
            graphics.drawString(font, empty,
                    panelLeft + (PANEL_WIDTH - font.width(empty)) / 2,
                    cardStartY + (cardAreaHeight - font.lineHeight) / 2, 0xAA_AAAAAA);
        } else {
            List<Faction> factionList = new ArrayList<>(factions);
            int gridStartX = panelLeft + (PANEL_WIDTH - CARDS_PER_ROW * (CARD_WIDTH + CARD_GAP) + CARD_GAP) / 2;

            int totalRows = (factionList.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
            for (int i = 0; i < factionList.size(); i++) {
                Faction f = factionList.get(i);
                int col = i % CARDS_PER_ROW;
                int row = i / CARDS_PER_ROW;
                int cy = cardStartY + row * (CARD_HEIGHT + CARD_GAP) - scrollOffset;

                // 跳过可见区域外的行
                if (cy + CARD_HEIGHT < cardStartY || cy >= cardStartY + cardAreaHeight) continue;

                int cx = gridStartX + col * (CARD_WIDTH + CARD_GAP);
                FactionCardWidget.render(graphics, font, cx, cy, CARD_WIDTH, CARD_HEIGHT, f,
                        ClientFactionCache.getLevel(f.id()), ClientFactionCache.getStanding(f.id()));
            }
        }

        graphics.disableScissor();
    }

    /** 绘制滚动条指示器 */
    private void drawScrollIndicator(GuiGraphics graphics, int cardStartY) {
        int barX = panelLeft + PANEL_WIDTH - 8;
        int barTop = cardStartY;
        int barBottom = cardStartY + cardAreaHeight;
        int barWidth = 4;

        // 轨道
        graphics.fill(barX, barTop, barX + barWidth, barBottom, 0x33_888888);

        // 滑块
        int maxScroll = Math.max(1, cardContentHeight - cardAreaHeight);
        float ratio = (float) cardAreaHeight / cardContentHeight;
        int sliderHeight = Math.max(12, (int) ((barBottom - barTop) * ratio));
        int sliderY = barTop + (int) (((barBottom - barTop - sliderHeight) * scrollOffset) / maxScroll);
        graphics.fill(barX, sliderY, barX + barWidth, sliderY + sliderHeight, 0xAA_AA5500);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 检测阵营卡片点击（使用与渲染相同的坐标计算）
            Collection<Faction> factions = FactionRegistry.getAllFactions();
            if (factions.isEmpty()) {
                // 静默返回，如果 /sagadyssey faction list 能显示阵营但 GUI 为空，说明是客户端网络同步问题
                Sagadyssey.LOGGER.warn("GUI: 阵营列表为空，可能客户端尚未同步");
                return false;
            }
            if (!factions.isEmpty()) {
                List<Faction> factionList = new ArrayList<>(factions);
                int cardStartY = panelTop + HEADER_HEIGHT + NETWORK_AREA_HEIGHT + DIVIDER_HEIGHT;
                int gridStartX = panelLeft + (PANEL_WIDTH - CARDS_PER_ROW * (CARD_WIDTH + CARD_GAP) + CARD_GAP) / 2;

                for (int i = 0; i < factionList.size(); i++) {
                    int col = i % CARDS_PER_ROW;
                    int row = i / CARDS_PER_ROW;
                    int cy = cardStartY + row * (CARD_HEIGHT + CARD_GAP) - scrollOffset;
                    int cx = gridStartX + col * (CARD_WIDTH + CARD_GAP);
                    if (mouseX >= cx && mouseX < cx + CARD_WIDTH
                            && mouseY >= cy && mouseY < cy + CARD_HEIGHT) {
                        focusedFaction = factionList.get(i);
                        this.rebuildWidgets();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 鼠标滚轮滚动卡片区域 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int maxScroll = Math.max(0, cardContentHeight - cardAreaHeight);
        if (maxScroll <= 0) return false;

        int newOffset = scrollOffset - (int) (deltaY * SCROLL_STEP);
        scrollOffset = Math.max(0, Math.min(maxScroll, newOffset));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // no-op: 使用自定义背景
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y, 0xFF_AA5500);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF_AA5500);
        graphics.fill(x - 1, y, x, y + h, 0xFF_AA5500);
        graphics.fill(x + w, y, x + w + 1, y + h, 0xFF_AA5500);
    }
}
