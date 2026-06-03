package dev.rewardhologram.task;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.HologramData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Dos tareas separadas:
 *
 * 1. BukkitRunnable SÍNCRONO — spawn evaluation cada segundo (hilo principal,
 *    necesario para spawnear entidades y enviar packets a jugadores).
 *
 * 2. BukkitRunnable SÍNCRONO — bobbing cada tick (hilo principal, necesario
 *    para teleportar entidades). Los packets de teleport son muy ligeros
 *    (~20 bytes cada uno), el impacto real en TPS es mínimo incluso con
 *    20 jugadores activos.
 *
 * Separar las tareas permite que el bobbing corra cada tick sin evaluar
 * el spawn (operación más costosa) en cada tick.
 */
public class HologramTask {

    private final RewardHologramPlugin plugin;

    public HologramTask(RewardHologramPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Tarea 1: Bobbing — cada tick (fluido, ligero)
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getHologramManager().tickBobbing();
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // Tarea 2: Evaluación de spawn — cada 20 ticks (1 segundo, más costoso)
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