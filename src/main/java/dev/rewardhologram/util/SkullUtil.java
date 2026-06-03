package dev.rewardhologram.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Utilidad para crear cabezas con texturas personalizadas (Base64).
 */
public class SkullUtil {

    /**
     * Crea un ItemStack de cráneo con textura Base64 personalizada.
     *
     * @param base64Texture Textura en formato Base64 (obtenida de minecraft-heads.com)
     * @return ItemStack con la textura aplicada
     */
    public static ItemStack createSkull(String base64Texture) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta == null) return skull;

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "RewardHead");
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);

        skull.setItemMeta(meta);
        return skull;
    }
}