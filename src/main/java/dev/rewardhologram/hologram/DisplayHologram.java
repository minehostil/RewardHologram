package dev.rewardhologram.hologram;

import dev.rewardhologram.RewardHologramPlugin;
import dev.rewardhologram.model.ActiveHologram;
import dev.rewardhologram.model.HologramData;
import dev.rewardhologram.util.ColorUtil;
import dev.rewardhologram.util.HologramUtil;
import dev.rewardhologram.util.SkullUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
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
 * Holograma basado en Display Entities (1.19.4+) con un proxy de interacción.
 *
 * Visual: TextDisplay/ItemDisplay — billboard, scale, glow, animaciones avanzadas.
 * Interacción: un ArmorStand invisible (pickable por el cliente) centrado en el
 * holograma. El cliente Java NUNCA envía paquetes de interacción hacia displays
 * (no son "pickable") y con la mano vacía apuntando al aire no envía NINGÚN
 * paquete — el armor stand proxy es la única vía fiable para capturar el clic
 * derecho sin item en la mano.
 */
public class DisplayHologram implements Hologram {

    private static final Vector3f ZERO_TRANSLATION = new Vector3f(0, 0, 0);
    private static final Quaternionf IDENTITY_ROTATION = new Quaternionf();
    private static final Vector3f NEAR_ZERO_SCALE = new Vector3f(0.0001f, 0.0001f, 0.0001f);

    /**
     * Mitad de la altura de la hitbox de un armor stand normal (~1.975).
     * El proxy se posiciona restando esto a la Y del centro deseado,
     * así su hitbox queda centrada en el holograma.
     */
    private static final double PROXY_HALF_HEIGHT = 0.9875;

    /**
     * Ticks de gracia antes de animar el pop-in. La interpolación de displays
     * recién spawneadas no arranca de forma fiable si el transform cambia
     * 1 tick después — 2 ticks asegura que el cliente ya procesó la escala 0.
     */
    private static final long POP_IN_DELAY_TICKS = 2L;

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

        // Proxy de interacción — armor stand invisible con hitbox real.
        // Centrado en el holograma (incluida la skull si existe) para que su
        // hitbox cubra el máximo de área clickeable.
        double centerY = computeInteractionCenterY(totalLines, spacing);
        ArmorStand proxy = spawnInteractionProxy(
                base.clone().add(0, centerY - PROXY_HALF_HEIGHT, 0));
        entities.add(proxy);

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
     * Y del centro de interacción: punto medio entre la parte baja (primera
     * línea) y la parte alta (skull si existe, última línea si no).
     */
    private double computeInteractionCenterY(int totalLines, double spacing) {
        double top = data.isSkullEnabled()
                ? totalLines * spacing + data.getSkullHeightOffset()
                : (totalLines - 1) * spacing;
        return top / 2.0;
    }

    /**
     * Pop-in: las entidades spawnean con escala ~0; tras 2 ticks de gracia se
     * animan hacia su escala final con interpolación nativa.
     *
     * setInterpolationDelay(0) es OBLIGATORIO en ambos pasos: sin inicializarlo,
     * la interpolación de displays recién spawneadas no arranca y el cambio de
     * escala salta directo (el bug reportado como "el pop no funciona").
     */
    private void schedulePopIn(ActiveHologram active, Map<Entity, Transformation> finalTransforms) {
        int duration = Math.max(1, data.getAppearDurationTicks());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finalTransforms.forEach((entity, transform) -> {
                if (entity instanceof Display d && !d.isDead()) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(duration);
                    d.setTransformation(transform);
                }
            });
            // Tras terminar el pop-in, habilitar el loop de animaciones por tick
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> active.setAppearanceDone(true), duration);
        }, POP_IN_DELAY_TICKS);
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

        // El proxy de interacción sigue al holograma (mismo bobbing) para que
        // su hitbox clickeable acompañe al visual en todo momento
        for (Entity e : active.getEntities()) {
            if (e instanceof ArmorStand proxy && !proxy.isDead()) {
                Location loc = proxy.getLocation();
                double centerY = computeInteractionCenterY(totalLines, spacing);
                proxy.teleport(new Location(loc.getWorld(), loc.getX(),
                        newY + centerY - PROXY_HALF_HEIGHT, loc.getZ()));
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
     *
     * Las PDC tags se quitan de TODAS las entidades (incluido el proxy) de
     * inmediato — un holograma "muriendo" no puede ser reclamado. El proxy
     * además se elimina al instante: no participa en la animación y dejar su
     * hitbox activa solo estorbaría los clics durante el pop-out.
     */
    @Override
    public void destroySmooth(ActiveHologram active) {
        int duration = Math.max(1, data.getDespawnDurationTicks());
        List<Entity> entities = new ArrayList<>(active.getEntities());

        for (Entity e : entities) {
            // Quitar tags de TODAS las entidades — sin esto el proxy seguiría
            // reclamable durante la animación de salida (doble recompensa)
            e.getPersistentDataContainer().remove(plugin.getOwnerKey());
            e.getPersistentDataContainer().remove(plugin.getHologramIdKey());

            // El proxy no anima — fuera de inmediato
            if (e instanceof ArmorStand) {
                HologramUtil.removeEntitySafely(e);
                continue;
            }

            if (!(e instanceof Display d) || d.isDead()) continue;

            unregisterGlow(d);

            d.setInterpolationDelay(0);
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
            // colorString() serializa el Component de Adventure a ese formato.
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
            td.setInterpolationDelay(0);
            td.setTransformation(staticLineTransform(index, initialScale));
        });
    }

    private ItemDisplay spawnSkull(Location loc, float initialScale) {
        return loc.getWorld().spawn(loc, ItemDisplay.class, skull -> {
            skull.setItemStack(SkullUtil.createSkull(data.getSkullTexture()));
            skull.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            skull.setBillboard(data.getBillboard());
            skull.setTeleportDuration(1);
            skull.setInvulnerable(true);
            skull.setPersistent(false);
            applyGlow(skull);
            skull.setInterpolationDelay(0);
            skull.setTransformation(staticSkullTransform(initialScale));
        });
    }

    /**
     * Armor stand invisible que actúa como proxy de interacción del holograma.
     *
     * CRÍTICO: setSmall(false) y setMarker(false). Con marker=true la hitbox es
     * de 0 bloques y el cliente no puede apuntarle; con small=true la hitbox se
     * reduce a menos de la mitad. La hitbox completa (~0.5 × 1.975) es la zona
     * de clic derecho SIN item en la mano — la única que existe para displays.
     *
     * Sin glow a propósito: el outline del glow en un armor stand invisible
     * dibuja el contorno de su hitbox (un paralelepípedo), arruinando el glow
     * del texto.
     */
    private ArmorStand spawnInteractionProxy(Location loc) {
        return loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setGravity(false);
            as.setSmall(false);
            as.setMarker(false);
            as.setCollidable(false);
            as.setInvulnerable(true);
            as.setCanPickupItems(false);
            as.setArms(false);
            as.setBasePlate(false);
            as.setPersistent(false);
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