package dev.rewardhologram.manager;

import dev.rewardhologram.RewardHologramPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persiste los tiempos de último spawn en cooldowns.yml.
 * Así los intervalos se respetan aunque el servidor se reinicie.
 *
 * Formato en cooldowns.yml:
 *   uuid-del-jugador:
 *     daily_reward: 1715000000000   <- timestamp en ms
 *     bonus_reward: 1714900000000
 */
public class CooldownManager {

    private final RewardHologramPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    // Cache en memoria para no leer disco en cada tick
    // UUID -> (hologramId -> timestamp ms)
    private final Map<UUID, Map<String, Long>> cache = new HashMap<>();

    public CooldownManager(RewardHologramPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cooldowns.yml");
        load();
    }

    // ─── Lectura ───────────────────────────────────────────────────────────────

    private void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try { file.createNewFile(); } catch (IOException e) {
                plugin.getLogger().warning("Could not create cooldowns.yml: " + e.getMessage());
            }
        }

        yaml = YamlConfiguration.loadConfiguration(file);
        cache.clear();

        for (String uuidStr : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Long> times = new HashMap<>();

                org.bukkit.configuration.ConfigurationSection section =
                        yaml.getConfigurationSection(uuidStr);
                if (section != null) {
                    for (String holoId : section.getKeys(false)) {
                        times.put(holoId, section.getLong(holoId));
                    }
                }
                cache.put(uuid, times);
            } catch (IllegalArgumentException ignored) {
                // Invalid UUID in file, skipping
            }
        }

        if (plugin.getConfig().getBoolean("settings.debug", false)) {
            plugin.getLogger().info("[Debug] Cooldowns loaded for " + cache.size() + " players.");
        }
    }

    // ─── Acceso ────────────────────────────────────────────────────────────────

    /**
     * Devuelve true si el jugador nunca ha tenido cooldowns registrados.
     */
    public boolean isNewPlayer(UUID uuid) {
        return !cache.containsKey(uuid);
    }

    /**
     * Registra el tiempo actual para todos los hologramas de un jugador nuevo.
     * Así el intervalo completo debe pasar antes de que aparezca el primer holograma,
     * evitando que al unirse por primera vez aparezcan todos a la vez.
     */
    public void initNewPlayer(UUID uuid, java.util.Collection<String> hologramIds) {
        if (!isNewPlayer(uuid)) return;
        long now = System.currentTimeMillis();
        hologramIds.forEach(id -> setLastSpawn(uuid, id, now));
    }
    public long getLastSpawn(UUID uuid, String hologramId) {
        return cache
                .getOrDefault(uuid, new HashMap<>())
                .getOrDefault(hologramId, 0L);
    }

    /**
     * Registra el spawn actual y guarda en disco de forma asíncrona.
     */
    public void setLastSpawn(UUID uuid, String hologramId, long timestamp) {
        cache.computeIfAbsent(uuid, k -> new HashMap<>()).put(hologramId, timestamp);
        saveAsync();
    }

    /**
     * Elimina los cooldowns de un jugador (por ejemplo, al reclamar manualmente).
     */
    public void clearPlayer(UUID uuid) {
        cache.remove(uuid);
        yaml.set(uuid.toString(), null);
        saveAsync();
    }

    // ─── Persistencia ──────────────────────────────────────────────────────────

    /**
     * Guarda todo el cache en cooldowns.yml de forma asíncrona para no bloquear el hilo principal.
     */
    private void saveAsync() {
        Map<UUID, Map<String, Long>> snapshot = new HashMap<>();
        cache.forEach((uuid, map) -> snapshot.put(uuid, new HashMap<>(map)));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration out = new YamlConfiguration();
            snapshot.forEach((uuid, map) ->
                    map.forEach((holoId, ts) ->
                            out.set(uuid.toString() + "." + holoId, ts)));
            try {
                out.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Error saving cooldowns.yml: " + e.getMessage());
            }
        });
    }

    /**
     * Guardado síncrono — usar solo en onDisable() para asegurar que se persiste.
     */
    public void saveSync() {
        YamlConfiguration out = new YamlConfiguration();
        cache.forEach((uuid, map) ->
                map.forEach((holoId, ts) ->
                        out.set(uuid.toString() + "." + holoId, ts)));
        try {
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Error saving cooldowns.yml: " + e.getMessage());
        }
    }
}