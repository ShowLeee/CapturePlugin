package org.showlee.capturePlugin;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameManager {
    private final CapturePlugin plugin;
    private BossBar waitingBar;
    private BossBar countdownBar;
    private BossBar gameTimerBar;
    private int countdown = 15;
    private int gameTime;
    private boolean gameActive = false;
    private final List<Player> players = new ArrayList<>();
    private final Map<String, Location> teamSpawns = new HashMap<>();
    private final Map<String, String> teamMessages = new HashMap<>();
    private final int minPlayers;
    private final int maxPlayers;
    private final int restartDelay;

    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private int alphaPoints = 0;
    private int betaPoints = 0;
    private BukkitRunnable gameTimerTask;
    private BukkitRunnable victoryCheckTask;
    private BukkitRunnable scoreboardTask;

    public GameManager(CapturePlugin plugin) {
        this.plugin = plugin;
        this.minPlayers = plugin.getConfig().getInt("game.min_players", 2);
        this.maxPlayers = plugin.getConfig().getInt("game.max_players", 10);
        this.gameTime = plugin.getConfig().getInt("game.game_duration", 1800);
        this.restartDelay = plugin.getConfig().getInt("game.restart_delay", 30);
        loadConfig();
        initWaitingBar();
    }

    private void initWaitingBar() {
        waitingBar = Bukkit.createBossBar(
                plugin.getConfig().getString("messages.waiting", "§6Ожидание игроков: §e0/0")
                        .replace("{current}", "0")
                        .replace("{max}", String.valueOf(minPlayers)),
                BarColor.WHITE,
                BarStyle.SOLID
        );
    }

    private void loadConfig() {
        teamSpawns.put("Alpha", parseLocation(plugin.getConfig().getString("spawns.alpha", "world,100,64,0,0,0")));
        teamSpawns.put("Beta", parseLocation(plugin.getConfig().getString("spawns.beta", "world,-100,64,0,180,0")));
        teamSpawns.put("Lobby", parseLocation(plugin.getConfig().getString("spawns.lobby", "world,0,64,0,0,0")));

        teamMessages.put("Alpha", plugin.getConfig().getString("messages.alpha", "§9Вы в команде Альфа!"));
        teamMessages.put("Beta", plugin.getConfig().getString("messages.beta", "§cВы в команде Бета!"));
    }

    private Location parseLocation(String locStr) {
        String[] parts = locStr.split(",");
        return new Location(
                Bukkit.getWorld(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5])
        );
    }

    public void playerJoin(Player player) {
        if (players.size() >= maxPlayers) {
            player.kickPlayer("§cСервер заполнен! Максимум игроков: " + maxPlayers);
            return;
        }

        players.add(player);
        updateWaitingBar();

        if (players.size() >= minPlayers && !gameActive) {
            startCountdown();
        }
    }

    public void playerLeave(Player player) {
        players.remove(player);
        updateWaitingBar();

        if (players.size() < minPlayers && gameActive) {
            cancelGame();
        }
    }

    private void updateWaitingBar() {
        String message = plugin.getConfig().getString("messages.waiting", "§6Ожидание игроков: §e{current}/{max}")
                .replace("{current}", String.valueOf(players.size()))
                .replace("{max}", String.valueOf(minPlayers));

        waitingBar.setTitle(message);
        waitingBar.setProgress(Math.min(1.0, (double)players.size() / minPlayers));
        waitingBar.setColor(players.size() >= minPlayers ? BarColor.GREEN : BarColor.RED);
        Bukkit.getOnlinePlayers().forEach(waitingBar::addPlayer);
    }

    private void startCountdown() {
        gameActive = true;
        waitingBar.removeAll();
        countdownBar = Bukkit.createBossBar("§6До начала игры: §e" + countdown + "§6 сек", BarColor.YELLOW, BarStyle.SOLID);
        players.forEach(countdownBar::addPlayer);

        new BukkitRunnable() {
            @Override
            public void run() {
                countdown--;
                countdownBar.setTitle("§6До начала игры: §e" + countdown + "§6 сек");

                if (countdown <= 0) {
                    cancel();
                    startGame();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void cancelGame() {
        gameActive = false;
        if (countdownBar != null) countdownBar.removeAll();
        if (gameTimerBar != null) gameTimerBar.removeAll();
        cancelTasks();

        Bukkit.broadcastMessage(plugin.getConfig().getString("messages.game_cancel", "§cИгра отменена!"));
        updateWaitingBar();
    }

    private void startGame() {
        countdownBar.removeAll();
        setupTeams();
        teleportTeams();
        sendTeamMessages();
        startGameTimer();
        startVictoryCheck();
        startScoreboardUpdater();
    }

    private void setupTeams() {
        Collections.shuffle(players);

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            String teamName = i % 2 == 0 ? "Alpha" : "Beta";

            Team team = player.getScoreboard().getTeam(teamName);
            if (team == null) {
                team = player.getScoreboard().registerNewTeam(teamName);
            }

            team.setColor(teamName.equals("Alpha") ? ChatColor.BLUE : ChatColor.RED);
            team.addEntry(player.getName());
        }
    }

    private void teleportTeams() {
        players.forEach(player -> {
            String teamName = getPlayerTeam(player);
            Location spawn = teamSpawns.get(teamName);
            if (spawn != null) {
                player.teleport(spawn);
            }
        });
    }

    private void sendTeamMessages() {
        players.forEach(player -> {
            String teamName = getPlayerTeam(player);
            player.sendMessage(teamMessages.get(teamName));
        });
    }

    private String getPlayerTeam(Player player) {
        Team team = player.getScoreboard().getPlayerTeam(player);
        return team != null ? team.getName() : "";
    }

    private void startGameTimer() {
        gameTimerBar = Bukkit.createBossBar("§6Осталось времени: §e" + formatTime(gameTime), BarColor.GREEN, BarStyle.SEGMENTED_20);
        players.forEach(gameTimerBar::addPlayer);

        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                gameTime--;
                gameTimerBar.setTitle("§6Осталось времени: §e" + formatTime(gameTime));
                gameTimerBar.setProgress(gameTime / (double)plugin.getConfig().getInt("game.game_duration", 1800));

                if (gameTime <= 60) {
                    gameTimerBar.setColor(BarColor.RED);
                }

                if (gameTime <= 0) {
                    endGame(null);
                    cancel();
                }
            }
        };
        gameTimerTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startVictoryCheck() {
        victoryCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }

                if (checkTeamWin("Alpha")) {
                    endGame("Alpha");
                } else if (checkTeamWin("Beta")) {
                    endGame("Beta");
                }
            }
        };
        victoryCheckTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startScoreboardUpdater() {
        scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateScoreboard();
            }
        };
        scoreboardTask.runTaskTimer(plugin, 0L, 10L);
    }

    private void updateScoreboard() {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("capture", "dummy",
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("scoreboard.title", "§6§lТочки Захвата")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        if (lines.isEmpty()) {
            lines = Arrays.asList(
                    "§fВремя: §e{time}",
                    "§fТочки: §a{alpha_points} §f| §c{beta_points}",
                    "§fУбийств: §e{kills}",
                    "§fЛучший: §b{top_killer}"
            );
        }

        String topKiller = getTopKillerName();
        int topKills = getTopKills();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i)
                    .replace("{time}", formatTime(gameTime))
                    .replace("{alpha_points}", String.valueOf(alphaPoints))
                    .replace("{beta_points}", String.valueOf(betaPoints))
                    .replace("{kills}", "{player_kills}")
                    .replace("{top_killer}", topKiller)
                    .replace("{top_kills}", String.valueOf(topKills));

            // Персональная замена для каждого игрока
            for (Player player : players) {
                line = line.replace("{player_kills}",
                        String.valueOf(playerKills.getOrDefault(player.getUniqueId(), 0)));
            }

            obj.getScore(ChatColor.translateAlternateColorCodes('&', line)).setScore(lines.size() - i);
        }

        players.forEach(p -> p.setScoreboard(board));
    }

    private String getTopKillerName() {
        return playerKills.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> Bukkit.getOfflinePlayer(e.getKey()).getName())
                .orElse("Нет данных");
    }

    private int getTopKills() {
        return playerKills.values().stream().max(Integer::compare).orElse(0);
    }

    private boolean checkTeamWin(String team) {
        return plugin.getCapturePoints().values().stream()
                .allMatch(point -> point.getControllingTeam().equals(team) && !point.isOnCooldown());
    }

    private void endGame(String winningTeam) {
        gameActive = false;
        cancelTasks();

        String topKiller = getTopKillerName();
        int topKills = getTopKills();

        String message = plugin.getConfig().getString("messages.winner_announcement",
                        "§6Победила команда {team}! Лучший игрок: §e{top_killer}§6 (убийств: §e{top_kills}§6)")
                .replace("{team}", winningTeam != null ?
                        (winningTeam.equals("Alpha") ? "§9Альфа" : "§cБета") : "§fНет")
                .replace("{top_killer}", topKiller != null ? topKiller : "Нет")
                .replace("{top_kills}", String.valueOf(topKills));

        Bukkit.broadcastMessage(message);
        teleportToLobby();
        scheduleRestart();
    }

    private void cancelTasks() {
        if (gameTimerTask != null) gameTimerTask.cancel();
        if (victoryCheckTask != null) victoryCheckTask.cancel();
        if (scoreboardTask != null) scoreboardTask.cancel();
    }

    private void teleportToLobby() {
        Location lobby = teamSpawns.get("Lobby");
        if (lobby != null) {
            Bukkit.getOnlinePlayers().forEach(p -> p.teleport(lobby));
        }
    }

    private void scheduleRestart() {
        new BukkitRunnable() {
            int countdown = restartDelay;

            @Override
            public void run() {
                if (countdown <= 0) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                    cancel();
                    return;
                }

                String message = plugin.getConfig().getString("messages.restart",
                                "§6Рестарт сервера через §e{time}§6 секунд")
                        .replace("{time}", String.valueOf(countdown));

                Bukkit.broadcastMessage(message);
                countdown--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void onPlayerKill(Player killer) {
        playerKills.merge(killer.getUniqueId(), 1, Integer::sum);
    }

    public int getPlayerKills(Player player) {
        return playerKills.getOrDefault(player.getUniqueId(), 0);
    }

    public boolean isGameActive() {
        return gameActive;
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}