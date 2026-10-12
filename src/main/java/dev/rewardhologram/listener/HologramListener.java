package dev.rewardhologram.listener;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.HologramData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.stream.Collectors;

public class HologramListener implements Listener {

    private final RewardHologramPlugin plugin;

    public HologramListener(RewardHologramPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Clic derecho ─────────────────────────────────────────────────────────

    /**
     * Handler unificado para ambas entidades del plugin:
     * armor stands (renderer ARMOR_STAND) y display entities (renderer DISPLAY).
     */
    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof ArmorStand) && !(clicked instanceof Display)) return;

        handleClaim(event.getPlayer(), clicked, event);
    }

    // ─── Clic izquierdo (ataque) ──────────────────────────────────────────────

    @EventHandler
    public void onHologramDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        Entity target = event.getEntity();
        if (!(target instanceof ArmorStand) && !(target instanceof Display)) return;

        String ownerStr = target.getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = target.getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        // Solo cancelar si es una entidad del plugin
        if (ownerStr == null || hologramId == null) return;

        event.setCancelled(true);

        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;

        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.RIGHT) return; // Solo acepta clic derecho

        claim(player, data, hologramId);
    }

    // ─── Jugador se une ────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Si es la primera vez que se une, inicializar cooldowns para todos los hologramas.
        // Esto evita que al unirse por primera vez aparezcan todos a la vez
        // bypasseando el chance — ahora deberá esperar el intervalo completo.
        java.util.Collection<String> ids = plugin.getHologramManager()
                .getAllDefinitions().stream()
                .map(HologramData::getId)
                .collect(Collectors.toList());

        plugin.getHologramManager().getCooldownManager()
                .initNewPlayer(player.getUniqueId(), ids);
    }

    // ─── Limpiar al desconectarse ──────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getHologramManager().removeAllForPlayer(event.getPlayer());
    }

    // ─── Limpiar al cambiar de mundo ──────────────────────────────────────────

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // Al cambiar de mundo los chunks del mundo anterior se descargan,
        // dejando las entidades del holograma huérfanas e inamovibles.
        // Eliminarlos inmediatamente evita hologramas que no desaparecen
        // y que se puedan reclamar infinitamente.
        plugin.getHologramManager().removeAllForPlayer(event.getPlayer());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Flujo común de reclamación por clic derecho.
     * El evento se cancela SIEMPRE que sea una entidad del plugin —
     * aunque quien clickea no sea el dueño — para evitar que la interacción
     * atraviese el holograma (romper bloques, abrir inventario, etc.).
     */
    private void handleClaim(Player player, Entity clicked,
                             PlayerInteractAtEntityEvent event) {
        String ownerStr = clicked.getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = clicked.getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        if (ownerStr == null || hologramId == null) return;

        event.setCancelled(true);

        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;

        // Verificar si RIGHT o BOTH están permitidos
        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.LEFT) return; // Solo acepta clic izquierdo

        claim(player, data, hologramId);
    }

    private void claim(Player player, HologramData data, String hologramId) {
        // FIX: verificar el cooldown ANTES de remover el holograma.
        // Antes el holograma se removía primero y executeCommands podía
        // rechazar el clic por cooldown — el holograma desaparecía sin dar
        // ninguna recompensa. Ahora un clic en cooldown simplemente se ignora
        // y el holograma sigue disponible.
        if (plugin.getHologramManager().isClickOnCooldown(player.getUniqueId(), hologramId)) return;

        // Remover del mapa ANTES de ejecutar comandos.
        // Esto garantiza que aunque falle el destroy o el jugador haga clic
        // múltiples veces muy rápido, las recompensas solo se dan una vez.
        plugin.getHologramManager().removeHologram(player, hologramId);
        plugin.getHologramManager().executeCommands(player, data);
    }
}