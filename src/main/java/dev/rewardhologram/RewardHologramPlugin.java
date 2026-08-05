package dev.rewardhologram;

import dev.rewardhologram.listener.HologramListener;
import dev.rewardhologram.manager.HologramManager;
import dev.rewardhologram.task.HologramTask;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RewardHologramPlugin extends JavaPlugin implements TabCompleter {

    private HologramManager hologramManager;
    private NamespacedKey ownerKey;
    private NamespacedKey hologramIdKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ownerKey      = new NamespacedKey(this, "hologram_owner");
        hologramIdKey = new NamespacedKey(this, "hologram_id");

        cleanOrphanedArmorStands();

        hologramManager = new HologramManager(this);

        getServer().getPluginManager().registerEvents(new HologramListener(this), this);

        new HologramTask(this).start();

        // Registrar tab completer
        if (getCommand("rewardhologram") != null)
            getCommand("rewardhologram").setTabCompleter(this);

        getLogger().info("RewardHologram v" + getDescription().getVersion() + " enabled.");
        getLogger().info("Holograms loaded: " + hologramManager.getAllDefinitions().size());
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) {
            hologramManager.removeAll();
            hologramManager.getCooldownManager().saveSync();
        }
        getLogger().info("RewardHologram disabled.");
    }

    // ─── Limpieza post-reinicio ────────────────────────────────────────────────

    private void cleanOrphanedArmorStands() {
        int removed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof ArmorStand as)) continue;
                String owner = as.getPersistentDataContainer()
                        .get(ownerKey, PersistentDataType.STRING);
                if (owner != null) { as.remove(); removed++; }
            }
        }
        if (removed > 0)
            getLogger().info("Removed " + removed + " orphaned armor stand(s) from previous session.");
    }

    // ─── Comandos ─────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("rewardhologram")) return false;
        if (!sender.hasPermission("rewardhologram.admin")) {
            sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cYou don't have permission to do this."));
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                hologramManager.removeAll();
                reloadConfig();
                hologramManager.loadConfig();
                sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&aRewardHologram reloaded successfully."));
            }
            case "list" -> {
                sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&6Defined holograms:"));
                hologramManager.getAllDefinitions().forEach(d ->
                        sender.sendMessage(dev.rewardhologram.util.ColorUtil.color(
                                "&7- &e" + d.getId() + " &7(chance: &a" + d.getChance()
                                + "%&7, interval: &a" + d.getInterval() + "s&7)")));
            }
            case "spawn" -> {
                if (args.length < 3) { sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cUsage: /rh spawn <id> <player>")); return true; }
                dev.rewardhologram.model.HologramData data = hologramManager.getDefinition(args[1]);
                if (data == null) { sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cHologram &e'" + args[1] + "' &cdoes not exist. Use &e/rh list &cto see available ones.")); return true; }
                org.bukkit.entity.Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cPlayer &e'" + args[2] + "' &cis not online.")); return true; }
                hologramManager.spawnHologram(target, data);
                sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&aHologram &e'" + args[1] + "' &aspawned for &e" + target.getName() + "&a."));
            }
            case "remove" -> {
                if (args.length < 3) { sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cUsage: /rh remove <id> <player>")); return true; }
                if (hologramManager.getDefinition(args[1]) == null) { sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cHologram &e'" + args[1] + "' &cdoes not exist. Use &e/rh list &cto see available ones.")); return true; }
                org.bukkit.entity.Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cPlayer &e'" + args[2] + "' &cis not online.")); return true; }
                hologramManager.removeHologram(target, args[1]);
                sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&aHologram &e'" + args[1] + "' &aremoved from &e" + target.getName() + "&a."));
            }
            case "removenear" -> {
                // Solo jugadores pueden usar este comando (necesita posición)
                if (!(sender instanceof org.bukkit.entity.Player admin)) {
                    sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cThis command can only be used by players."));
                    return true;
                }

                // Radio opcional — default 5 bloques
                double radius = 5.0;
                if (args.length >= 2) {
                    try {
                        radius = Double.parseDouble(args[1]);
                        if (radius <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&cInvalid radius. Usage: /rh removenear [radius]"));
                        return true;
                    }
                }

                int removed = hologramManager.removeNear(admin.getLocation(), radius);
                if (removed == 0) {
                    sender.sendMessage(dev.rewardhologram.util.ColorUtil.color(
                            "&eNo hologram armor stands found within &a" + radius + " &eblocks."));
                } else {
                    sender.sendMessage(dev.rewardhologram.util.ColorUtil.color(
                            "&aRemoved &e" + removed + " &ahologram armor stand(s) within &e" + radius + " &ablocks."));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&6=== RewardHologram ==="));
        sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&e/rh reload                   &7- Reload the configuration"));
        sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&e/rh list                     &7- List all defined holograms"));
        sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&e/rh spawn <id> <player>      &7- Force spawn a hologram for a player"));
        sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&e/rh remove <id> <player>     &7- Remove a player's active hologram"));
        sender.sendMessage(dev.rewardhologram.util.ColorUtil.color("&e/rh removenear [radius]      &7- Remove all hologram stands within radius (default 5)"));
    }

    // ─── Tab Completer ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("rewardhologram.admin")) return List.of();

        List<String> completions = new ArrayList<>();
        String current = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            // Primer argumento — subcomandos
            List<String> subcommands = Arrays.asList(
                    "reload", "list", "spawn", "remove", "removenear");
            return filter(subcommands, current);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "spawn", "remove" -> {
                    // Segundo argumento — IDs de hologramas
                    List<String> ids = hologramManager.getAllDefinitions()
                            .stream()
                            .map(dev.rewardhologram.model.HologramData::getId)
                            .collect(Collectors.toList());
                    return filter(ids, current);
                }
                case "removenear" -> {
                    // Segundo argumento — radio sugerido
                    return filter(Arrays.asList("3", "5", "10", "15", "20"), current);
                }
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "spawn", "remove" -> {
                    // Tercer argumento — jugadores online
                    List<String> players = Bukkit.getOnlinePlayers()
                            .stream()
                            .map(Player::getName)
                            .collect(Collectors.toList());
                    return filter(players, current);
                }
            }
        }

        return completions;
    }

    /** Filtra una lista de opciones según el texto que el jugador lleva escrito. */
    private List<String> filter(List<String> options, String current) {
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
    }

    // ─── Getters ──────────────────────────────────────────────────────────────
    public HologramManager getHologramManager() { return hologramManager; }
    public NamespacedKey getOwnerKey()           { return ownerKey; }
    public NamespacedKey getHologramIdKey()      { return hologramIdKey; }
}