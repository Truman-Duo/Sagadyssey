package com.jgeted.sagadyssey.npc.faction;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 声望即将被修改事件（可取消）。
 * <p>
 * 在 multiplier 应用后、值写入前触发。
 * 取消后声望不变，涟漪也不触发。
 * <p>
 * 通过 NeoForge EVENT_BUS 触发（非 mod bus）。
 */
public class StandingModifyEvent extends Event implements ICancellableEvent {

    private final Player player;
    private final Faction faction;
    private int delta;
    private final String reason;

    public StandingModifyEvent(Player player, Faction faction, int delta, String reason) {
        this.player = player;
        this.faction = faction;
        this.delta = delta;
        this.reason = reason;
    }

    public Player getPlayer() { return player; }
    public Faction getFaction() { return faction; }
    public int getDelta() { return delta; }
    /** 监听器可修改 delta */
    public void setDelta(int delta) { this.delta = delta; }
    public String getReason() { return reason; }
}
