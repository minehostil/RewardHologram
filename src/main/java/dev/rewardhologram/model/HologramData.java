package dev.rewardhologram.model;

import org.bukkit.ChatColor;
import org.bukkit.entity.Display;

import java.util.ArrayList;
import java.util.List;

public class HologramData {

    /** Tipo de clic para reclamar el holograma. */
    public enum ClickType { RIGHT, LEFT, BOTH }

    /**
     * Motor de renderizado del holograma.
     * DEFAULT: hereda settings.renderer del config global.
     * ARMOR_STAND: máximo soporte (Java + Bedrock/Geyser).
     * DISPLAY: TextDisplay/ItemDisplay — scale, glow, billboard y animaciones avanzadas.
     */
    public enum Renderer { DEFAULT, ARMOR_STAND, DISPLAY }

    /** Animación de aparición/desaparición (solo DISPLAY; NONE en ARMOR_STAND). */
    public enum SpawnAnimation { NONE, POP }

    private final String id;
    private final double chance;
    private final int interval;
    private final int despawnAfter;

    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;

    private final boolean bobbingEnabled;
    private final double bobbingAmplitude;
    private final double bobbingSpeed;

    private final List<String> lines;
    private final List<Double> lineScales; // Escala por línea (DISPLAY)
    private final List<RewardEntry> rewards;
    private final int rewardPick;

    private final String appearMessage;
    private final String claimMessage;

    private final boolean titleEnabled;
    private final String titleText;
    private final String titleSubtitle;
    private final int titleFadeIn;
    private final int titleStay;
    private final int titleFadeOut;

    private final boolean soundEnabled;
    private final String soundName;
    private final float soundVolume;
    private final float soundPitch;

    private final boolean skullEnabled;
    private final String skullTexture;
    private final float skullHeadYaw;
    private final double skullHeightOffset;
    private final boolean skullRotationEnabled;
    private final float skullRotationSpeed;
    private final double skullScale;

    private final double lineSpacing;

    private final boolean claimSoundEnabled;
    private final String claimSoundName;
    private final float claimSoundVolume;
    private final float claimSoundPitch;

    private final ClickType clickType;

    // ─── DISPLAY renderer ──────────────────────────────────────────────────────
    private final Renderer renderer;
    private final Display.Billboard billboard;
    private final double displayScale;
    private final boolean glowEnabled;
    private final ChatColor glowColor;
    private final boolean textShadowed;
    private final boolean textSeeThrough;
    private final boolean textBackgroundTransparent;

    // Animaciones de la skull (solo DISPLAY)
    private final boolean squashEnabled;
    private final double squashAmplitude;
    private final double squashSpeed;
    private final boolean tiltEnabled;
    private final double tiltAmplitude;
    private final double tiltSpeed;

    // Aparición / desaparición
    private final SpawnAnimation appearAnimation;
    private final int appearDurationTicks;
    private final boolean appearParticlesEnabled;
    private final String appearParticleType;
    private final int appearParticleCount;
    private final double appearParticleSpread;
    private final double appearParticleSpeed;

    private final SpawnAnimation despawnAnimation;
    private final int despawnDurationTicks;
    private final boolean despawnParticlesEnabled;
    private final String despawnParticleType;
    private final int despawnParticleCount;
    private final double despawnParticleSpread;
    private final double despawnParticleSpeed;

