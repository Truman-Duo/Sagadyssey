package com.jgeted.sagadyssey.core.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PlayerResearchData {
    public static final Codec<PlayerResearchData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.listOf().xmap(HashSet::new, ArrayList::new)
                            .fieldOf("unlocked").forGetter(d -> new HashSet<>(d.unlocked))
            ).apply(instance, PlayerResearchData::new)
    );

    private final Set<String> unlocked;

    public PlayerResearchData() { this.unlocked = new HashSet<>(); }
    public PlayerResearchData(Set<String> unlocked) { this.unlocked = new HashSet<>(unlocked); }

    public boolean isUnlocked(String nodeId) { return unlocked.contains(nodeId); }
    public boolean unlock(String nodeId) { return unlocked.add(nodeId); }
    public Set<String> getUnlocked() { return unlocked; }
}
