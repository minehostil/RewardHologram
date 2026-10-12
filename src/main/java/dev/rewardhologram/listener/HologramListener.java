package dev.rewardhologram.listener;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.HologramData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.stream.Collectors;

public class HologramListener implements Listener {

    /**
     * Alcance del ray para el clic izquierdo. El cliente solo permite
     * atacar entidades a ~3 bloques; 4 da margen por latencia.
     */
    private static final double ATTACK_REACH = 4.0;

    private final RewardHologramPlugin plugin;

    public HologramListener(RewardHologramPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Clic derecho ─────────────────────────────────────────────────────────

    /**
     * Clic derecho sobre ARMOR STANDS — disparan PlayerInteractAtEntityEvent
     * (el paquete INTERACT_AT es exclusivo de esta entidad en el cliente vanilla).
     */
    @EventHandler
    public void onInteractArmorStand(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) return;
        handleRightClick(event.getPlayer(), event.getRightClicked(), event);
    }

    /**
     * Clic derecho sobre DISPLAY ENTITIES — disparan el genérico
     * PlayerInteractEntityEvent (el cliente no envía INTERACT_AT para displays).
     * El guard evita re-procesar eventos At que ya manejó onInteractArmorStand.
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

        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.LEFT) return;

        claim(player, data, hologramId);
    }

    // ─── Clic izquierdo ───────────────────────────────────────────────────────

    /**
     * Clic izquierdo sobre cualquier entidad del plugin — vía arm swing + ray trace.
     *
     * Por qué este enfoque y no eventos de daño:
     *  1. Las displays no son LivingEntity y al ser invulnerables NUNCA generan
     *     EntityDamageByEntityEvent.
     *  2. Nuestras entidades son invulnerables (setInvulnerable(true)) — el check
     *     isInvulnerableTo() aborta el daño ANTES de disparar el evento de daño,
     *     así que ese evento tampoco llega para los armor stands del plugin.
     *
     * El paquete de swing del brazo se envía en TODO clic izquierdo, incluso con
     * una entidad en la mira — PlayerAnimationEvent es un punto de enganche
     * fiable en cualquier versión de la API. rayTraceEntities() reproduce el
     * cálculo de objetivo del cliente.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();

        // Chequeo barato (map lookup) antes del ray trace — el 99% de los swings
        // (minería, combate) ocurre sin ningún holograma activo del jugador
        if (!plugin.getHologramManager().hasActiveHolograms(player.getUniqueId())) return;

        RayTraceResult entityHit = player.rayTraceEntities((int) ATTACK_REACH, true);
        if (entityHit == null || entityHit.getHitEntity() == null) return;

        // Si hay un bloque más cerca que la entidad, el ataque va al bloque,
        // no al holograma (evita reclamos al minar "a través" del holograma)
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                ATTACK_REACH, FluidCollisionMode.NEVER, true);
        if (blockHit != null && entityHit.getHitPosition()
                .distanceSquared(player.getEyeLocation())
                > blockHit.getHitPosition()
                .distanceSquared(player.getEyeLocation())) {
            return;
        }

        Entity hit = entityHit.getHitEntity();

        String ownerStr = hit.getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = hit.getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        if (ownerStr == null || hologramId == null) return;

        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;

        HologramData.ClickType type = data.getClickType();
        if (type == HologramData.ClickType.RIGHT) return;

        claim(player, data, hologramId);
    }

    /**
     * Se mantiene como red de seguridad: cancela daño a armor stands del plugin
     * y reclama si el evento llegara a dispararse (el claim es idempotente —
     * cooldown + remoción previa — así que aunque este handler y el ray trace
     * procesen el mismo clic, la recompensa se da una sola vez).
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