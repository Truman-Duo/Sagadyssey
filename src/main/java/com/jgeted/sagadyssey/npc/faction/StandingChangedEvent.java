package com.jgeted.sagadyssey.npc.faction;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/**
 * 声望已被修改事件（不可取消）。
 * <p>
 * 在所有涟漪完成后、同步到客户端前触发。
 * 此时 getStanding() 返回的是最终值。
 */
public class StandingChangedEvent extends Event {

    private final Player player;
    private final Faction faction;
    private final int oldValue;
    private final int newValue;
    private final String reason;

    public StandingChangedEvent(Player player, Faction faction,
                                 int oldValue, int newValue, String reason) {
        this.player = player;
        this.faction = faction;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
    }

    public Player getPlayer() { return player; }
    public Faction getFaction() { return faction; }
    public int getOldValue() { return oldValue; }
    public int getNewValue() { return newValue; }
    public String getReason() { return reason; }
}
