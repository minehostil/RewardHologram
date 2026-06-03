package dev.rewardhologram.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad para convertir códigos de color a componentes Adventure.
 *
 * Soporta:
 *  - Códigos clásicos: &a, &6, &l, &r, etc.
 *  - Colores hex:      &#RRGGBB  (ej: &#FF5733Texto rojo)
 */
public class ColorUtil {

    // Patrón para detectar colores hex tipo &#RRGGBB
    private static final Pattern HEX_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()           // Habilita soporte hex en LegacySerializer
                    .useUnusualXRepeatedCharacterHexFormat() // Formato &x&R&R&G&G&B&B interno
                    .build();

    /**
     * Convierte texto con &códigos y &#RRGGBB a Component Adventure.
     * Usar para líneas del holograma, títulos y mensajes.
     */
    public static Component color(String text) {
        return LEGACY.deserialize(convertHex(text));
    }

    /** Alias semántico para mensajes de chat. */
    public static Component colorMessage(String text) {
        return color(text);
    }

    /**
     * Convierte a String con § — solo para usos legacy si fuera necesario.
     */
    public static String colorString(String text) {
        return LEGACY.serialize(color(text))
                .replace('&', '\u00A7');
    }

    /**
     * Convierte &#RRGGBB al formato interno que LegacyComponentSerializer entiende:
     * &x&R&R&G&G&B&B
     *
     * Ejemplo: &#FF5733 → &x&F&F&5&7&3&3
     */
    private static String convertHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}