    private HologramData(Builder b) {
        this.id = b.id;
        this.chance = b.chance;
        this.interval = b.interval;
        this.despawnAfter = b.despawnAfter;
        this.offsetX = b.offsetX;
        this.offsetY = b.offsetY;
        this.offsetZ = b.offsetZ;
        this.bobbingEnabled = b.bobbingEnabled;
        this.bobbingAmplitude = b.bobbingAmplitude;
        this.bobbingSpeed = b.bobbingSpeed;
        this.lines = b.lines;
        this.lineScales = b.lineScales;
        this.rewards = b.rewards;
        this.rewardPick = b.rewardPick;
        this.appearMessage = b.appearMessage;
        this.claimMessage = b.claimMessage;
        this.titleEnabled = b.titleEnabled;
        this.titleText = b.titleText;
        this.titleSubtitle = b.titleSubtitle;
        this.titleFadeIn = b.titleFadeIn;
        this.titleStay = b.titleStay;
        this.titleFadeOut = b.titleFadeOut;
        this.soundEnabled = b.soundEnabled;
        this.soundName = b.soundName;
        this.soundVolume = b.soundVolume;
        this.soundPitch = b.soundPitch;
        this.skullEnabled = b.skullEnabled;
        this.skullTexture = b.skullTexture;
        this.skullHeadYaw = b.skullHeadYaw;
        this.skullHeightOffset = b.skullHeightOffset;
        this.skullRotationEnabled = b.skullRotationEnabled;
        this.skullRotationSpeed = b.skullRotationSpeed;
        this.skullScale = b.skullScale;
        this.lineSpacing = b.lineSpacing;
        this.claimSoundEnabled = b.claimSoundEnabled;
        this.claimSoundName = b.claimSoundName;
        this.claimSoundVolume = b.claimSoundVolume;
        this.claimSoundPitch = b.claimSoundPitch;
        this.clickType = b.clickType;
        this.renderer = b.renderer;
        this.billboard = b.billboard;
        this.displayScale = b.displayScale;
        this.glowEnabled = b.glowEnabled;
        this.glowColor = b.glowColor;
        this.textShadowed = b.textShadowed;
        this.textSeeThrough = b.textSeeThrough;
        this.textBackgroundTransparent = b.textBackgroundTransparent;
        this.squashEnabled = b.squashEnabled;
        this.squashAmplitude = b.squashAmplitude;
        this.squashSpeed = b.squashSpeed;
        this.tiltEnabled = b.tiltEnabled;
        this.tiltAmplitude = b.tiltAmplitude;
        this.tiltSpeed = b.tiltSpeed;
        this.appearAnimation = b.appearAnimation;
        this.appearDurationTicks = b.appearDurationTicks;
        this.appearParticlesEnabled = b.appearParticlesEnabled;
        this.appearParticleType = b.appearParticleType;
        this.appearParticleCount = b.appearParticleCount;
        this.appearParticleSpread = b.appearParticleSpread;
        this.appearParticleSpeed = b.appearParticleSpeed;
        this.despawnAnimation = b.despawnAnimation;
        this.despawnDurationTicks = b.despawnDurationTicks;
        this.despawnParticlesEnabled = b.despawnParticlesEnabled;
        this.despawnParticleType = b.despawnParticleType;
        this.despawnParticleCount = b.despawnParticleCount;
        this.despawnParticleSpread = b.despawnParticleSpread;
        this.despawnParticleSpeed = b.despawnParticleSpeed;
    }

    public static Builder builder(String id) { return new Builder(id); }

    public static class Builder {
        private final String id;
        private double chance = 25.0;
        private int interval = 300;
        private int despawnAfter = 30;
        private double offsetX = 0, offsetY = 1.5, offsetZ = 2.5;
        private boolean bobbingEnabled = true;
        private double bobbingAmplitude = 0.15, bobbingSpeed = 0.05;
        private List<String> lines = new ArrayList<>();
        private List<Double> lineScales = new ArrayList<>();
        private List<RewardEntry> rewards = new ArrayList<>();
        private int rewardPick = -1;
        private String appearMessage = "", claimMessage = "";
        private boolean titleEnabled = false;
        private String titleText = "", titleSubtitle = "";
        private int titleFadeIn = 10, titleStay = 60, titleFadeOut = 20;
        private boolean soundEnabled = false;
        private String soundName = "ENTITY_EXPERIENCE_ORB_PICKUP";
        private float soundVolume = 1.0f, soundPitch = 1.0f;
        private boolean skullEnabled = false;
        private String skullTexture = "";
        private float skullHeadYaw = 0.0f;
        private double skullHeightOffset = 0.5;
        private boolean skullRotationEnabled = false;
        private float skullRotationSpeed = 2.0f;
        private double skullScale = 1.0;
        private double lineSpacing = 0.28;
        private boolean claimSoundEnabled = false;
        private String claimSoundName = "ENTITY_PLAYER_LEVELUP";
        private float claimSoundVolume = 1.0f, claimSoundPitch = 1.0f;
        private ClickType clickType = ClickType.RIGHT;
        private Renderer renderer = Renderer.DEFAULT;
        private Display.Billboard billboard = Display.Billboard.CENTER;
        private double displayScale = 1.0;
        private boolean glowEnabled = false;
        private ChatColor glowColor = ChatColor.WHITE;
        private boolean textShadowed = false;
        private boolean textSeeThrough = false;
        private org.bukkit.Color textBackgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0);
        private boolean squashEnabled = false;
        private double squashAmplitude = 0.12, squashSpeed = 0.15;
        private boolean tiltEnabled = false;
        private double tiltAmplitude = 8.0, tiltSpeed = 0.06;
        private SpawnAnimation appearAnimation = SpawnAnimation.NONE;
        private int appearDurationTicks = 6;
        private boolean appearParticlesEnabled = false;
        private String appearParticleType = "TOTEM_OF_UNDYING";
        private int appearParticleCount = 25;
        private double appearParticleSpread = 0.5, appearParticleSpeed = 0.1;
        private SpawnAnimation despawnAnimation = SpawnAnimation.NONE;
        private int despawnDurationTicks = 6;
        private boolean despawnParticlesEnabled = false;
        private String despawnParticleType = "CLOUD";
        private int despawnParticleCount = 15;
        private double despawnParticleSpread = 0.4, despawnParticleSpeed = 0.05;

