package dev.rewardhologram.util;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.HologramData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class HologramUtil {

    /**
     * Prefijo compartido de los teams de scoreboard usados para el glow.
     * Los nombres de team tienen un límite de 16 caracteres:
     * "rh_" (3) + el nombre de color más largo, LIGHT_PURPLE (12) = 15. Seguro.
     * El prefijo anterior "rh_glow_" excedía el límite con colores largos y el
     * registro del team fallaba silenciosamente.
     */
    public static final String GLOW_TEAM_PREFIX = "rh_";

    private HologramUtil() {}

    /**
     * Nombre del team de glow para un color.
     * Fuente única de verdad — usada por DisplayHologram (registro) y
     * HologramManager (limpieza), así nunca se desincronizan.
     */
    public static String glowTeamName(ChatColor color) {
        return GLOW_TEAM_PREFIX + color.name();
    }

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