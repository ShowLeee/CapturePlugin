package org.showlee.capturePlugin;

import org.bukkit.ChatColor;

public enum PointType {
    ALPHA_TEAM("Alpha", ChatColor.BLUE),
    BETA_TEAM("Beta", ChatColor.RED),
    NEUTRAL("Neutral", ChatColor.WHITE);

    private final String displayName;
    private final ChatColor color;

    PointType(String displayName, ChatColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    // Геттеры
    public String getDisplayName() { return displayName; }
    public ChatColor getColor() { return color; }
}