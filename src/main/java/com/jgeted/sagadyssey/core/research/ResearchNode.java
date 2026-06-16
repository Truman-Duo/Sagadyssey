package com.jgeted.sagadyssey.core.research;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ResearchNode {
    private final String id;
    private final String name;
    private final String description;
    private final int cost;
    private final java.util.List<String> prerequisites;
    private final boolean isRoot;

    public ResearchNode(String id, String name, String description, int cost,
                        java.util.List<String> prerequisites, boolean isRoot) {
        this.id = id; this.name = name; this.description = description;
        this.cost = cost; this.prerequisites = prerequisites; this.isRoot = isRoot;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCost() { return cost; }
    public java.util.List<String> getPrerequisites() { return prerequisites; }
    public boolean isRoot() { return isRoot; }

    public boolean arePrerequisitesMet(Set<String> unlocked) {
        if (isRoot) return true;
        for (String prereq : prerequisites) {
            if (!unlocked.contains(prereq)) return false;
        }
        return true;
    }
}
