package dev.rewardhologram.hologram;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.ActiveHologram;
import dev.rewardhologram.model.HologramData;
import dev.rewardhologram.util.ColorUtil;
import dev.rewardhologram.util.SkullUtil;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Holograma basado en armor stands reales.
 * Funciona para todos los jugadores (Java y Bedrock vía Geyser).
 * Sin dependencia de PacketEvents.
 */
public class RealHologram implements Hologram {

    private final RewardHologramPlugin plugin;
    private final Player player;
    private final HologramData data;

    public RealHologram(RewardHologramPlugin plugin, Player player, HologramData data) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
    }

    // Un armor stand normal (small=false) muestra el nombre ~2.35 bloques arriba de su Y base.
    private static final double NAME_TAG_OFFSET = 2.35;
    // La skull (small=true) muestra el casco ~1.075 bloques arriba de su Y base.
    private static final double SKULL_OFFSET = 1.075;

    @Override
    public ActiveHologram spawn() {
        Location base = HologramUtil.calculateBaseLocation(player, data);
        List<Entity> entities = new ArrayList<>();

        int totalLines = data.getLines().size();
        double spacing = data.getLineSpacing();

        // Skull encima de todas las líneas con offset configurable
        if (data.isSkullEnabled()) {
            double skullVisualY = (totalLines - 1) * spacing + spacing + data.getSkullHeightOffset();
            entities.add(spawnSkullStand(base.clone().add(0, skullVisualY - SKULL_OFFSET, 0)));
        }

        // Líneas de texto — la primera (i=0) es la más alta
        for (int i = 0; i < totalLines; i++) {
            double lineVisualY = (totalLines - 1 - i) * spacing;
            entities.add(spawnTextStand(base.clone().add(0, lineVisualY - NAME_TAG_OFFSET, 0), data.getLines().get(i)));
        }

        entities.forEach(e -> HologramUtil.tagEntity(e, plugin, player, data.getId()));

        return new ActiveHologram(data.getId(), ActiveHologram.Type.REAL,
                entities, base.clone(), data.getSkullHeadYaw());
    }

    @Override
    public void tick(ActiveHologram active) {
        // La fase siempre avanza para mantener el estado consistente
        active.incrementPhase(data.getBobbingSpeed());

        if (data.isSkullEnabled() && data.isSkullRotationEnabled())
            active.incrementYaw(data.getSkullRotationSpeed());

        double bob = data.isBobbingEnabled()
                ? Math.sin(active.getBobbingPhase()) * data.getBobbingAmplitude()
                : 0;
        teleportEntities(active, active.getBaseY() + bob);
    }

    @Override
    public void destroy(ActiveHologram active) {
        active.getEntities().forEach(HologramUtil::removeEntitySafely);
    }

    // ─── Internals ─────────────────────────────────────────────────────────────

    private void teleportEntities(ActiveHologram active, double newY) {
        List<Entity> entities = active.getEntities();
        int totalLines = data.getLines().size();
        double spacing = data.getLineSpacing();
        int idx = 0;

        if (data.isSkullEnabled() && !entities.isEmpty()) {
            Entity skull = entities.get(idx++);
            if (!skull.isDead()) {
                Location loc = skull.getLocation();
                double skullVisualY = (totalLines - 1) * spacing + spacing + data.getSkullHeightOffset();
                skull.teleport(new Location(loc.getWorld(), loc.getX(),
                        newY + skullVisualY - SKULL_OFFSET, loc.getZ(),
                        active.getCurrentYaw(), 0f));
            }
        }

        for (int i = 0; i < totalLines && idx < entities.size(); i++, idx++) {
            Entity as = entities.get(idx);
            if (!as.isDead()) {
                Location loc = as.getLocation();
                double lineVisualY = (totalLines - 1 - i) * spacing;
                as.teleport(new Location(loc.getWorld(), loc.getX(),
                        newY + lineVisualY - NAME_TAG_OFFSET, loc.getZ()));
            }
        }
    }

    private ArmorStand spawnTextStand(Location loc, String text) {
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.customName(ColorUtil.color(text));
        as.setCustomNameVisible(true);
        as.setInvisible(true);
        as.setGravity(false);
        as.setSmall(false);      // Hitbox normal — más fácil de clickear
        as.setCollidable(false);
        as.setInvulnerable(true);
        as.setCanPickupItems(false);
        as.setMarker(false);     // Sin marker — habilita el hitbox de clic
        as.setArms(false);
        as.setBasePlate(false);
        as.setPersistent(false); // No persistir en el mundo — evita huérfanos
        return as;
    }

    private ArmorStand spawnSkullStand(Location loc) {
        // Aplicar la rotación de la cabeza al spawnear
        loc.setYaw(data.getSkullHeadYaw());
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setInvisible(true);
        as.setGravity(false);
        as.setSmall(true);
        as.setCollidable(false);
        as.setInvulnerable(true);
        as.setCanPickupItems(false);
        as.setPersistent(false);

        ItemStack skull = SkullUtil.createSkull(data.getSkullTexture());
        EntityEquipment eq = as.getEquipment();
        if (eq != null) eq.setHelmet(skull);

        return as;
    }
}