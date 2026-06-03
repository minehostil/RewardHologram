package dev.rewardhologram.hologram;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.ActiveHologram;
import dev.rewardhologram.model.HologramData;
import dev.rewardhologram.util.ColorUtil;
import dev.rewardhologram.util.SkullUtil;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holograma basado en armor stands reales.
 * Funciona para todos los jugadores (Java y Bedrock vía Geyser).
 * Sin dependencia de PacketEvents.
 */
public class RealHologram {

    private final RewardHologramPlugin plugin;
    private final Player player;
    private final HologramData data;

    public RealHologram(RewardHologramPlugin plugin, Player player, HologramData data) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
    }

    // Un armor stand normal (small=false) muestra el nombre ~2.35 bloques arriba de su Y base.
    // Para que las líneas queden donde queremos hay que bajar cada stand ese offset.
    private static final double NAME_TAG_OFFSET = 2.35;

    // La skull (small=true) muestra el casco ~1.075 bloques arriba de su Y base.
    private static final double SKULL_OFFSET = 1.075;

    public ActiveHologram spawn() {
        Location base = calculateBaseLocation();
        List<ArmorStand> entities = new ArrayList<>();

        int totalLines = data.getLines().size();
        double spacing = data.getLineSpacing();

        // Skull encima de todas las líneas con offset configurable
        if (data.isSkullEnabled()) {
            double skullVisualY = (totalLines - 1) * spacing + spacing + data.getSkullHeightOffset();
            double skullSpawnY  = skullVisualY - SKULL_OFFSET;
            entities.add(spawnSkullStand(base.clone().add(0, skullSpawnY, 0)));
        }

        // Líneas de texto — la primera (i=0) es la más alta
        for (int i = 0; i < totalLines; i++) {
            double lineVisualY = (totalLines - 1 - i) * spacing;
            double lineSpawnY  = lineVisualY - NAME_TAG_OFFSET;
            entities.add(spawnTextStand(base.clone().add(0, lineSpawnY, 0), data.getLines().get(i)));
        }

        entities.forEach(as -> {
            as.getPersistentDataContainer().set(
                    plugin.getOwnerKey(), PersistentDataType.STRING,
                    player.getUniqueId().toString());
            as.getPersistentDataContainer().set(
                    plugin.getHologramIdKey(), PersistentDataType.STRING,
                    data.getId());
        });

        return new ActiveHologram(data.getId(), ActiveHologram.Type.REAL,
                Collections.emptyList(), entities, base.getY(), data.getSkullHeadYaw());
    }

    public void teleportEntities(ActiveHologram active, double newY) {
        List<ArmorStand> entities = active.getRealEntities();
        int totalLines = data.getLines().size();
        double spacing = data.getLineSpacing();
        int idx = 0;

        if (data.isSkullEnabled() && !entities.isEmpty()) {
            ArmorStand skull = entities.get(idx++);
            if (!skull.isDead()) {
                Location loc = skull.getLocation();
                double skullVisualY = (totalLines - 1) * spacing + spacing + data.getSkullHeightOffset();
                skull.teleport(new Location(loc.getWorld(), loc.getX(),
                        newY + skullVisualY - SKULL_OFFSET, loc.getZ(),
                        active.getCurrentYaw(), 0f));
            }
        }

        for (int i = 0; i < totalLines && idx < entities.size(); i++, idx++) {
            ArmorStand as = entities.get(idx);
            if (!as.isDead()) {
                Location loc = as.getLocation();
                double lineVisualY = (totalLines - 1 - i) * spacing;
                as.teleport(new Location(loc.getWorld(), loc.getX(),
                        newY + lineVisualY - NAME_TAG_OFFSET, loc.getZ()));
            }
        }
    }

    public void destroy(ActiveHologram active) {
        active.getRealEntities().forEach(as -> {
            if (as != null && !as.isDead()) as.remove();
        });
    }

    // ─── Internals ─────────────────────────────────────────────────────────────

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

        ItemStack skull = SkullUtil.createSkull(data.getSkullTexture());
        EntityEquipment eq = as.getEquipment();
        if (eq != null) eq.setHelmet(skull);

        return as;
    }

    private Location calculateBaseLocation() {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw());
        double dx = -Math.sin(yaw) * data.getOffsetZ() + data.getOffsetX();
        double dz =  Math.cos(yaw) * data.getOffsetZ();
        return loc.add(dx, data.getOffsetY(), dz);
    }
}