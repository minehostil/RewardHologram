package dev.rewardhologram.listener;

import com.destroystokyo.paper.event.player.PrePlayerAttackEntityEvent;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
     * Clic derecho sobre ARMOR STANDS.
     * Los armor stands disparan PlayerInteractAtEntityEvent — el paquete
     * INTERACT_AT (con punto de interacción) es exclusivo de esta entidad
     * en el cliente vanilla.
     */
    @EventHandler
    public void onInteractArmorStand(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) return;
        handleRightClick(event.getPlayer(), event.getRightClicked(), event);
    }

    /**
     * Clic derecho sobre DISPLAY ENTITIES (TextDisplay/ItemDisplay).
     *
     * FIX: las displays NO disparan PlayerInteractAtEntityEvent — el cliente
     * vanilla no envía INTERACT_AT para ellas. Disparan el evento genérico
     * PlayerInteractEntityEvent. Por eso los hologramas DISPLAY no eran
     * reclamables con el listener anterior.
     *
     * El guard "instanceof" evita doble procesamiento: si el evento At
     * también llega a este handler (At extiende a InteractEntity), ya fue
     * manejado por onInteractArmorStand.
     */
    @EventHandler
    public void onInteractDisplay(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) return;
        if (!(event.getRightClicked() instanceof Display)) return;
        handleRightClick(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleRightClick(Player player, Entity clicked,
                                  PlayerInteractEntityEvent event) {
        String ownerStr = clicked.getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = clicked.getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        if (ownerStr == null || hologramId == null) return;

        // Cancelar SIEMPRE que sea una entidad del plugin — aunque quien
        // clickee no sea el dueño — para que la interacción no atraviese
        // el holograma (colocar bloques, abrir inventario, etc.)
        event.setCancelled(true);

        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;

        // Verificar si RIGHT o BOTH están permitidos
        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.LEFT) return;

        claim(player, data, hologramId);
    }

    // ─── Clic izquierdo ───────────────────────────────────────────────────────

    /**
     * Clic izquierdo sobre DISPLAY ENTITIES.
     *
     * Las displays no son LivingEntity y, al ser invulnerables, NO generan
     * EntityDamageByEntityEvent. Este evento de protocolo de Paper se dispara
     * para TODA entidad atacada (incluidas las displays) y es la única vía
     * fiable para detectar el clic izquierdo sobre ellas.
     */
    @EventHandler
    public void onAttackDisplay(PrePlayerAttackEntityEvent event) {
        if (!(event.getEntity() instanceof Display target)) return;

        String ownerStr = target.getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = target.getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        if (ownerStr == null || hologramId == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;

        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.RIGHT) return;

        claim(player, data, hologramId);
    }

    /**
     * Clic izquierdo sobre ARMOR STANDS (vía EntityDamageByEntityEvent,
     * como en el plugin original).
     */
    @EventHandler
    public void onArmorStandDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ArmorStand)) return;

        String ownerStr = event.getEntity().getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = event.getEntity().getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

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

    // ─── Claim ─────────────────────────────────────────────────────────────────

    private void claim(Player player, HologramData data, String hologramId) {
        // Verificar el cooldown ANTES de remover el holograma.
        // Un clic en cooldown simplemente se ignora y el holograma sigue
        // disponible — nunca se consume sin pagar la recompensa.
        if (plugin.getHologramManager().isClickOnCooldown(player.getUniqueId(), hologramId)) return;

        // Remover del mapa ANTES de ejecutar comandos.
        // Esto garantiza que aunque falle el destroy o el jugador haga clic
        // múltiples veces muy rápido, las recompensas solo se dan una vez.
        plugin.getHologramManager().removeHologram(player, hologramId);
        plugin.getHologramManager().executeCommands(player, data);
    }
}