package dev.rewardhologram.listener;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.HologramData;
import org.bukkit.entity.ArmorStand;
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

    @EventHandler
    public void onInteractArmorStand(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand as)) return;

        String ownerStr = as.getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = as.getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        if (ownerStr == null || hologramId == null) return;

        event.setCancelled(true);

        if (!event.getPlayer().getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;

        // Verificar si RIGHT o BOTH están permitidos
        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.LEFT) return; // Solo acepta clic izquierdo

        claim(event.getPlayer(), data, hologramId);
    }

    // ─── Clic izquierdo (ataque) ──────────────────────────────────────────────

    @EventHandler
    public void onArmorStandDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand as)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        String ownerStr = as.getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = as.getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        // Solo cancelar si es un armor stand del plugin
        if (ownerStr == null || hologramId == null) return;

        event.setCancelled(true);

        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;

        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.RIGHT) return;

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

    // ─── Helper ───────────────────────────────────────────────────────────────

    private void claim(Player player, HologramData data, String hologramId) {
        // Remover del mapa ANTES de ejecutar comandos.
        // Esto garantiza que aunque falle el destroy o el jugador haga clic
        // múltiples veces muy rápido, las recompensas solo se dan una vez.
        plugin.getHologramManager().removeHologram(player, hologramId);
        plugin.getHologramManager().executeCommands(player, data);
    }
}