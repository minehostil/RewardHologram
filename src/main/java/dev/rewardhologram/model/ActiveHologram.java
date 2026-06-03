package dev.rewardhologram.model;

import java.util.List;

/**
 * Representa un holograma activo para un jugador específico.
 */
public class ActiveHologram {

    public enum Type { REAL }

    private final String hologramId;
    private final Type type;
    private final List<Integer> entityIds;  // Reservado para uso futuro
    private final List<org.bukkit.entity.ArmorStand> realEntities;

    private double bobbingPhase = 0.0;
    private double baseY;
    private float currentYaw;  // Rotación actual de la cabeza

    public ActiveHologram(String hologramId, Type type,
                          List<Integer> entityIds,
                          List<org.bukkit.entity.ArmorStand> realEntities,
                          double baseY, float initialYaw) {
        this.hologramId = hologramId;
        this.type = type;
        this.entityIds = entityIds;
        this.realEntities = realEntities;
        this.baseY = baseY;
        this.currentYaw = initialYaw;
    }

    public String getHologramId() { return hologramId; }
    public Type getType() { return type; }
    public List<Integer> getEntityIds() { return entityIds; }
    public List<org.bukkit.entity.ArmorStand> getRealEntities() { return realEntities; }
    public double getBobbingPhase() { return bobbingPhase; }
    public void incrementPhase(double speed) { this.bobbingPhase += speed; }
    public double getBaseY() { return baseY; }
    public void setBaseY(double baseY) { this.baseY = baseY; }
    public float getCurrentYaw() { return currentYaw; }
    public void incrementYaw(float speed) { this.currentYaw = (this.currentYaw + speed) % 360f; }
}