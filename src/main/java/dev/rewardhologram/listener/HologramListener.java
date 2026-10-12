package dev.rewardhologram.listener;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.ActiveHologram;
import dev.rewardhologram.model.HologramData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.stream.Collectors;

public class HologramListener implements Listener {

    private final RewardHologramPlugin plugin;

    public HologramListener(RewardHologramPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Clic derecho directo (armor stands) ───────────────────────────────────

    /**
     * Clic derecho sobre armor stands. Disparan PlayerInteractAtEntityEvent,
     * que es subclase de PlayerInteractEntityEvent — un único handler cubre
     * ambos. Las displays NUNCA disparan este evento (el cliente no las
     * considera "pickable") — esas van por el ray trace de más abajo.
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof ArmorStand) && !(clicked instanceof Display)) return;
        handleRightClickDirect(event.getPlayer(), clicked, event);
    }

    private void handleRightClickDirect(Player player, Entity clicked,
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
        if (data.getClickType() == HologramData.ClickType.LEFT) return;

        claim(player, data, hologramId);
    }

    // ─── Clic derecho por ray trace (DISPLAY) ──────────────────────────────────

    /**
     * Fallback de clic derecho para hologramas DISPLAY.
     *
     * El cliente vanilla NO considera las displays "pickable" (no son
     * LivingEntity), así que NUNCA envía el paquete de interacción hacia
     * ellas — el clic se convierte en uso normal de ítem/bloque. Este
     * handler intercepta ese clic y lo convierte en claim cuando el ray
     * del servidor golpea el AABB del holograma del propio jugador.
     */
    @EventHandler
    public void onRightClickRay(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // un solo ray por clic físico

        Player player = event.getPlayer();

        ActiveHologram hit = plugin.getHologramManager().rayTraceHologram(player);
        if (hit == null) return;

        // El holograma intercepta el clic — evita colocar bloques
        // o consumir ítems "a través" del holograma
        event.setCancelled(true);

        HologramData data = plugin.getHologramManager().getDefinition(hit.getHologramId());
        if (data == null) return;
        if (data.getClickType() == HologramData.ClickType.LEFT) return;

        claim(player, data, hit.getHologramId());
    }

    // ─── Clic izquierdo por ray trace (DISPLAY) ────────────────────────────────

    /**
     * Clic izquierdo sobre hologramas DISPLAY vía arm swing + ray trace.
     * Las displays no generan eventos de daño (no son LivingEntity y son
     * invulnerables), pero el paquete de swing del brazo se envía en TODO
     * clic izquierdo — es el punto de enganche fiable.
     */
    @EventHandler
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();

        ActiveHologram hit = plugin.getHologramManager().rayTraceHologram(player);
        if (hit == null) return;

        HologramData data = plugin.getHologramManager().getDefinition(hit.getHologramId());
        if (data == null) return;
        if (data.getClickType() == HologramData.ClickType.RIGHT) return;

        claim(player, data, hit.getHologramId());
    }

    // ─── Clic izquierdo directo (armor stands — red de seguridad) ──────────────

    /**
     * El claim es idempotente (cooldown + remoción previa), así que aunque
     * este handler y otro mecanismo procesen el mismo clic, la recompensa
     * se da una sola vez.
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
        if (data.getClickType() == HologramData.ClickType.RIGHT) return;

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
        plugin.getHologramManager().removeAllForPlayer(event.getPlayer());
    }

    // ─── Claim ─────────────────────────────────────────────────────────────────

    private void claim(Player player, HologramData data, String hologramId) {
        // Verificar el cooldown ANTES de remover el holograma.
        // Un clic en cooldown simplemente se ignora y el holograma sigue
        // disponible — nunca se consume sin pagar la recompensa.
        if (plugin.getHologramManager().isClickOnCooldown(player.getUniqueId(), hologramId)) return;

        // Remover del mapa ANTES de ejecutar comandos.
        // Garantiza que las recompensas solo se den una vez.
        plugin.getHologramManager().removeHologram(player, hologramId);
        plugin.getHologramManager().executeCommands(player, data);
    }
}