package dev.rewardhologram.task;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.HologramData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 1. Tarea cada tick — animaciones (delega en cada Hologram:
 *    armor stands se teletransportan, displays interpolan transforms).
 * 2. Tarea cada 20 ticks — evaluación de spawn (interval + chance).
 */
public class HologramTask {

    private final RewardHologramPlugin plugin;

    public HologramTask(RewardHologramPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Tarea 1: Animaciones — cada tick
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getHologramManager().tickHolograms();
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // Tarea 2: Evaluación de spawn — cada 20 ticks (1 segundo)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (HologramData data : plugin.getHologramManager().getAllDefinitions()) {
                        plugin.getHologramManager().trySpawn(player, data);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}