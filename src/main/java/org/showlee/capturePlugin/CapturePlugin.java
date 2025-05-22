package org.showlee.capturePlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class CapturePlugin extends JavaPlugin {
    private final Map<String, CapturePoint> capturePoints = new HashMap<>();
    private FileConfiguration config;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        loadCapturePoints();
        gameManager = new GameManager(this);
        getCommand("capture").setExecutor(new CaptureCommand(this));
        getServer().getPluginManager().registerEvents(new CaptureListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        // Запускаем таймер для обновления точек
        new BukkitRunnable() {
            @Override
            public void run() {
                updateCapturePoints();

                // Визуальные эффекты теперь автоматически обрабатываются внутри CapturePoint
                // при каждом вызове update()
            }
        }.runTaskTimer(this, 20L, 20L); // Обновление раз в секунду
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CapturePlaceholder(gameManager).register();
        }
        getServer().getPluginManager().registerEvents(
                new PlayerDeathListener(gameManager), this);
    }

    @Override
    public void onDisable() {
        // Очищаем все BossBar и эффекты
        capturePoints.values().forEach(CapturePoint::cleanup);
    }

    private void loadCapturePoints() {
        if (config.contains("points")) {
            for (String pointName : config.getConfigurationSection("points").getKeys(false)) {
                String path = "points." + pointName;

                Location loc = new Location(
                        Bukkit.getWorld(config.getString(path + ".world")),
                        config.getDouble(path + ".x"),
                        config.getDouble(path + ".y"),
                        config.getDouble(path + ".z")
                );

                PointType type = PointType.valueOf(config.getString(path + ".type"));
                String team = config.getString(path + ".team");

                capturePoints.put(pointName, new CapturePoint(pointName, loc, type, team, this));
            }
        }
    }
    public GameManager getGameManager() {
        return gameManager;
    }
    private void updateCapturePoints() {
        capturePoints.values().forEach(CapturePoint::update);
    }

    public Map<String, CapturePoint> getCapturePoints() {
        return capturePoints;
    }
}