        private Builder(String id) { this.id = id; }

        public Builder chance(double v) { this.chance = v; return this; }
        public Builder interval(int v) { this.interval = v; return this; }
        public Builder despawnAfter(int v) { this.despawnAfter = v; return this; }
        public Builder offset(double x, double y, double z) { this.offsetX = x; this.offsetY = y; this.offsetZ = z; return this; }
        public Builder bobbing(boolean enabled, double amplitude, double speed) {
            this.bobbingEnabled = enabled; this.bobbingAmplitude = amplitude; this.bobbingSpeed = speed; return this;
        }
        public Builder lines(List<String> lines, List<Double> scales) {
            this.lines = lines;
            this.lineScales = scales;
            // Rellenar escalas faltantes con 1.0 para que ambas listas queden alineadas
            while (this.lineScales.size() < this.lines.size()) this.lineScales.add(1.0);
            return this;
        }
        public Builder rewards(List<RewardEntry> rewards, int pick) { this.rewards = rewards; this.rewardPick = pick; return this; }
        public Builder appearMessage(String v) { this.appearMessage = v; return this; }
        public Builder claimMessage(String v) { this.claimMessage = v; return this; }
        public Builder title(boolean enabled, String t, String st, int in, int stay, int out) {
            this.titleEnabled = enabled; this.titleText = t; this.titleSubtitle = st;
            this.titleFadeIn = in; this.titleStay = stay; this.titleFadeOut = out; return this;
        }
        public Builder appearSound(boolean enabled, String name, float volume, float pitch) {
            this.soundEnabled = enabled; this.soundName = name; this.soundVolume = volume; this.soundPitch = pitch; return this;
        }
        public Builder skull(boolean enabled, String texture, float headYaw, double heightOffset,
                             boolean rotationEnabled, float rotationSpeed) {
            this.skullEnabled = enabled; this.skullTexture = texture; this.skullHeadYaw = headYaw;
            this.skullHeightOffset = heightOffset; this.skullRotationEnabled = rotationEnabled;
            this.skullRotationSpeed = rotationSpeed; return this;
        }
        public Builder skullDisplay(double scale) { this.skullScale = scale; return this; }
        public Builder skullEffects(boolean squashEnabled, double squashAmp, double squashSpeed,
                                    boolean tiltEnabled, double tiltAmp, double tiltSpeed) {
            this.squashEnabled = squashEnabled; this.squashAmplitude = squashAmp; this.squashSpeed = squashSpeed;
            this.tiltEnabled = tiltEnabled; this.tiltAmplitude = tiltAmp; this.tiltSpeed = tiltSpeed; return this;
        }
        public Builder lineSpacing(double v) { this.lineSpacing = v; return this; }
        public Builder claimSound(boolean enabled, String name, float volume, float pitch) {
            this.claimSoundEnabled = enabled; this.claimSoundName = name;
            this.claimSoundVolume = volume; this.claimSoundPitch = pitch; return this;
        }
        public Builder clickType(ClickType v) { this.clickType = v; return this; }
        public Builder renderer(Renderer v) { this.renderer = v; return this; }
        public Builder display(Display.Billboard billboard, double scale,
                       boolean glowEnabled, ChatColor glowColor,
                       boolean shadowed, boolean seeThrough, org.bukkit.Color backgroundColor) {
    this.billboard = billboard; this.displayScale = scale;
    this.glowEnabled = glowEnabled; this.glowColor = glowColor;
    this.textShadowed = shadowed; this.textSeeThrough = seeThrough;
    this.textBackgroundColor = backgroundColor; return this;
}
        public Builder appear(SpawnAnimation anim, int durationTicks) {
            this.appearAnimation = anim; this.appearDurationTicks = durationTicks; return this;
        }
        public Builder appearParticles(boolean enabled, String type, int count, double spread, double speed) {
            this.appearParticlesEnabled = enabled; this.appearParticleType = type;
            this.appearParticleCount = count; this.appearParticleSpread = spread; this.appearParticleSpeed = speed;
            return this;
        }
        public Builder despawn(SpawnAnimation anim, int durationTicks) {
            this.despawnAnimation = anim; this.despawnDurationTicks = durationTicks; return this;
        }
        public Builder despawnParticles(boolean enabled, String type, int count, double spread, double speed) {
            this.despawnParticlesEnabled = enabled; this.despawnParticleType = type;
            this.despawnParticleCount = count; this.despawnParticleSpread = spread; this.despawnParticleSpeed = speed;
            return this;
        }

