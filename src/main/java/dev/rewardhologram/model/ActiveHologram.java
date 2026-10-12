package dev.rewardhologram.model;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa un holograma activo para un jugador específico.
 * Las entidades se guardan como Entity genérico para soportar
 * tanto armor stands (Type.REAL) como displays (Type.DISPLAY).
 */
public class ActiveHologram {

    public enum Type { REAL, DISPLAY }

    private final String hologramId;
    private final Type type;
    private final List<Entity> entities;
    private final Location baseLocation;

    private double bobbingPhase = 0.0;
    private double baseY;
    private float currentYaw;          // Rotación actual de la cabeza

    // Estado de animaciones (DISPLAY)
    private boolean appearanceDone = true;       // Pop-in terminado
    private boolean transformLoopStarted = false; // Loop de transforms por tick iniciado

    public ActiveHologram(String hologramId, Type type, List<Entity> entities,
                          Location baseLocation, float initialYaw) {
        this.hologramId = hologramId;
        this.type = type;
        this.entities = entities;
        this.baseLocation = baseLocation;
        this.baseY = baseLocation.getY();
        this.currentYaw = initialYaw;
    }

    public String getHologramId() { return hologramId; }
    public Type getType() { return type; }
    public List<Entity> getEntities() { return entities; }
    public Location getBaseLocation() { return baseLocation; }

    public List<ArmorStand> getRealEntities() {
        List<ArmorStand> list = new ArrayList<>();
        for (Entity e : entities) if (e instanceof ArmorStand as) list.add(as);
        return list;
    }

    public List<Display> getDisplayEntities() {
        List<Display> list = new ArrayList<>();
        for (Entity e : entities) if (e instanceof Display d) list.add(d);
        return list;
    }

    public double getBobbingPhase() { return bobbingPhase; }
    public void incrementPhase(double speed) { this.bobbingPhase += speed; }
    public double getBaseY() { return baseY; }
    public void setBaseY(double baseY) { this.baseY = baseY; }
    public float getCurrentYaw() { return currentYaw; }
    public void incrementYaw(float speed) { this.currentYaw = (this.currentYaw + speed) % 360f; }

    public boolean isAppearanceDone() { return appearanceDone; }
    public void setAppearanceDone(boolean v) { this.appearanceDone = v; }
    public boolean isTransformLoopStarted() { return transformLoopStarted; }
    public void markTransformLoopStarted() { this.transformLoopStarted = true; }
}