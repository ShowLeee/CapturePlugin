package org.showlee.capturePlugin;

import org.bukkit.entity.Player;

public class CapturePlaceholder extends PlaceholderExpansion {
    private final GameManager gameManager;

    public CapturePlaceholder(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public String onPlaceholderRequest(Player p, String identifier) {
        if (identifier.equals("kills")) {
            return String.valueOf(gameManager.getPlayerKills(p));
        }
        return null;
    }

    // ... обязательные методы расширения ...
}