        public HologramData build() { return new HologramData(this); }
    }

    // ─── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public double getChance() { return chance; }
    public int getInterval() { return interval; }
    public int getDespawnAfter() { return despawnAfter; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public boolean isBobbingEnabled() { return bobbingEnabled; }
    public double getBobbingAmplitude() { return bobbingAmplitude; }
    public double getBobbingSpeed() { return bobbingSpeed; }
    public List<String> getLines() { return lines; }
    public List<Double> getLineScales() { return lineScales; }
    public double getLineScale(int index) {
        if (index < 0 || index >= lineScales.size()) return 1.0;
        return lineScales.get(index);
    }
    public List<RewardEntry> getRewards() { return rewards; }
    public int getRewardPick() { return rewardPick; }
    public String getAppearMessage() { return appearMessage; }
    public String getClaimMessage() { return claimMessage; }
    public boolean isTitleEnabled() { return titleEnabled; }
    public String getTitleText() { return titleText; }
    public String getTitleSubtitle() { return titleSubtitle; }
    public int getTitleFadeIn() { return titleFadeIn; }
    public int getTitleStay() { return titleStay; }
    public int getTitleFadeOut() { return titleFadeOut; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public String getSoundName() { return soundName; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }
    public boolean isSkullEnabled() { return skullEnabled; }
    public String getSkullTexture() { return skullTexture; }
    public float getSkullHeadYaw() { return skullHeadYaw; }
    public double getSkullHeightOffset() { return skullHeightOffset; }
    public boolean isSkullRotationEnabled() { return skullRotationEnabled; }
    public float getSkullRotationSpeed() { return skullRotationSpeed; }
    public double getSkullScale() { return skullScale; }
    public double getLineSpacing() { return lineSpacing; }
    public boolean isClaimSoundEnabled() { return claimSoundEnabled; }
    public String getClaimSoundName() { return claimSoundName; }
    public float getClaimSoundVolume() { return claimSoundVolume; }
    public float getClaimSoundPitch() { return claimSoundPitch; }
    public ClickType getClickType() { return clickType; }

    // DISPLAY renderer
    public Renderer getRenderer() { return renderer; }
    public Display.Billboard getBillboard() { return billboard; }
    public double getDisplayScale() { return displayScale; }
    public boolean isGlowEnabled() { return glowEnabled; }
    public ChatColor getGlowColor() { return glowColor; }
    public boolean isTextShadowed() { return textShadowed; }
    public boolean isTextSeeThrough() { return textSeeThrough; }
    public org.bukkit.Color getTextBackgroundColor() { return textBackgroundColor; }
    public boolean isSquashEnabled() { return squashEnabled; }
    public double getSquashAmplitude() { return squashAmplitude; }
    public double getSquashSpeed() { return squashSpeed; }
    public boolean isTiltEnabled() { return tiltEnabled; }
    public double getTiltAmplitude() { return tiltAmplitude; }
    public double getTiltSpeed() { return tiltSpeed; }
    public SpawnAnimation getAppearAnimation() { return appearAnimation; }
    public int getAppearDurationTicks() { return appearDurationTicks; }
    public boolean isAppearParticlesEnabled() { return appearParticlesEnabled; }
    public String getAppearParticleType() { return appearParticleType; }
    public int getAppearParticleCount() { return appearParticleCount; }
    public double getAppearParticleSpread() { return appearParticleSpread; }
    public double getAppearParticleSpeed() { return appearParticleSpeed; }
    public SpawnAnimation getDespawnAnimation() { return despawnAnimation; }
    public int getDespawnDurationTicks() { return despawnDurationTicks; }
    public boolean isDespawnParticlesEnabled() { return despawnParticlesEnabled; }
    public String getDespawnParticleType() { return despawnParticleType; }
    public int getDespawnParticleCount() { return despawnParticleCount; }
    public double getDespawnParticleSpread() { return despawnParticleSpread; }
    public double getDespawnParticleSpeed() { return despawnParticleSpeed; }
}