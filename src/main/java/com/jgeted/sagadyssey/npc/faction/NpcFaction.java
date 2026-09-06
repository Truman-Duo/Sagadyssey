package com.jgeted.sagadyssey.npc.faction;

/**
 * NPC 阵营（旧版枚举，仅用于 NBT 向后兼容）。
 * <p>
 * 新代码应使用 {@link Faction} record 和 {@link FactionRegistry}，
 * 而非此枚举。此枚举保留仅为读取旧存档中已保存的 NPC 阵营标签。
 *
 * @deprecated 请使用 {@link Faction} 和 {@link FactionRegistry} 替代。
 */
@Deprecated
public enum NpcFaction {
    HOSTILE("敌对"),
    NEUTRAL("中立"),
    FRIENDLY("友好");

    private final String displayName;

    NpcFaction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
