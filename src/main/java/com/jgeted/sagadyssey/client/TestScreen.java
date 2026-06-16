package com.jgeted.sagadyssey.client;

import com.jgeted.sagadyssey.menu.TestMenu;
import com.jgeted.sagadyssey.core.network.ChatPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 测试 GUI 屏幕。包含一个按钮，点击向服务端发送聊天消息。
 */
public class TestScreen extends AbstractContainerScreen<TestMenu> {

    private static final Component TITLE = Component.literal("Sagadyssey 测试界面");
    private static final Component BUTTON_TEXT = Component.literal("点击我发送消息");

    public TestScreen(TestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 100;
    }

    @Override
    protected void init() {
        super.init();
        int buttonX = (width - 120) / 2;
        int buttonY = (height - 20) / 2;

        addRenderableWidget(Button.builder(BUTTON_TEXT, button -> {
            PacketDistributor.sendToServer(new ChatPayload("你好，世界！"));
        })
                .bounds(buttonX, buttonY, 120, 20)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 绘制半透明背景
        guiGraphics.fill((width - 176) / 2, (height - 100) / 2,
                (width + 176) / 2, (height + 100) / 2,
                0xCC000000);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleX = (imageWidth - font.width(TITLE)) / 2;
        guiGraphics.drawString(font, TITLE, titleX, 10, 0xFFFFFF, false);
    }
}
