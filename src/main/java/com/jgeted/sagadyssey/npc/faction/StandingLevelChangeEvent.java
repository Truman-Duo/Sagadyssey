package com.jgeted.sagadyssey.npc.faction;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/**
 * 声望等级发生变化事件（不可取消）。
 * <p>
 * 在 StandingChangedEvent 之前逐个触发，
 * 含直接修改和涟漪传播触发的等级变化。
 */
public class StandingLevelChangeEvent extends Event {

    private final Player player;
    private final Faction faction;
    private final StandingLevel oldLevel;
    private final StandingLevel newLevel;

    public StandingLevelChangeEvent(Player player, Faction faction,
                                     StandingLevel oldLevel, StandingLevel newLevel) {
        this.player = player;
        this.faction = faction;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() { return player; }
    public Faction getFaction() { return faction; }
    public StandingLevel getOldLevel() { return oldLevel; }
    public StandingLevel getNewLevel() { return newLevel; }
}
