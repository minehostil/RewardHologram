package dev.rewardhologram.hologram;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.ActiveHologram;
import dev.rewardhologram.model.HologramData;
import dev.rewardhologram.util.ColorUtil;
import dev.rewardhologram.util.HologramUtil;
import dev.rewardhologram.util.SkullUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holograma basado en Display Entities (1.19.4+).
 *
 * Ventajas sobre armor stands:
 *  - Billboard configurable (el texto puede mirar siempre al jugador o ser fijo)
 *  - Escala por línea y global
 *  - Glow con color personalizado
 *  - Animaciones avanzadas de la skull: squash & stretch, tilt lateral, spin
 *  - Aparición/desaparición suave (pop-in / pop-out) vía interpolación nativa
 *
 * El movimiento vertical (bobbing) usa teleport con teleportDuration=1,
 * que el cliente interpola — muy suave y barato en packets.
 */
public class DisplayHologram implements Hologram {

    private static final Vector3f ZERO_TRANSLATION = new Vector3f(0, 0, 0);
    private static final Quaternionf IDENTITY_ROTATION = new Quaternionf();
    private static final Vector3f NEAR_ZERO_SCALE = new Vector3f(0.0001f, 0.0001f, 0.0001f);

    private final RewardHologramPlugin plugin;
    private final Player player;
    private final HologramData data;

