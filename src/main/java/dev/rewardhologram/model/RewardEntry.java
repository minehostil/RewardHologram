package dev.rewardhologram.model;

/**
 * Una recompensa individual con su propia probabilidad.
 * Al reclamar el holograma se evalúa el chance de cada recompensa
 * de forma independiente, permitiendo dar múltiples o ninguna.
 */
public class RewardEntry {

    private final String command;
    private final double chance; // 0.0 - 100.0

    public RewardEntry(String command, double chance) {
        this.command = command;
        this.chance = chance;
    }

    public String getCommand() { return command; }
    public double getChance() { return chance; }

    /** Evalúa si esta recompensa se otorga. */
    public boolean roll() {
        return Math.random() * 100 <= chance;
    }
}