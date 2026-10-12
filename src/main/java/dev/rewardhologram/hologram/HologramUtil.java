package dev.rewardhologram.hologram;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.HologramData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class HologramUtil {

    private HologramUtil() {}

    /** Calcula la posición base del holograma delante del jugador según sus offsets. */
    public static Location calculateBaseLocation(Player player, HologramData data) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw());
        double dx = -Math.sin(yaw) * data.getOffsetZ() + data.getOffsetX();
        double dz =  Math.cos(yaw) * data.getOffsetZ();
        return loc.add(dx, data.getOffsetY(), dz);
    }

    /** Aplica las PDC tags de dueño e ID a una entidad del holograma. */
    public static void tagEntity(Entity entity, RewardHologramPlugin plugin, Player owner, String hologramId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(plugin.getOwnerKey(), PersistentDataType.STRING, owner.getUniqueId().toString());
        pdc.set(plugin.getHologramIdKey(), PersistentDataType.STRING, hologramId);
    }

    /**
     * Elimina una entidad de forma segura. Si la referencia directa dice que está
     * muerta pero visualmente sigue ahí, la busca por UUID en el mundo.
     */
    public static void removeEntitySafely(Entity entity) {
        if (entity == null) return;
        if (!entity.isDead()) {
            entity.remove();
            return;
        }
        try {
            Entity byUuid = Bukkit.getEntity(entity.getUniqueId());
            if (byUuid != null && !byUuid.isDead()) byUuid.remove();
        } catch (Exception ignored) {}
    }
}