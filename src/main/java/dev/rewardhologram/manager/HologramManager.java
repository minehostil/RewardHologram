package dev.rewardhologram.manager;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.hologram.DisplayHologram;
import dev.rewardhologram.hologram.Hologram;
import dev.rewardhologram.hologram.HologramUtil;
import dev.rewardhologram.hologram.RealHologram;
import dev.rewardhologram.model.ActiveHologram;
import dev.rewardhologram.model.ActiveHologramInfo;
import dev.rewardhologram.model.HologramData;
import dev.rewardhologram.model.RewardEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HologramManager {

    private final RewardHologramPlugin plugin;
    private final org.bukkit.NamespacedKey ownerKey;
    private final org.bukkit.NamespacedKey hologramIdKey;
    private final Map<String, HologramData> hologramDefinitions = new LinkedHashMap<>();
    private final Map<UUID, Map<String, ActiveHologram>> activeHolograms = new HashMap<>();
    private final CooldownManager cooldownManager;
    private final HologramData.Renderer defaultRenderer;

    public HologramManager(RewardHologramPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = plugin.getOwnerKey();
        this.hologramIdKey = plugin.getHologramIdKey();
        this.cooldownManager = new CooldownManager(plugin);
        this.defaultRenderer = parseRenderer(
                plugin.getConfig().getString("settings.renderer", "ARMOR_STAND"));
        loadConfig();
    }

    // ─── Factory ───────────────────────────────────────────────────────────────

    /** Crea el holograma según el renderer configurado (global o por holograma). */
    public Hologram createHologram(Player player, HologramData data) {
        HologramData.Renderer renderer = data.getRenderer() == HologramData.Renderer.DEFAULT
                ? defaultRenderer
                : data.getRenderer();
        return renderer == HologramData.Renderer.DISPLAY
                ? new DisplayHologram(plugin, player, data)
                : new RealHologram(plugin, player, data);
    }

    // ─── Config ────────────────────────────────────────────────────────────────

    public void loadConfig() {
        hologramDefinitions.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("holograms");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection h = section.getConfigurationSection(id);
            if (h == null) continue;

            // Lines soporta dos formatos mezclables:
            //   - "texto simple"                    (escala 1.0)
            //   - { text: "...", scale: 1.3 }       (escala personalizada, DISPLAY)
            List<String> lines = new ArrayList<>();
            List<Double> lineScales = new ArrayList<>();
            parseLines(h, lines, lineScales);

            hologramDefinitions.put(id, HologramData.builder(id)
                    .chance(h.getDouble("chance", 25.0))
                    .interval(h.getInt("interval", 300))
                    .despawnAfter(h.getInt("despawn-after", 30))
                    .offset(h.getDouble("offset.x", 0),
                            h.getDouble("offset.y", 1.5),
                            h.getDouble("offset.z", 2.5))
                    .bobbing(h.getBoolean("bobbing.enabled", true),
                            h.getDouble("bobbing.amplitude", 0.15),
                            h.getDouble("bobbing.speed", 0.05))
                    .lines(lines, lineScales)
                    .rewards(parseRewards(h), h.getInt("rewards.pick", -1))
                    .appearMessage(h.getString("appear-message", ""))
                    .claimMessage(h.getString("claim-message", ""))
                    .title(h.getBoolean("title.enabled", false),
                            h.getString("title.title", ""),
                            h.getString("title.subtitle", ""),
                            h.getInt("title.fade-in", 10),
                            h.getInt("title.stay", 60),
                            h.getInt("title.fade-out", 20))
                    .appearSound(h.getBoolean("sound.enabled", false),
                            h.getString("sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                            (float) h.getDouble("sound.volume", 1.0),
                            (float) h.getDouble("sound.pitch", 1.0))
                    .skull(h.getBoolean("skull.enabled", false),
                            h.getString("skull.texture", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzgwNWE1ZjkzMTE2N2UxNjYxZTkzNjFkNzk5YzAyNmRkMDJkODJhYzNlNThlZDQxNThkOTlhYmUxMGQ0NiJ9fX0="),
                            (float) h.getDouble("skull.head-yaw", 0.0),
                            h.getDouble("skull.height-offset", 0.5),
                            h.getBoolean("skull.rotation.enabled", false),
                            (float) h.getDouble("skull.rotation.speed", 2.0))
                    .skullDisplay(h.getDouble("skull.scale", 1.0))
                    .skullEffects(
                            h.getBoolean("skull.effects.squash.enabled", false),
                            h.getDouble("skull.effects.squash.amplitude", 0.12),
                            h.getDouble("skull.effects.squash.speed", 0.15),
                            h.getBoolean("skull.effects.tilt.enabled", false),
                            h.getDouble("skull.effects.tilt.amplitude", 8.0),
                            h.getDouble("skull.effects.tilt.speed", 0.06))
                    .lineSpacing(h.getDouble("line-spacing", 0.28))
                    .renderer(parseRenderer(h.getString("renderer", "DEFAULT")))
                    .display(
                            parseBillboard(h.getString("display.billboard", "CENTER")),
                            h.getDouble("display.scale", 1.0),
                            h.getBoolean("display.glow.enabled", false),
                            parseChatColor(h.getString("display.glow.color", "WHITE")),
                            h.getBoolean("display.text.shadowed", false),
                            h.getBoolean("display.text.see-through", false),
                            h.getString("display.text.background", "TRANSPARENT")
                                    .equalsIgnoreCase("TRANSPARENT"))
                    .appear(parseSpawnAnimation(h.getString("appear.animation", "NONE")),
                            h.getInt("appear.duration-ticks", 6))
                    .appearParticles(
                            h.getBoolean("appear.particles.enabled", false),
                            h.getString("appear.particles.type", "TOTEM_OF_UNDYING"),
                            h.getInt("appear.particles.count", 25),
                            h.getDouble("appear.particles.spread", 0.5),
                            h.getDouble("appear.particles.speed", 0.1))
                    .despawn(parseSpawnAnimation(h.getString("despawn.animation", "NONE")),
                            h.getInt("despawn.duration-ticks", 6))
                    .despawnParticles(
                            h.getBoolean("despawn.particles.enabled", false),
                            h.getString("despawn.particles.type", "CLOUD"),
                            h.getInt("despawn.particles.count", 15),
                            h.getDouble("despawn.particles.spread", 0.4),
                            h.getDouble("despawn.particles.speed", 0.05))
                    .claimSound(h.getBoolean("claim-sound.enabled", false),
                            h.getString("claim-sound.name", "ENTITY_PLAYER_LEVELUP"),
                            (float) h.getDouble("claim-sound.volume", 1.0),
                            (float) h.getDouble("claim-sound.pitch", 1.0))
                    .clickType(parseClickType(h.getString("click-type", "RIGHT")))
                    .build());
        }

        plugin.getLogger().info("Holograms loaded: " + hologramDefinitions.size()
                + " (renderer: " + defaultRenderer + ")");
    }

    // Click cooldown: UUID -> (hologramId -> timestamp ms)
    private final Map<UUID, Map<String, Long>> clickCooldowns = new HashMap<>();

    /**
     * Solo verifica — SIN efectos secundarios (antes registraba el clic aquí,
     * lo que podía hacer que executeCommands rechazara un clic válido).
     */
    public boolean isClickOnCooldown(UUID uuid, String hologramId) {
        long cooldownMs = plugin.getConfig().getLong("settings.click-cooldown-ms", 500L);
        long last = clickCooldowns
                .getOrDefault(uuid, Collections.emptyMap())
                .getOrDefault(hologramId, 0L);
        return System.currentTimeMillis() - last < cooldownMs;
    }

    private void recordClick(UUID uuid, String hologramId) {
        clickCooldowns.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(hologramId, System.currentTimeMillis());
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
                ActiveHologram removed = playerActives.remove(entry.getKey());
                if (removed == null) continue;
                List<Entity> oldEntities = new ArrayList<>(removed.getEntities());
                createHologram(player, data).destroy(removed);
                releaseTicketsFor(oldEntities);
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
            List<Entity> oldEntities = new ArrayList<>(existing.getEntities());
            createHologram(player, data).destroy(existing);
            releaseTicketsFor(oldEntities);
        }

        Hologram hologram = createHologram(player, data);
        ActiveHologram active = hologram.spawn();
        activeHolograms.computeIfAbsent(uuid, k -> new HashMap<>()).put(data.getId(), active);
        cooldownManager.setLastSpawn(uuid, data.getId(), System.currentTimeMillis());

        // Anclar los chunks donde están las entidades para que no se descarguen
        addTickets(active.getEntities());

        if (data.isAppearParticlesEnabled()) spawnHoloParticles(active, data, true);

        sendAppearEffects(player, data);

        // Guardar referencia al active para verificar en el despawn
        // que no fue reemplazado por uno más nuevo
        final ActiveHologram spawnedActive = active;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ActiveHologram current = activeHolograms
                    .getOrDefault(uuid, Collections.emptyMap())
                    .get(data.getId());
            if (current == spawnedActive) {
                if (player.isOnline()) {
                    removeHologram(player, data.getId());
                } else {
                    ActiveHologram removed = activeHolograms
                            .getOrDefault(uuid, Collections.emptyMap())
                            .remove(data.getId());
                    if (removed != null) {
                        List<Entity> entities = new ArrayList<>(removed.getEntities());
                        entities.forEach(HologramUtil::removeEntitySafely);
                        releaseTicketsFor(entities);
                    }
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

        List<Entity> entities = new ArrayList<>(active.getEntities());
        HologramData data = hologramDefinitions.get(hologramId);

        if (data != null) {
            spawnHoloParticles(active, data, false);
            Hologram hologram = createHologram(player, data);
            if (active.getType() == ActiveHologram.Type.DISPLAY
                    && data.getDespawnAnimation() == HologramData.SpawnAnimation.POP) {
                hologram.destroySmooth(active);
            } else {
                hologram.destroy(active);
            }
        } else {
            entities.forEach(HologramUtil::removeEntitySafely);
        }

        releaseTicketsFor(entities);
    }

    public void removeAllForPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, ActiveHologram> actives = activeHolograms.remove(uuid);
        if (actives != null) {
            actives.values().forEach(active -> {
                List<Entity> entities = new ArrayList<>(active.getEntities());
                // Misma lógica de destrucción segura que destroy() con fallback por UUID
                entities.forEach(HologramUtil::removeEntitySafely);
                releaseTicketsFor(entities);
            });
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
                if (actives == null) continue;
                actives.values().forEach(active -> {
                    List<Entity> entities = new ArrayList<>(active.getEntities());
                    entities.forEach(HologramUtil::removeEntitySafely);
                    releaseTicketsFor(entities);
                });
            }
        }
        activeHolograms.clear();
    }

    // ─── Chunk tickets (con conteo para no liberar chunks aún en uso) ──────────

    private void addTickets(List<Entity> entities) {
        for (Entity e : entities) {
            if (e == null || e.isDead()) continue;
            try {
                e.getLocation().getChunk().addPluginChunkTicket(plugin);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Libera los tickets de los chunks de las entidades eliminadas,
     * PERO solo si ningún otro holograma activo sigue usando esos chunks.
     * Antes se liberaban a ciegas — si dos hologramas compartían chunk,
     * el chunk podía descargarse dejando entidades fantasma.
     */
    private void releaseTicketsFor(Collection<Entity> removedEntities) {
        Set<Chunk> chunksToRelease = new HashSet<>();
        for (Entity e : removedEntities) {
            if (e == null) continue;
            try {
                chunksToRelease.add(e.getLocation().getChunk());
            } catch (Exception ignored) {}
        }
        if (chunksToRelease.isEmpty()) return;

        Set<Chunk> stillUsed = new HashSet<>();
        for (Map<String, ActiveHologram> map : activeHolograms.values()) {
            for (ActiveHologram a : map.values()) {
                for (Entity e : a.getEntities()) {
                    if (e == null || e.isDead()) continue;
                    try {
                        stillUsed.add(e.getLocation().getChunk());
                    } catch (Exception ignored) {}
                }
            }
        }

        for (Chunk chunk : chunksToRelease) {
            if (!stillUsed.contains(chunk)) {
                try {
                    chunk.removePluginChunkTicket(plugin);
                } catch (Exception ignored) {}
            }
        }
    }

    // ─── Tick de animaciones ───────────────────────────────────────────────────

    /**
     * Llamado cada tick. Limpia jugadores offline y delega las animaciones
     * en cada implementación de Hologram (armor stand o display).
     */
    public void tickHolograms() {
        Iterator<Map.Entry<UUID, Map<String, ActiveHologram>>> playerIt =
                activeHolograms.entrySet().iterator();

        while (playerIt.hasNext()) {
            Map.Entry<UUID, Map<String, ActiveHologram>> playerEntry = playerIt.next();
            Player player = Bukkit.getPlayer(playerEntry.getKey());

            if (player == null || !player.isOnline()) {
                playerEntry.getValue().values().forEach(active -> {
                    List<Entity> entities = new ArrayList<>(active.getEntities());
                    entities.forEach(HologramUtil::removeEntitySafely);
                    releaseTicketsFor(entities);
                });
                playerIt.remove();
                continue;
            }

            Iterator<Map.Entry<String, ActiveHologram>> holoIt =
                    playerEntry.getValue().entrySet().iterator();

            while (holoIt.hasNext()) {
                Map.Entry<String, ActiveHologram> holoEntry = holoIt.next();
                ActiveHologram active = holoEntry.getValue();
                HologramData data = hologramDefinitions.get(holoEntry.getKey());

                boolean allDead = active.getEntities().stream()
                        .allMatch(e -> e == null || e.isDead());
                if (allDead) { holoIt.remove(); continue; }

                if (data == null) continue;

                createHologram(player, data).tick(active);
            }
        }
    }

    // ─── Partículas ────────────────────────────────────────────────────────────

    private void spawnHoloParticles(ActiveHologram active, HologramData data, boolean appear) {
        boolean enabled = appear ? data.isAppearParticlesEnabled() : data.isDespawnParticlesEnabled();
        if (!enabled) return;

        String typeName = appear ? data.getAppearParticleType() : data.getDespawnParticleType();
        int count = appear ? data.getAppearParticleCount() : data.getDespawnParticleCount();
        double spread = appear ? data.getAppearParticleSpread() : data.getDespawnParticleSpread();
        double speed = appear ? data.getAppearParticleSpeed() : data.getDespawnParticleSpeed();

        try {
            Particle particle = Particle.valueOf(typeName.toUpperCase());
            Location center = active.getBaseLocation().clone();
            int totalLines = Math.max(1, data.getLines().size());
            center.add(0, ((totalLines - 1) * data.getLineSpacing()) / 2.0, 0);
            center.getWorld().spawnParticle(particle, center, count, spread, spread, spread, speed);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle type '" + typeName
                    + "' in hologram '" + data.getId()
                    + "'. Use particle names that don't require extra data (avoid DUST, ITEM, BLOCK...).");
        }
    }

    // ─── Comandos / Efectos ────────────────────────────────────────────────────

    public void executeCommands(Player player, HologramData data) {
        // Registrar el clic (el check de cooldown ahora se hace en claim(), ANTES de remover
        // el holograma — así un clic en cooldown ya no pierde la recompensa)
        recordClick(player.getUniqueId(), data.getId());

        // Verificar que el holograma ya no esté activo (fue removido en claim() antes de llegar aquí)
        if (activeHolograms
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .containsKey(data.getId())) return;

        List<RewardEntry> rewards = data.getRewards();
        int pick = data.getRewardPick();

        if (pick <= 0 || pick >= rewards.size()) {
            // Sin pick (o pick >= total): evaluar todas por su chance individual
            for (RewardEntry reward : rewards) {
                if (reward.roll()) {
                    String parsed = reward.getCommand().replace("%player%", player.getName());
                    Bukkit.getScheduler().runTask(plugin,
                            () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed));
                }
            }
        } else {
            // Con pick: mezclar la lista, tomar las primeras N y evaluarlas por su chance
            List<RewardEntry> shuffled = new ArrayList<>(rewards);
            Collections.shuffle(shuffled);
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

    // ─── Parsers ───────────────────────────────────────────────────────────────

    private HologramData.Renderer parseRenderer(String value) {
        try {
            HologramData.Renderer r = HologramData.Renderer.valueOf(value.toUpperCase());
            // DEFAULT a nivel global no tiene sentido — caer a ARMOR_STAND
            return r == HologramData.Renderer.DEFAULT ? HologramData.Renderer.ARMOR_STAND : r;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid renderer '" + value + "', using ARMOR_STAND.");
            return HologramData.Renderer.ARMOR_STAND;
        }
    }

    private Display.Billboard parseBillboard(String value) {
        try {
            return Display.Billboard.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid billboard '" + value + "', using CENTER.");
            return Display.Billboard.CENTER;
        }
    }

    private ChatColor parseChatColor(String value) {
        try {
            ChatColor c = ChatColor.valueOf(value.toUpperCase());
            if (c.isColor()) return c;
        } catch (IllegalArgumentException ignored) {}
        plugin.getLogger().warning("Invalid glow color '" + value + "', using WHITE.");
        return ChatColor.WHITE;
    }

    private HologramData.SpawnAnimation parseSpawnAnimation(String value) {
        try {
            return HologramData.SpawnAnimation.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid animation '" + value + "', using NONE.");
            return HologramData.SpawnAnimation.NONE;
        }
    }

    private HologramData.ClickType parseClickType(String value) {
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
     *
     * Formato legacy (lista simple, chance 100%):
     *   commands:
     *     - "give %player% diamond 1"
     */
    private List<RewardEntry> parseRewards(ConfigurationSection h) {
        List<RewardEntry> list = new ArrayList<>();

        String listKey = h.isList("rewards.list") ? "rewards.list" : "rewards";

        if (h.isList(listKey)) {
            List<Map<?, ?>> rawList = h.getMapList(listKey);
            for (Map<?, ?> map : rawList) {
                Object cmdObj = map.get("command");
                if (cmdObj == null) continue;
                String cmd = cmdObj.toString();
                if (cmd.isEmpty()) continue;

                double chance = 100.0;
                Object chanceObj = map.get("chance");
                if (chanceObj instanceof Number) {
                    chance = ((Number) chanceObj).doubleValue();
                }
                list.add(new RewardEntry(cmd, chance));
            }
        }

        if (list.isEmpty()) {
            for (String cmd : h.getStringList("commands")) {
                list.add(new RewardEntry(cmd, 100.0));
            }
        }

        return list;
    }

    // ─── Getters / Utilidades admin ────────────────────────────────────────────

    public Collection<HologramData> getAllDefinitions() { return hologramDefinitions.values(); }
    public HologramData getDefinition(String id) { return hologramDefinitions.get(id); }
    public CooldownManager getCooldownManager() { return cooldownManager; }

    /**
     * Elimina todas las entidades del plugin dentro del radio dado.
     * Busca por PDC key directamente en el mundo, así atrapa también
     * entidades huérfanas que no estén en el mapa activeHolograms.
     * Soporta tanto armor stands como display entities.
     * @return cantidad de entidades eliminadas
     */
    public int removeNear(Location center, double radius) {
        int removed = 0;
        double radiusSq = radius * radius;
        List<Entity> toRemove = new ArrayList<>();

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof ArmorStand) && !(entity instanceof Display)) continue;
            if (entity.getLocation().distanceSquared(center) > radiusSq) continue;

            String ownerStr = entity.getPersistentDataContainer()
                    .get(ownerKey, org.bukkit.persistence.PersistentDataType.STRING);
            String hologramId = entity.getPersistentDataContainer()
                    .get(hologramIdKey, org.bukkit.persistence.PersistentDataType.STRING);

            if (ownerStr == null || hologramId == null) continue;

            // Eliminar del mapa activo si existe
            try {
                UUID ownerUuid = UUID.fromString(ownerStr);
                Map<String, ActiveHologram> playerActives = activeHolograms.get(ownerUuid);
                if (playerActives != null) playerActives.remove(hologramId);
            } catch (IllegalArgumentException ignored) {}

            toRemove.add(entity);
            removed++;
        }

        toRemove.forEach(HologramUtil::removeEntitySafely);
        releaseTicketsFor(toRemove);

        return removed;
    }

    /**
     * Devuelve una lista de info de hologramas activos:
     * ownerName, hologramId, y la ubicación de la primera entidad.
     */
    public List<ActiveHologramInfo> getActiveList() {
        List<ActiveHologramInfo> result = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, ActiveHologram>> playerEntry : activeHolograms.entrySet()) {
            String ownerName = Bukkit.getOfflinePlayer(playerEntry.getKey()).getName();
            if (ownerName == null) ownerName = playerEntry.getKey().toString().substring(0, 8);
            for (Map.Entry<String, ActiveHologram> holoEntry : playerEntry.getValue().entrySet()) {
                ActiveHologram active = holoEntry.getValue();
                Location loc = null;
                for (Entity e : active.getEntities()) {
                    if (e != null && !e.isDead()) { loc = e.getLocation(); break; }
                }
                result.add(new ActiveHologramInfo(ownerName, holoEntry.getKey(), loc));
            }
        }
        return result;
    }
}