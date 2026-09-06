package com.jgeted.sagadyssey.npc.faction.api;

import com.jgeted.sagadyssey.npc.faction.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * {@link SagadysseyFactionApi} 的默认实现。
 */
public final class SagadysseyFactionApiImpl implements SagadysseyFactionApi {

    private static final SagadysseyFactionApiImpl INSTANCE = new SagadysseyFactionApiImpl();

    public static SagadysseyFactionApi getInstance() {
        return INSTANCE;
    }

    private SagadysseyFactionApiImpl() {}

    @Override
    public Faction getFaction(ResourceLocation id) {
        return FactionRegistry.get(id);
    }

    @Override
    public Collection<Faction> getAllFactions() {
        return FactionRegistry.getAllFactions();
    }

    @Override
    public int getStanding(Player player, Faction faction) {
        return FactionAttachments.getStandings(player).getValue(faction);
    }

    @Override
    public StandingLevel getStandingLevel(Player player, Faction faction) {
        return FactionAttachments.getStandings(player).getLevel(faction);
    }

    @Override
    public boolean isHostileTo(Player player, Faction faction) {
        return FactionAttachments.getStandings(player).isHostile(faction);
    }

    @Override
    public void modifyStanding(Player player, Faction faction, int delta, String reason) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(faction, "faction must not be null");
        if (!(player instanceof ServerPlayer sp)) {
            throw new IllegalStateException("modifyStanding must be called on server side");
        }
        StandingModifier.applyModification(sp, faction, delta, reason);
    }

    @Override
    public void setStanding(Player player, Faction faction, int value, String reason) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(faction, "faction must not be null");
        if (!(player instanceof ServerPlayer sp)) {
            throw new IllegalStateException("setStanding must be called on server side");
        }
        int oldValue = FactionAttachments.getStandings(player).getValue(faction);
        int delta = value - oldValue;
        if (delta != 0) {
            StandingModifier.applyModification(sp, faction, delta, reason);
        }
    }

    @Override
    public InterFactionRelation getRelation(Faction a, Faction b) {
        return FactionRelationMatrix.getInstance().getRelation(a, b);
    }

    @Override
    public Set<Faction> getHostileFactions(Player player) {
        return FactionAttachments.getStandings(player).getHostileFactions();
    }

    @Override
    public Set<Faction> getAlliedFactions(Player player) {
        return FactionAttachments.getStandings(player).getAlliedFactions();
    }
}
