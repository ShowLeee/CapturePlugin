package org.showlee.capturePlugin;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class CapturePoint {
    private final String name;
    private final String displayName;
    private final Location center;
    private final PointType type;
    private String controllingTeam;
    private int progress;
    private final Set<Player> playersInZone = new HashSet<>();
    private final BossBar bossBar;
    private final CapturePlugin plugin;
    private double rotationAngle = 0;
    private final List<Location> circleParticles = new ArrayList<>();
    private final int zoneRadius = 6;
    private boolean beaconPulse = false;
    private int pulseCounter = 0;

    private String currentCapturingTeam = null;
    private int captureProgress = 0;
    private long lastCaptureTime = 0;

    public CapturePoint(String configName, Location center, PointType type, String initialTeam, CapturePlugin plugin) {
        this.name = configName;
        this.displayName = plugin.getConfig().getString("points." + configName + ".name", configName);
        this.center = center;
        this.type = type;
        this.controllingTeam = initialTeam;
        this.plugin = plugin;

        this.bossBar = Bukkit.createBossBar(
                getDisplayName(),
                getBarColor(),
                BarStyle.SEGMENTED_20
        );
        updateBossBar();
        initCircleParticles();
        startVisualEffects();
    }

    private void initCircleParticles() {
        int particles = 48;
        for (int i = 0; i < particles; i++) {
            double angle = 2 * Math.PI * i / particles;
            circleParticles.add(center.clone().add(
                    Math.cos(angle) * zoneRadius,
                    0.1,
                    Math.sin(angle) * zoneRadius
            ));
        }
    }

    private void startVisualEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                renderVisualEffects();
                if (pulseCounter++ % 4 == 0) beaconPulse = !beaconPulse;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void renderVisualEffects() {
        World world = center.getWorld();
        Color teamColor = getTeamColor();

        for (Location loc : circleParticles) {
            world.spawnParticle(Particle.REDSTONE, loc, 1,
                    new Particle.DustOptions(teamColor, 1.2f));
        }

        rotationAngle += 0.2;
        for (int i = 0; i < 8; i++) {
            double angle = rotationAngle + (i * Math.PI/4);
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    center.clone().add(
                            Math.cos(angle) * (zoneRadius - 0.5),
                            0.5,
                            Math.sin(angle) * (zoneRadius - 0.5)
                    ), 1);
        }

        if (progress >= 85) {
            if (beaconPulse) {
                spawnBeam(teamColor);
            }
        } else {
            spawnBeam(teamColor);
        }
    }

    private void spawnBeam(Color color) {
        World world = center.getWorld();
        for (double y = 0; y <= 2; y += 0.5) {
            world.spawnParticle(Particle.REDSTONE,
                    center.clone().add(0, y, 0), 2,
                    new Particle.DustOptions(color, 1.5f));
        }
    }

    public void update() {
        checkPlayersInZone();
        updateBossBarVisibility();
        updateProgress();
        checkCapture();
        updateBossBar();
    }

    private void checkPlayersInZone() {
        playersInZone.clear();
        for (Player player : center.getWorld().getPlayers()) {
            if (isInZone(player.getLocation())) {
                playersInZone.add(player);
            }
        }
    }

    public boolean isInZone(Location loc) {
        if (!loc.getWorld().equals(center.getWorld())) return false;
        return loc.distanceSquared(center) <= zoneRadius * zoneRadius;
    }

    private void updateProgress() {
        String capturingTeam = determineCapturingTeam();

        if (capturingTeam == null) {
            resetCaptureProgress();
            return;
        }

        if (!capturingTeam.equals(currentCapturingTeam)) {
            resetCaptureProgress();
            currentCapturingTeam = capturingTeam;
        }

        int captureSpeed = plugin.getConfig().getInt("capture.speed", 1);
        if (!capturingTeam.equals(controllingTeam)) {
            captureProgress += captureSpeed;
        } else {
            captureProgress -= captureSpeed;
        }

        captureProgress = Math.max(0, Math.min(100, captureProgress));
        progress = captureProgress;
    }

    private void resetCaptureProgress() {
        if (captureProgress > 0) {
            center.getWorld().playSound(center, Sound.BLOCK_ANVIL_PLACE, 0.5f, 1f);
            center.getWorld().spawnParticle(Particle.SMOKE_NORMAL, center, 20, 1, 1, 1, 0.1);
        }
        captureProgress = 0;
    }

    private String determineCapturingTeam() {
        String team = null;
        for (Player player : playersInZone) {
            Team playerTeam = player.getScoreboard().getPlayerTeam(player);
            String teamName = playerTeam != null ? playerTeam.getName() : null;

            if (team == null) {
                team = teamName;
            } else if (teamName == null || !teamName.equals(team)) {
                return null;
            }
        }
        return team;
    }

    private void checkCapture() {
        if (captureProgress >= 100) {
            controllingTeam = currentCapturingTeam;
            lastCaptureTime = System.currentTimeMillis();
            playCaptureEffects();
            resetCaptureProgress();
            currentCapturingTeam = null;
        }
    }

    private void playCaptureEffects() {
        World world = center.getWorld();
        Color teamColor = getTeamColor();

        for (double y = 0; y <= 5; y += 0.3) {
            world.spawnParticle(Particle.REDSTONE,
                    center.clone().add(0, y, 0), 3,
                    new Particle.DustOptions(teamColor, 3f));
        }

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1f);
    }

    private void updateBossBar() {
        if (currentCapturingTeam != null) {
            bossBar.setTitle("§6" + displayName + " §7(§" +
                    (currentCapturingTeam.equals("Alpha") ? "9Альфа" : "cБета") +
                    "§7) §e" + captureProgress + "%");
        } else {
            bossBar.setTitle("§6" + displayName + " §7[" +
                    (controllingTeam.equals("Alpha") ? "§9Альфа" :
                            controllingTeam.equals("Beta") ? "§cБета" : "§fНейтр") +
                    "§7]");
        }
        bossBar.setProgress(progress / 100.0);
        bossBar.setColor(getBarColor());
    }

    private Color getTeamColor() {
        switch (controllingTeam) {
            case "Alpha": return Color.fromRGB(0, 100, 255);
            case "Beta": return Color.fromRGB(255, 50, 50);
            default: return Color.fromRGB(220, 220, 220);
        }
    }

    private BarColor getBarColor() {
        switch (controllingTeam) {
            case "Alpha": return BarColor.BLUE;
            case "Beta": return BarColor.RED;
            default: return BarColor.WHITE;
        }
    }

    public void updateBossBarVisibility() {
        for (Player player : center.getWorld().getPlayers()) {
            if (isInZone(player.getLocation())) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    public void cleanup() {
        bossBar.removeAll();
    }

    // Геттеры
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public Location getCenter() { return center; }
    public PointType getType() { return type; }
    public String getControllingTeam() { return controllingTeam; }
    public int getProgress() { return progress; }
    public boolean isOnCooldown() {
        return System.currentTimeMillis() - lastCaptureTime <
                plugin.getConfig().getInt("capture.cooldown", 30) * 1000L;
    }
}