    public DisplayHologram(RewardHologramPlugin plugin, Player player, HologramData data) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
    }

    // ─── Spawn ─────────────────────────────────────────────────────────────────

    @Override
    public ActiveHologram spawn() {
        Location base = HologramUtil.calculateBaseLocation(player, data);
        List<Entity> entities = new ArrayList<>();
        // Transform final de cada entidad — usado por el pop-in para animar hacia ella
        Map<Entity, Transformation> finalTransforms = new HashMap<>();

        boolean popIn = data.getAppearAnimation() == HologramData.SpawnAnimation.POP;
        float initialScale = popIn ? 0.0001f : 1.0f;

        int totalLines = data.getLines().size();
        double spacing = data.getLineSpacing();

        // Skull encima de todas las líneas
        if (data.isSkullEnabled()) {
            double skullVisualY = (totalLines - 1) * spacing + spacing + data.getSkullHeightOffset();
            ItemDisplay skull = spawnSkull(base.clone().add(0, skullVisualY, 0), initialScale);
            entities.add(skull);
            finalTransforms.put(skull, staticSkullTransform(1.0f));
        }

        // Líneas de texto — la primera (i=0) es la más alta
        for (int i = 0; i < totalLines; i++) {
            double lineVisualY = (totalLines - 1 - i) * spacing;
            TextDisplay line = spawnTextLine(base.clone().add(0, lineVisualY, 0), i, initialScale);
            entities.add(line);
            finalTransforms.put(line, staticLineTransform(i, 1.0f));
        }

        entities.forEach(e -> HologramUtil.tagEntity(e, plugin, player, data.getId()));

        ActiveHologram active = new ActiveHologram(data.getId(), ActiveHologram.Type.DISPLAY,
                entities, base.clone(), data.getSkullHeadYaw());

        if (popIn) {
            active.setAppearanceDone(false);
            schedulePopIn(active, finalTransforms);
        }

        return active;
    }

    /**
     * Pop-in: las entidades spawnean con escala ~0 y un tick después se
     * animan hacia su escala final usando la interpolación nativa del display.
     */
    private void schedulePopIn(ActiveHologram active, Map<Entity, Transformation> finalTransforms) {
        int duration = Math.max(1, data.getAppearDurationTicks());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finalTransforms.forEach((entity, transform) -> {
                if (entity instanceof Display d && !d.isDead()) {
                    d.setInterpolationDuration(duration);
                    d.setTransformation(transform);
                }
            });
            // Tras terminar el pop-in, habilitar el loop de animaciones por tick
            Bukkit.getScheduler().runTaskLater(plugin, () -> active.setAppearanceDone(true), duration);
        }, 1L);
    }

    // ─── Tick (animaciones) ────────────────────────────────────────────────────

    @Override
    public void tick(ActiveHologram active) {
        // La fase siempre avanza: squash/tilt la usan aunque el bobbing esté desactivado
        active.incrementPhase(data.getBobbingSpeed());

        if (data.isSkullEnabled() && data.isSkullRotationEnabled())
            active.incrementYaw(data.getSkullRotationSpeed());

        double bob = data.isBobbingEnabled()
                ? Math.sin(active.getBobbingPhase()) * data.getBobbingAmplitude()
                : 0;
        teleportAll(active, active.getBaseY() + bob);

        // Durante el pop-in no tocar transforms (el scheduler las controla)
        if (!active.isAppearanceDone()) return;

        List<Display> displays = active.getDisplayEntities();
        boolean skullAnimated = data.isSkullEnabled()
                && (data.isSquashEnabled() || data.isTiltEnabled() || data.isSkullRotationEnabled());
        if (!skullAnimated || displays.isEmpty()) return;

        // Primera vez: bajar la interpolación a 1 tick para animaciones fluidas por tick
        if (!active.isTransformLoopStarted()) {
            active.markTransformLoopStarted();
            displays.forEach(d -> d.setInterpolationDuration(1));
        }

        Display skull = displays.get(0);
        if (!skull.isDead()) skull.setTransformation(dynamicSkullTransform(active));
    }

    private void teleportAll(ActiveHologram active, double newY) {
        List<Display> displays = active.getDisplayEntities();
        int totalLines = data.getLines().size();
        double spacing = data.getLineSpacing();
        int idx = 0;

        if (data.isSkullEnabled() && !displays.isEmpty()) {
            Display skull = displays.get(idx++);
            if (!skull.isDead()) {
                Location loc = skull.getLocation();
                double skullVisualY = (totalLines - 1) * spacing + spacing + data.getSkullHeightOffset();
                skull.teleport(new Location(loc.getWorld(), loc.getX(), newY + skullVisualY, loc.getZ()));
            }
        }

        for (int i = 0; i < totalLines && idx < displays.size(); i++, idx++) {
            Display line = displays.get(idx);
            if (!line.isDead()) {
                Location loc = line.getLocation();
                double lineVisualY = (totalLines - 1 - i) * spacing;
                line.teleport(new Location(loc.getWorld(), loc.getX(), newY + lineVisualY, loc.getZ()));
            }
        }
    }

    // ─── Destroy ───────────────────────────────────────────────────────────────

    @Override
    public void destroy(ActiveHologram active) {
        active.getEntities().forEach(e -> {
            if (e instanceof Display d) unregisterGlow(d);
            HologramUtil.removeEntitySafely(e);
        });
    }

    /**
     * Pop-out: escala a ~0 con interpolación y elimina las entidades al terminar.
     * Las PDC tags se quitan de inmediato para que un holograma "muriendo"
     * no pueda ser reclamado.
     */
    @Override
    public void destroySmooth(ActiveHologram active) {
        int duration = Math.max(1, data.getDespawnDurationTicks());
        List<Entity> entities = new ArrayList<>(active.getEntities());

        for (Entity e : entities) {
            if (!(e instanceof Display d) || d.isDead()) continue;

            d.getPersistentDataContainer().remove(plugin.getOwnerKey());
            d.getPersistentDataContainer().remove(plugin.getHologramIdKey());
            unregisterGlow(d);

            d.setInterpolationDuration(duration);
            Transformation current = d.getTransformation();
            d.setTransformation(new Transformation(
                    current.getTranslation(), current.getLeftRotation(),
                    NEAR_ZERO_SCALE, current.getRightRotation()));
        }

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> entities.forEach(HologramUtil::removeEntitySafely), duration + 2L);
    }

    // ─── Spawns de entidades ───────────────────────────────────────────────────

    private TextDisplay spawnTextLine(Location loc, int index, float initialScale) {
        return loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            // setText() solo acepta String con códigos § (API Bukkit legacy).
            // colorString() serializa el Component de Adventure a ese formato —
            // color() devolvería un Component y no compila aquí.
            td.setText(ColorUtil.colorString(data.getLines().get(index)));
            td.setBillboard(data.getBillboard());
            td.setSeeThrough(data.isTextSeeThrough());
            td.setShadowed(data.isTextShadowed());
            td.setBackgroundColor(data.isTextBackgroundTransparent()
                    ? Color.fromARGB(0, 0, 0, 0)      // Fondo invisible — look holograma clásico
                    : Color.fromARGB(0x40, 0, 0, 0)); // Fondo semitransparente
            td.setTeleportDuration(1); // Interpolar teleports — movimiento suave
            td.setInvulnerable(true);
            td.setPersistent(false);
            applyGlow(td);
            td.setTransformation(staticLineTransform(index, initialScale));
        });
    }

    private ItemDisplay spawnSkull(Location loc, float initialScale) {
        return loc.getWorld().spawn(loc, ItemDisplay.class, id -> {
            id.setItemStack(SkullUtil.createSkull(data.getSkullTexture()));
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            id.setBillboard(data.getBillboard());
            id.setTeleportDuration(1);
            td.setInvulnerable(true);
            id.setPersistent(false);
            applyGlow(id);
            id.setTransformation(staticSkullTransform(initialScale));
        });
    }

    // ─── Transforms ────────────────────────────────────────────────────────────

    private Transformation staticLineTransform(int index, float scaleMultiplier) {
        float scale = (float) (data.getLineScale(index) * data.getDisplayScale()) * scaleMultiplier;
        return new Transformation(ZERO_TRANSLATION, IDENTITY_ROTATION,
                new Vector3f(scale, scale, scale), IDENTITY_ROTATION);
    }

    private Transformation staticSkullTransform(float scaleMultiplier) {
        float scale = (float) (data.getSkullScale() * data.getDisplayScale()) * scaleMultiplier;
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(data.getSkullHeadYaw()));
        return new Transformation(ZERO_TRANSLATION, rotation,
                new Vector3f(scale, scale, scale), IDENTITY_ROTATION);
    }

    /**
     * Transform animada de la skull: squash & stretch (escala Y oscilante con
     * compensación X/Z), tilt lateral (rotación Z) y spin (rotación Y).
     * Con billboard CENTER, la rotación Z local se percibe como inclinación
     * desde la perspectiva del espectador — exactamente el efecto deseado.
     */
    private Transformation dynamicSkullTransform(ActiveHologram active) {
        float base = (float) (data.getSkullScale() * data.getDisplayScale());
        float sx = base, sy = base, sz = base;

        if (data.isSquashEnabled()) {
            double s = Math.sin(active.getBobbingPhase() * data.getSquashSpeed());
            sy = (float) (base * (1.0 + s * data.getSquashAmplitude()));
            float sxz = (float) (base * (1.0 - s * data.getSquashAmplitude() * 0.5));
            sx = sxz;
            sz = sxz;
        }

        Quaternionf rotation = new Quaternionf();
        if (data.isTiltEnabled()) {
            double tiltDeg = Math.sin(active.getBobbingPhase() * data.getTiltSpeed()) * data.getTiltAmplitude();
            rotation.rotateZ((float) Math.toRadians(tiltDeg));
        }
        float yaw = data.isSkullRotationEnabled()
                ? active.getCurrentYaw()
                : data.getSkullHeadYaw();
        rotation.rotateY((float) Math.toRadians(yaw));

        return new Transformation(ZERO_TRANSLATION, rotation,
                new Vector3f(sx, sy, sz), IDENTITY_ROTATION);
    }

    // ─── Glow (vía scoreboard team — API pura) ─────────────────────────────────

    private void applyGlow(Display display) {
        if (!data.isGlowEnabled()) return;
        display.setGlowing(true);
        try {
            var sb = Bukkit.getScoreboardManager().getMainScoreboard();
            String teamName = HologramUtil.glowTeamName(data.getGlowColor());
            Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.registerNewTeam(teamName);
                team.setColor(data.getGlowColor());
            }
            team.addEntry(display.getUniqueId().toString());
        } catch (Exception ignored) {}
    }

    private void unregisterGlow(Display display) {
        try {
            for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
                if (team.getName().startsWith(HologramUtil.GLOW_TEAM_PREFIX))
                    team.removeEntry(display.getUniqueId().toString());
            }
        } catch (Exception ignored) {}
    }
}