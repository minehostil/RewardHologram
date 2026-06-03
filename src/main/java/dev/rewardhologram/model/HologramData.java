package dev.rewardhologram.model;

import java.util.List;

public class HologramData {

    /** Tipo de clic para reclamar el holograma. */
    public enum ClickType { RIGHT, LEFT, BOTH }

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
    private final List<RewardEntry> rewards;
    private final int rewardPick; // Cuántas recompensas elegir de la lista (-1 = todas)

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
    private final double lineSpacing;

    private final boolean claimSoundEnabled;
    private final String claimSoundName;
    private final float claimSoundVolume;
    private final float claimSoundPitch;

    private final ClickType clickType;

    public HologramData(String id, double chance, int interval, int despawnAfter,
                        double offsetX, double offsetY, double offsetZ,
                        boolean bobbingEnabled, double bobbingAmplitude, double bobbingSpeed,
                        List<String> lines, List<RewardEntry> rewards, int rewardPick,
                        String appearMessage, String claimMessage,
                        boolean titleEnabled, String titleText, String titleSubtitle,
                        int titleFadeIn, int titleStay, int titleFadeOut,
                        boolean soundEnabled, String soundName, float soundVolume, float soundPitch,
                        boolean skullEnabled, String skullTexture, float skullHeadYaw,
                        double skullHeightOffset,
                        boolean skullRotationEnabled, float skullRotationSpeed,
                        double lineSpacing,
                        boolean claimSoundEnabled, String claimSoundName,
                        float claimSoundVolume, float claimSoundPitch,
                        ClickType clickType) {
        this.id = id;
        this.chance = chance;
        this.interval = interval;
        this.despawnAfter = despawnAfter;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.bobbingEnabled = bobbingEnabled;
        this.bobbingAmplitude = bobbingAmplitude;
        this.bobbingSpeed = bobbingSpeed;
        this.lines = lines;
        this.rewards = rewards;
        this.rewardPick = rewardPick;
        this.appearMessage = appearMessage;
        this.claimMessage = claimMessage;
        this.titleEnabled = titleEnabled;
        this.titleText = titleText;
        this.titleSubtitle = titleSubtitle;
        this.titleFadeIn = titleFadeIn;
        this.titleStay = titleStay;
        this.titleFadeOut = titleFadeOut;
        this.soundEnabled = soundEnabled;
        this.soundName = soundName;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
        this.skullEnabled = skullEnabled;
        this.skullTexture = skullTexture;
        this.skullHeadYaw = skullHeadYaw;
        this.skullHeightOffset = skullHeightOffset;
        this.skullRotationEnabled = skullRotationEnabled;
        this.skullRotationSpeed = skullRotationSpeed;
        this.lineSpacing = lineSpacing;
        this.claimSoundEnabled = claimSoundEnabled;
        this.claimSoundName = claimSoundName;
        this.claimSoundVolume = claimSoundVolume;
        this.claimSoundPitch = claimSoundPitch;
        this.clickType = clickType;
    }

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
    public double getLineSpacing() { return lineSpacing; }
    public boolean isClaimSoundEnabled() { return claimSoundEnabled; }
    public String getClaimSoundName() { return claimSoundName; }
    public float getClaimSoundVolume() { return claimSoundVolume; }
    public float getClaimSoundPitch() { return claimSoundPitch; }
    public ClickType getClickType() { return clickType; }
}