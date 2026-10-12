package dev.rewardhologram.listener;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.HologramData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
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
        handleClaim(event.getPlayer(), as);
    }

    // Interacción con displays (clic derecho sobre TextDisplay/ItemDisplay)
    @EventHandler
    public void onInteractDisplay(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Display)) return;
        handleClaim(event.getPlayer(), event.getRightClicked());
    }

    // ─── Clic izquierdo (ataque) ──────────────────────────────────────────────

    @EventHandler
    public void onHologramDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ArmorStand)
                && !(event.getEntity() instanceof Display)) return;

        String ownerStr = event.getEntity().getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = event.getEntity().getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        // Solo cancelar si es una entidad del plugin
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
        plugin.getHologramManager().removeAllForPlayer(event.getPlayer());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void handleClaim(Player player, org.bukkit.entity.Entity clicked) {
        String ownerStr = clicked.getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = clicked.getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        if (ownerStr == null || hologramId == null) return;

        // El evento se cancela siempre que sea una entidad del plugin
        // (evita romper bloques / abrir el inventario del armor stand)
        if (clicked instanceof ArmorStand) {
            // PlayerInteractAtEntityEvent se cancela desde el caller
        }

        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;

        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.LEFT) return; // Solo acepta clic izquierdo

        claim(player, data, hologramId);
    }

    private void claim(Player player, HologramData data, String hologramId) {
        // FIX: verificar el cooldown ANTES de remover el holograma.
        // Antes el holograma se removía primero y executeCommands podía
        // rechazar el clic por cooldown — perdiendo la recompensa.
        if (plugin.getHologramManager().isClickOnCooldown(player.getUniqueId(), hologramId)) return;

        // Remover del mapa ANTES de ejecutar comandos.
        // Esto garantiza que aunque falle el destroy o el jugador haga clic
        // múltiples veces muy rápido, las recompensas solo se dan una vez.
        plugin.getHologramManager().removeHologram(player, hologramId);
        plugin.getHologramManager().executeCommands(player, data);
    }
}