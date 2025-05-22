package org.showlee.capturePlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CaptureCommand implements CommandExecutor {
    private final CapturePlugin plugin;

    public CaptureCommand(CapturePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Показываем информацию о всех точках
            plugin.getCapturePoints().values().forEach(point -> {
                player.sendMessage("§6Точка: §e" + point.getName());
                player.sendMessage("§aТип: §f" + point.getType());
                player.sendMessage("§aКонтроль: §f" + point.getControllingTeam());
                player.sendMessage("§aПрогресс: §f" + point.getProgress() + "%");
                player.sendMessage(""); // Пустая строка для разделения
            });
            return true;
        }

        // Дополнительные подкоманды можно добавить здесь

        return false;
    }
}