package dev.rewardhologram.hologram;

import dev.rewardhologram.model.ActiveHologram;

/**
 * Abstracción de un holograma renderizable.
 * Cada implementación (armor stands o display entities)
 * maneja sus propias animaciones y ciclo de vida.
 */
public interface Hologram {

    /** Spawnea las entidades y devuelve el estado activo. */
    ActiveHologram spawn();

    /**
     * Animaciones por tick (bobbing, squash, tilt, spin...).
     * Llamado desde el hilo principal.
     */
    void tick(ActiveHologram active);

    /** Eliminación inmediata de las entidades. */
    void destroy(ActiveHologram active);

    /**
     * Eliminación suave (pop-out interpolado).
     * Default: destrucción inmediata (usado por ARMOR_STAND).
     */
    default void destroySmooth(ActiveHologram active) { destroy(active); }
}