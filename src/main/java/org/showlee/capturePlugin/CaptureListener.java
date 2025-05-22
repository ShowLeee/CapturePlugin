package org.showlee.capturePlugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CaptureListener implements Listener {
    private final CapturePlugin plugin;

    public CaptureListener(CapturePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            if (point.isInZone(event.getTo())) {
                point.showBossBar(event.getPlayer());
            } else if (point.isInZone(event.getFrom())) {
                point.hideBossBar(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            point.hideBossBar(event.getPlayer());
        }
    }
}