package dev.rewardhologram.util;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de líneas de holograma con placeholders internos.
 *
 * Placeholder soportado:
 *   {line_scale:X} — multiplicador de escala de la línea (solo DISPLAY renderer).
 *
 * El placeholder puede aparecer en cualquier parte de la línea; se elimina del
 * texto final. Si se repite, se usa la primera aparición y se eliminan todas.
 *
 * Ejemplos:
 *   "{line_scale:1.5}&#FF00FF&l★ TÍTULO ★"   → scale 1.5
 *   "&eLínea normal"                          → scale 1.0
 *
 * IMPORTANTE: en el config, la línea debe ir ENTRE COMILLAS cuando usa el
 * placeholder — YAML interpretaría "{" como inicio de mapping y rompería.
 */
public final class LineParser {

    private static final Pattern LINE_SCALE_PATTERN =
            Pattern.compile("\\{line_scale:([^}]*)}", Pattern.CASE_INSENSITIVE);

    private LineParser() {}

    /** Línea parseada: texto limpio (sin placeholders) y escala aplicable. */
    public record ParsedLine(String text, double scale) {}

    /**
     * Separa el placeholder {line_scale:X} del texto.
     * Tolerante a fallos: valor inválido o <= 0 → warn + escala 1.0.
     * El placeholder SIEMPRE se remueve del texto (con o sin valor válido),
     * para que nunca aparezca literal en el holograma.
     */
    public static ParsedLine parse(String raw, Logger logger, String context) {
        if (raw == null || raw.isEmpty()) return new ParsedLine("", 1.0);

        Matcher matcher = LINE_SCALE_PATTERN.matcher(raw);
        if (!matcher.find()) return new ParsedLine(raw, 1.0);

        String value = matcher.group(1).trim();
        // Eliminar TODAS las ocurrencias del placeholder del texto final
        String text = matcher.replaceAll("").stripLeading();

        double scale;
        try {
            scale = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            if (logger != null)
                logger.warning("[" + context + "] Invalid {line_scale:" + value
                        + "} — must be a number (e.g. {line_scale:1.5}). Using 1.0.");
            return new ParsedLine(text, 1.0);
        }

        if (scale <= 0) {
            if (logger != null)
                logger.warning("[" + context + "] {line_scale:" + value
                        + "} must be greater than 0. Using 1.0.");
            scale = 1.0;
        }

        return new ParsedLine(text, scale);
    }
}