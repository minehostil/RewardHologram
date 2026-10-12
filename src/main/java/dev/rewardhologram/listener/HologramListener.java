package dev.rewardhologram.listener;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.ActiveHologram;
import dev.rewardhologram.model.HologramData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
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

    // ─── Clic derecho ─────────────────────────────────────────────────────────

    /**
     * Clic derecho sobre ARMOR STANDS.
     *
     * CRÍTICO: debe registrarse en PlayerInteractAtEntityEvent (la clase exacta),
     * NO en PlayerInteractEntityEvent. Los armor stands disparan At, que tiene
     * su PROPIO HandlerList — un handler de la superclase nunca lo recibe.
     * Ese fue el bug que rompió ARMOR_STAND.
     */
    @EventHandler
    public void onInteractArmorStand(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) return;
        handleRightClickDirect(event.getPlayer(), event.getRightClicked(), event);
    }

    /**
     * Clic derecho sobre DISPLAY e INTERACTION entities — disparan el evento
     * genérico (no el At). Los guards evitan re-procesar eventos que ya
     * manejan los handlers especializados.
     */
    @EventHandler
    public void onInteractGeneric(PlayerInteractEntityEvent event) {
        // PlayerInteractAtEntityEvent es subclase — ya lo manejó onInteractArmorStand
        if (event instanceof PlayerInteractAtEntityEvent) return;
        // PlayerArmorStandManipulateEvent también es subclase — lo maneja su handler
        if (event instanceof PlayerArmorStandManipulateEvent) return;

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Interaction) && !(clicked instanceof Display)) return;
        handleRightClickDirect(event.getPlayer(), clicked, event);
    }

    /**
     * Bloquea equipar/robar items de CUALQUIER armor stand del plugin.
     *
     * Con un ítem equipable (armaduras, elytra, heads) el servidor dispara
     * ESTE evento en lugar del At — sin el claim aquí, ese clic solo
     * equiparía la armadura sin reclamar. Cancela el equipamiento Y reclama.
     */
    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        String ownerStr = event.getRightClicked().getPersistentDataContainer()
                .get(plugin.getOwnerKey(), PersistentDataType.STRING);
        String hologramId = event.getRightClicked().getPersistentDataContainer()
                .get(plugin.getHologramIdKey(), PersistentDataType.STRING);

        if (ownerStr == null || hologramId == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;
        if (data.getClickType() == HologramData.ClickType.LEFT) return;

        claim(player, data, hologramId);
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
        // el holograma (colocar bloques, abrir inventario, equipar, etc.)
        event.setCancelled(true);

        if (!player.getUniqueId().toString().equals(ownerStr)) return;

        HologramData data = plugin.getHologramManager().getDefinition(hologramId);
        if (data == null) return;
        if (data.getClickType() == HologramData.ClickType.LEFT) return;

        claim(player, data, hologramId);
    }

    /**
     * Fallback de clic derecho: ray trace server-side contra los AABBs de los
     * hologramas del jugador. Cubre el caso USE_ITEM (item en mano apuntando
     * al aire) y respalda cualquier hueco de los eventos directos.
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

    // ─── Clic izquierdo ───────────────────────────────────────────────────────

    /**
     * Clic izquierdo sobre cualquier holograma del propio jugador —
     * vía arm swing + ray trace.
     *
     * Por qué este enfoque y no eventos de daño:
     *  1. Las displays no son LivingEntity y nunca generan EntityDamageByEntityEvent.
     *  2. Nuestras entidades son invulnerables — el check isInvulnerableTo()
     *     aborta el daño ANTES de disparar el evento, así que ese evento
     *     tampoco llega de forma fiable para los armor stands del plugin.
     *
     * El paquete de swing del brazo se envía en TODO clic izquierdo —
     * es el punto de enganche 100% fiable. rayTraceHologram ahora cubre
     * REAL y DISPLAY.
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

    /**
     * Red de seguridad: si el evento de daño llegara a dispararse
     * (algunas rutas del servidor), reclama igual — el claim es idempotente
     * (cooldown + isActiveHologram + remoción previa).
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

    // ─── Claim ─────────────────────────────────────────────────────────────────

    private void claim(Player player, HologramData data, String hologramId) {
        if (plugin.getHologramManager().isClickOnCooldown(player.getUniqueId(), hologramId)) return;
        if (!plugin.getHologramManager().isActiveHologram(player.getUniqueId(), hologramId)) return;

        plugin.getHologramManager().removeHologram(player, hologramId);
        plugin.getHologramManager().executeCommands(player, data);
    }
}