package com.jgeted.sagadyssey.npc.faction.gui;

/**
 * NPC 对话中的阵营信息叠层。
 * <p>
 * TODO: 后续在 NPC 交易 Screen 的 render 方法中叠加阵营纹章 + 声望等级。
 * 实现路线：
 * <ul>
 *   <li>通过 Mixin 在现有 NPC 交易 Screen 的 render 方法中叠加渲染</li>
 *   <li>使用 {@code GuiGraphics#drawString} + {@code GuiGraphics#blit}</li>
 *   <li>对 COLD 状态的 NPC，在打开交易界面前先弹出提示消息</li>
 *   <li>对话文本通过 lang/faction_dialogue.json 管理</li>
 * </ul>
 */
public final class FactionChatOverlay {

    private FactionChatOverlay() {}

    // TODO: 实现 NPC 对话中的阵营纹章 + 声望等级叠层渲染
}
