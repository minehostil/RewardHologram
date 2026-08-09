package dev.rewardhologram.manager;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.hologram.RealHologram;
import dev.rewardhologram.model.ActiveHologram;
import dev.rewardhologram.model.HologramData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public class HologramManager {

    private final RewardHologramPlugin plugin;
    private final org.bukkit.NamespacedKey ownerKey;
    private final org.bukkit.NamespacedKey hologramIdKey;
    private final Map<String, HologramData> hologramDefinitions = new LinkedHashMap<>();
    private final Map<UUID, Map<String, ActiveHologram>> activeHolograms = new HashMap<>();
    private final CooldownManager cooldownManager;

    public HologramManager(RewardHologramPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = plugin.getOwnerKey();
        this.hologramIdKey = plugin.getHologramIdKey();
        this.cooldownManager = new CooldownManager(plugin);
        loadConfig();
    }

    // ─── Config ────────────────────────────────────────────────────────────────

    public void loadConfig() {
        hologramDefinitions.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("holograms");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection h = section.getConfigurationSection(id);
            if (h == null) continue;

            hologramDefinitions.put(id, new HologramData(
                    id,
                    h.getDouble("chance", 25.0),
                    h.getInt("interval", 300),
                    h.getInt("despawn-after", 30),
                    h.getDouble("offset.x", 0),
                    h.getDouble("offset.y", 1.5),
                    h.getDouble("offset.z", 2.5),
                    h.getBoolean("bobbing.enabled", true),
                    h.getDouble("bobbing.amplitude", 0.15),
                    h.getDouble("bobbing.speed", 0.05),
                    h.getStringList("lines"),
                    parseRewards(h),
                    h.getInt("rewards.pick", -1),
                    h.getString("appear-message", ""),
                    h.getString("claim-message", ""),
                    h.getBoolean("title.enabled", false),
                    h.getString("title.title", ""),
                    h.getString("title.subtitle", ""),
                    h.getInt("title.fade-in", 10),
                    h.getInt("title.stay", 60),
                    h.getInt("title.fade-out", 20),
                    h.getBoolean("sound.enabled", false),
                    h.getString("sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                    (float) h.getDouble("sound.volume", 1.0),
                    (float) h.getDouble("sound.pitch", 1.0),
                    h.getBoolean("skull.enabled", false),
                    h.getString("skull.texture", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzgwNWE1ZjkzMTE2N2UxNjYxZTkzNjFkNzk5YzAyNmRkMDJkODJhYzNlNThlZDQxNThkOTlhYmUxMGQ0NiJ9fX0="),
                    (float) h.getDouble("skull.head-yaw", 0.0),
                    h.getDouble("skull.height-offset", 0.5),
                    h.getBoolean("skull.rotation.enabled", false),
                    (float) h.getDouble("skull.rotation.speed", 2.0),
                    h.getDouble("line-spacing", 0.28),
                    h.getBoolean("claim-sound.enabled", false),
                    h.getString("claim-sound.name", "ENTITY_PLAYER_LEVELUP"),
                    (float) h.getDouble("claim-sound.volume", 1.0),
                    (float) h.getDouble("claim-sound.pitch", 1.0),
                    parseClickType(h.getString("click-type", "RIGHT"))
            ));
        }

        plugin.getLogger().info("Holograms loaded: " + hologramDefinitions.size());
    }

    // Click cooldown: UUID -> (hologramId -> timestamp ms)
    private final Map<UUID, Map<String, Long>> clickCooldowns = new HashMap<>();

    public boolean isClickOnCooldown(UUID uuid, String hologramId) {
        long cooldownMs = plugin.getConfig().getLong("settings.click-cooldown-ms", 500L);
        long last = clickCooldowns
                .getOrDefault(uuid, Collections.emptyMap())
                .getOrDefault(hologramId, 0L);
        long now = System.currentTimeMillis();
        if (now - last < cooldownMs) return true;
        // Registrar el clic al mismo tiempo
        clickCooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(hologramId, now);
        return false;
    }

    // ─── Spawn ─────────────────────────────────────────────────────────────────

    public void trySpawn(Player player, HologramData data) {
        UUID uuid = player.getUniqueId();

        // Si ya hay uno activo con este ID, no hacer nada
        if (activeHolograms.getOrDefault(uuid, Collections.emptyMap()).containsKey(data.getId())) return;

        // Verificar intervalo ANTES de tocar hologramas activos
        long last = cooldownManager.getLastSpawn(uuid, data.getId());
        if (System.currentTimeMillis() - last < (long) data.getInterval() * 1000L) return;

        // Verificar chance ANTES de tocar hologramas activos
        if (Math.random() * 100 > data.getChance()) return;

        // Solo si pasa el intervalo y el chance, eliminar cualquier holograma activo previo
        Map<String, ActiveHologram> playerActives = activeHolograms.get(uuid);
        if (playerActives != null && !playerActives.isEmpty()) {
            for (Map.Entry<String, ActiveHologram> entry : new HashMap<>(playerActives).entrySet()) {
                HologramData existing = hologramDefinitions.get(entry.getKey());
                if (existing != null) new RealHologram(plugin, player, existing).destroy(entry.getValue());
            }
            playerActives.clear();
        }

        spawnHologram(player, data);
    }

    public void spawnHologram(Player player, HologramData data) {
        UUID uuid = player.getUniqueId();

        // Si ya hay uno activo para este holograma, eliminarlo antes de spawnear el nuevo
        Map<String, ActiveHologram> playerActives = activeHolograms.get(uuid);
        if (playerActives != null && playerActives.containsKey(data.getId())) {
            ActiveHologram existing = playerActives.remove(data.getId());
            new RealHologram(plugin, player, data).destroy(existing);
        }

        ActiveHologram active = new RealHologram(plugin, player, data).spawn();
        activeHolograms.computeIfAbsent(uuid, k -> new HashMap<>()).put(data.getId(), active);
        cooldownManager.setLastSpawn(uuid, data.getId(), System.currentTimeMillis());

        // Anclar los chunks donde están las entidades para que no se descarguen
        // mientras el holograma esté activo, evitando entidades fantasma
        active.getRealEntities().forEach(as -> {
            if (as != null && !as.isDead()) {
                as.getLocation().getChunk().addPluginChunkTicket(plugin);
            }
        });

        sendAppearEffects(player, data);

        // Guardar referencia al active para verificar en el despawn
        // que no fue reemplazado por uno más nuevo
        final ActiveHologram spawnedActive = active;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Solo despawnear si el active actual es el mismo que spawneamos
            // (evita eliminar un holograma nuevo que reemplazó a este)
            ActiveHologram current = activeHolograms
                    .getOrDefault(uuid, Collections.emptyMap())
                    .get(data.getId());
            if (current == spawnedActive) {
                if (player.isOnline()) {
                    removeHologram(player, data.getId());
                } else {
                    activeHolograms.getOrDefault(uuid, Collections.emptyMap()).remove(data.getId());
                }
            }
        }, (long) data.getDespawnAfter() * 20L);
    }

    // ─── Remove ────────────────────────────────────────────────────────────────

    public void removeHologram(Player player, String hologramId) {
        UUID uuid = player.getUniqueId();
        Map<String, ActiveHologram> playerActives = activeHolograms.get(uuid);
        if (playerActives == null) return;

        ActiveHologram active = playerActives.remove(hologramId);
        if (active == null) return;

        // Liberar chunk tickets antes de destruir
        active.getRealEntities().forEach(as -> {
            if (as != null && !as.isDead()) {
                as.getLocation().getChunk().removePluginChunkTicket(plugin);
            }
        });

        HologramData data = hologramDefinitions.get(hologramId);
        if (data != null) new RealHologram(plugin, player, data).destroy(active);
    }

    public void removeAllForPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, ActiveHologram> actives = activeHolograms.remove(uuid);
        if (actives != null) {
            actives.values().forEach(active ->
                    active.getRealEntities().forEach(as -> {
                        if (as != null && !as.isDead()) {
                            as.getLocation().getChunk().removePluginChunkTicket(plugin);
                            as.remove();
                        }
                    })
            );
        }
        clickCooldowns.remove(uuid);

        if (plugin.getConfig().getBoolean("settings.debug", false)) {
            plugin.getLogger().info("[Debug] Cleared holograms for " + player.getName());
        }
    }

    public void removeAll() {
        for (UUID uuid : new HashSet<>(activeHolograms.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeAllForPlayer(player);
            } else {
                Map<String, ActiveHologram> actives = activeHolograms.remove(uuid);
                if (actives != null) {
                    actives.values().forEach(active ->
                            active.getRealEntities().forEach(as -> {
                                if (as != null && !as.isDead()) {
                                    // Liberar chunk ticket antes de eliminar
                                    try {
                                        as.getLocation().getChunk()
                                                .removePluginChunkTicket(plugin);
                                    } catch (Exception ignored) {}
                                    as.remove();
                                }
                            })
                    );
                }
            }
        }
        activeHolograms.clear();
    }

    // ─── Bobbing ───────────────────────────────────────────────────────────────

    public void tickBobbing() {
        Iterator<Map.Entry<UUID, Map<String, ActiveHologram>>> playerIt =
                activeHolograms.entrySet().iterator();

        while (playerIt.hasNext()) {
            Map.Entry<UUID, Map<String, ActiveHologram>> playerEntry = playerIt.next();
            Player player = Bukkit.getPlayer(playerEntry.getKey());

            if (player == null || !player.isOnline()) {
                playerEntry.getValue().forEach((hId, active) ->
                        active.getRealEntities().forEach(as -> {
                            if (as != null && !as.isDead()) as.remove();
                        })
                );
                playerIt.remove();
                continue;
            }

            Iterator<Map.Entry<String, ActiveHologram>> holoIt =
                    playerEntry.getValue().entrySet().iterator();

            while (holoIt.hasNext()) {
                Map.Entry<String, ActiveHologram> holoEntry = holoIt.next();
                ActiveHologram active = holoEntry.getValue();
                HologramData data = hologramDefinitions.get(holoEntry.getKey());

                if (data == null) continue;

                boolean allDead = active.getRealEntities().stream()
                        .allMatch(as -> as == null || as.isDead());
                if (allDead) { holoIt.remove(); continue; }

                // Bobbing
                boolean needsUpdate = false;
                if (data.isBobbingEnabled()) {
                    active.incrementPhase(data.getBobbingSpeed());
                    needsUpdate = true;
                }

                // Rotación de cabeza
                if (data.isSkullEnabled() && data.isSkullRotationEnabled()) {
                    active.incrementYaw(data.getSkullRotationSpeed());
                    needsUpdate = true;
                }

                if (needsUpdate) {
                    double newY = active.getBaseY() +
                            (data.isBobbingEnabled()
                                    ? Math.sin(active.getBobbingPhase()) * data.getBobbingAmplitude()
                                    : 0);
                    new RealHologram(plugin, player, data).teleportEntities(active, newY);
                }
            }
        }
    }

    // ─── Comandos / Efectos ────────────────────────────────────────────────────

    public void executeCommands(Player player, HologramData data) {
        // Verificar cooldown de clic
        if (isClickOnCooldown(player.getUniqueId(), data.getId())) return;

        // Verificar que el holograma ya no esté activo (fue removido en claim() antes de llegar aquí)
        // Si por alguna razón sigue activo, no ejecutar
        if (activeHolograms
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .containsKey(data.getId())) return;

        List<dev.rewardhologram.model.RewardEntry> rewards = data.getRewards();
        int pick = data.getRewardPick();

        if (pick <= 0 || pick >= rewards.size()) {
            // Sin pick (o pick >= total): evaluar todas por su chance individual
            for (dev.rewardhologram.model.RewardEntry reward : rewards) {
                if (reward.roll()) {
                    String parsed = reward.getCommand().replace("%player%", player.getName());
                    Bukkit.getScheduler().runTask(plugin,
                            () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed));
                }
            }
        } else {
            // Con pick: mezclar la lista, tomar las primeras N y evaluarlas por su chance
            List<dev.rewardhologram.model.RewardEntry> shuffled = new ArrayList<>(rewards);
            java.util.Collections.shuffle(shuffled);
            shuffled.stream().limit(pick).forEach(reward -> {
                if (reward.roll()) {
                    String parsed = reward.getCommand().replace("%player%", player.getName());
                    Bukkit.getScheduler().runTask(plugin,
                            () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed));
                }
            });
        }

        sendClaimMessage(player, data);
    }

    private void sendAppearEffects(Player player, HologramData data) {
        String msg = data.getAppearMessage();
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(dev.rewardhologram.util.ColorUtil.colorMessage(
                    msg.replace("%player%", player.getName())));
        }

        if (data.isTitleEnabled()) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    dev.rewardhologram.util.ColorUtil.color(data.getTitleText()),
                    dev.rewardhologram.util.ColorUtil.color(data.getTitleSubtitle()),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(data.getTitleFadeIn() * 50L),
                            java.time.Duration.ofMillis(data.getTitleStay() * 50L),
                            java.time.Duration.ofMillis(data.getTitleFadeOut() * 50L)
                    )
            ));
        }

        if (data.isSoundEnabled()) {
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(data.getSoundName());
                player.playSound(player.getLocation(), sound,
                        data.getSoundVolume(), data.getSoundPitch());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound in hologram '"
                        + data.getId() + "': " + data.getSoundName());
            }
        }
    }

    private void sendClaimMessage(Player player, HologramData data) {
        String msg = data.getClaimMessage();
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(dev.rewardhologram.util.ColorUtil.colorMessage(
                    msg.replace("%player%", player.getName())));
        }

        if (data.isClaimSoundEnabled()) {
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(data.getClaimSoundName());
                player.playSound(player.getLocation(), sound,
                        data.getClaimSoundVolume(), data.getClaimSoundPitch());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid claim sound in hologram '"
                        + data.getId() + "': " + data.getClaimSoundName());
            }
        }
    }

    // ─── Getters ───────────────────────────────────────────────────────────────

    public Collection<HologramData> getAllDefinitions() { return hologramDefinitions.values(); }
    public HologramData getDefinition(String id) { return hologramDefinitions.get(id); }
    public CooldownManager getCooldownManager() { return cooldownManager; }

    /**
     * Elimina todos los armor stands del plugin dentro del radio dado.
     * Busca por PDC key directamente en el mundo, así atrapa también
     * entidades huérfanas que no estén en el mapa activeHolograms.
     * @return cantidad de armor stands eliminados
     */
    public int removeNear(org.bukkit.Location center, double radius) {
        int removed = 0;
        double radiusSq = radius * radius;

        for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof org.bukkit.entity.ArmorStand as)) continue;
            if (entity.getLocation().distanceSquared(center) > radiusSq) continue;

            String ownerStr = as.getPersistentDataContainer()
                    .get(ownerKey, org.bukkit.persistence.PersistentDataType.STRING);
            String hologramId = as.getPersistentDataContainer()
                    .get(hologramIdKey, org.bukkit.persistence.PersistentDataType.STRING);

            if (ownerStr == null || hologramId == null) continue;

            // Eliminar del mapa activo si existe
            try {
                UUID ownerUuid = UUID.fromString(ownerStr);
                Map<String, ActiveHologram> playerActives = activeHolograms.get(ownerUuid);
                if (playerActives != null) playerActives.remove(hologramId);
            } catch (IllegalArgumentException ignored) {}

            as.remove();
            removed++;
        }

        return removed;
    }

    /**
     * Devuelve una lista de info de hologramas activos:
     * ownerName, hologramId, y la ubicación del primer armor stand.
     */
    public List<ActiveHologramInfo> getActiveList() {
        List<ActiveHologramInfo> result = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, ActiveHologram>> playerEntry : activeHolograms.entrySet()) {
            String ownerName = Bukkit.getOfflinePlayer(playerEntry.getKey()).getName();
            if (ownerName == null) ownerName = playerEntry.getKey().toString().substring(0, 8);
            for (Map.Entry<String, ActiveHologram> holoEntry : playerEntry.getValue().entrySet()) {
                ActiveHologram active = holoEntry.getValue();
                org.bukkit.Location loc = null;
                if (!active.getRealEntities().isEmpty()) {
                    org.bukkit.entity.ArmorStand as = active.getRealEntities().get(0);
                    if (as != null && !as.isDead()) loc = as.getLocation();
                }
                result.add(new ActiveHologramInfo(ownerName, holoEntry.getKey(), loc));
            }
        }
        return result;
    }

    public record ActiveHologramInfo(String ownerName, String hologramId, org.bukkit.Location location) {}
        try {
            return HologramData.ClickType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid click-type '" + value + "', using RIGHT.");
            return HologramData.ClickType.RIGHT;
        }
    }

    /**
     * Lee la sección rewards del config.
     * Soporta dos formatos:
     *
     * Formato nuevo (con chance individual):
     *   rewards:
     *     - command: "give %player% diamond 1"
     *       chance: 50.0
     *     - command: "give %player% gold_ingot 3"
     *       chance: 100.0
     *
     * Formato legacy (lista simple, chance 100%):
     *   commands:
     *     - "give %player% diamond 1"
     */
    private java.util.List<dev.rewardhologram.model.RewardEntry> parseRewards(
            org.bukkit.configuration.ConfigurationSection h) {

        java.util.List<dev.rewardhologram.model.RewardEntry> list = new java.util.ArrayList<>();

        // Formato nuevo con pick — rewards es una sección con pick y list:
        //   rewards:
        //     pick: 2
        //     list:
        //       - command: "..."
        //         chance: 50.0
        String listKey = h.isList("rewards.list") ? "rewards.list" : "rewards";

        if (h.isList(listKey)) {
            java.util.List<java.util.Map<?, ?>> rawList = h.getMapList(listKey);
            for (java.util.Map<?, ?> map : rawList) {
                Object cmdObj = map.get("command");
                if (cmdObj == null) continue;
                String cmd = cmdObj.toString();
                if (cmd.isEmpty()) continue;

                double chance = 100.0;
                Object chanceObj = map.get("chance");
                if (chanceObj instanceof Number) {
                    chance = ((Number) chanceObj).doubleValue();
                }
                list.add(new dev.rewardhologram.model.RewardEntry(cmd, chance));
            }
        }

        // Formato legacy — lista commands (chance 100%)
        if (list.isEmpty()) {
            for (String cmd : h.getStringList("commands")) {
                list.add(new dev.rewardhologram.model.RewardEntry(cmd, 100.0));
            }
        }

        return list;
    }
}