package dev.rewardhologram.model;

import org.bukkit.Location;

/**
 * Snapshot de un holograma activo para uso en comandos admin.
 */
public class ActiveHologramInfo {

    private final String ownerName;
    private final String hologramId;
    private final Location location;

    public ActiveHologramInfo(String ownerName, String hologramId, Location location) {
        this.ownerName = ownerName;
        this.hologramId = hologramId;
        this.location = location;
    }

    public String ownerName()  { return ownerName; }
    public String hologramId() { return hologramId; }
    public Location location() { return location; }
}